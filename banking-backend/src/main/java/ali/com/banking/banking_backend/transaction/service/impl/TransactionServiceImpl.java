package ali.com.banking.banking_backend.transaction.service.impl;

import ali.com.banking.banking_backend.account.entity.Account;
import ali.com.banking.banking_backend.account.entity.AccountStatus;
import ali.com.banking.banking_backend.account.repository.AccountRepository;
import ali.com.banking.banking_backend.customer.entity.Customer;
import ali.com.banking.banking_backend.exception.AccountNotFoundException;
import ali.com.banking.banking_backend.exception.CustomerAccessDeniedException;
import ali.com.banking.banking_backend.transaction.dto.DepositRequest;
import ali.com.banking.banking_backend.transaction.dto.TransactionResponse;
import ali.com.banking.banking_backend.transaction.dto.TransferRequest;
import ali.com.banking.banking_backend.transaction.dto.TransferResponse;
import ali.com.banking.banking_backend.transaction.dto.WithdrawalRequest;
import ali.com.banking.banking_backend.transaction.entity.Transaction;
import ali.com.banking.banking_backend.transaction.entity.TransactionType;
import ali.com.banking.banking_backend.transaction.mapper.TransactionMapper;
import ali.com.banking.banking_backend.transaction.repository.TransactionRepository;
import ali.com.banking.banking_backend.transaction.service.TransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionServiceImpl implements TransactionService {

    private static final BigDecimal MAX_BALANCE = new BigDecimal("99999999999999999.99");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransactionServiceImpl(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional
    public TransactionResponse deposit(Long accountId, DepositRequest request, String authenticatedEmail) {
        validateAccountId(accountId);
        validateRequest(request);

        BigDecimal amount = request.getAmount();
        validateAmount(amount);

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        ensureOwner(account, authenticatedEmail);
        ensureAccountIsActive(account);

        BigDecimal currentBalance = requireAccountBalance(account);

        BigDecimal newBalance = currentBalance.add(amount);
        if (newBalance.compareTo(MAX_BALANCE) > 0) {
            throw new IllegalArgumentException("Deposit would exceed the maximum allowed account balance");
        }

        account.setBalance(newBalance);

        Transaction transaction = Transaction.builder()
                .account(account)
                .type(TransactionType.DEPOSIT)
                .amount(amount)
                .balanceAfter(newBalance)
                .description("Deposit to account")
                .reference(UUID.randomUUID().toString())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);
        return TransactionMapper.toResponse(savedTransaction);
    }

    @Override
    @Transactional
    public TransactionResponse withdraw(Long accountId, WithdrawalRequest request, String authenticatedEmail) {
        validateAccountId(accountId);
        validateRequest(request);

        BigDecimal amount = request.getAmount();
        validateAmount(amount);

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        ensureOwner(account, authenticatedEmail);
        ensureAccountIsActive(account);

        BigDecimal currentBalance = requireAccountBalance(account);

        if (currentBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }

        BigDecimal newBalance = currentBalance.subtract(amount);

        account.setBalance(newBalance);

        Transaction transaction = Transaction.builder()
                .account(account)
                .type(TransactionType.WITHDRAWAL)
                .amount(amount)
                .balanceAfter(newBalance)
                .description("Withdrawal from account")
                .reference(UUID.randomUUID().toString())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);
        return TransactionMapper.toResponse(savedTransaction);
    }

        @Override
        @Transactional
        public TransferResponse transfer(Long sourceAccountId, TransferRequest request, String authenticatedEmail) {
        validateAccountId(sourceAccountId);
        validateRequest(request);

        Long destinationAccountId = request.getDestinationAccountId();
        validateAccountId(destinationAccountId);
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }

        BigDecimal amount = request.getAmount();
        validateAmount(amount);

        Long firstAccountId = sourceAccountId < destinationAccountId
            ? sourceAccountId
            : destinationAccountId;
        Long secondAccountId = sourceAccountId < destinationAccountId
            ? destinationAccountId
            : sourceAccountId;

        Account firstLockedAccount = accountRepository.findByIdForUpdate(firstAccountId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        Account secondLockedAccount = accountRepository.findByIdForUpdate(secondAccountId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));

        Account sourceAccount = sourceAccountId.equals(firstAccountId)
            ? firstLockedAccount
            : secondLockedAccount;
        Account destinationAccount = destinationAccountId.equals(firstAccountId)
            ? firstLockedAccount
            : secondLockedAccount;

        ensureOwner(sourceAccount, authenticatedEmail);
        ensureAccountIsActive(sourceAccount);
        ensureAccountIsActive(destinationAccount);

        BigDecimal sourceBalance = requireAccountBalance(sourceAccount);
        BigDecimal destinationBalance = requireAccountBalance(destinationAccount);
        if (sourceBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }

        BigDecimal newSourceBalance = sourceBalance.subtract(amount);
        BigDecimal newDestinationBalance = destinationBalance.add(amount);
        if (newDestinationBalance.compareTo(MAX_BALANCE) > 0) {
            throw new IllegalArgumentException("Transfer would exceed the maximum allowed destination account balance");
        }

        sourceAccount.setBalance(newSourceBalance);
        destinationAccount.setBalance(newDestinationBalance);

        String transferReference = UUID.randomUUID().toString();
        Transaction sourceTransaction = Transaction.builder()
            .account(sourceAccount)
            .type(TransactionType.WITHDRAWAL)
            .amount(amount)
            .balanceAfter(newSourceBalance)
            .description("Transfer to account")
            .reference(UUID.randomUUID().toString())
            .transferReference(transferReference)
            .build();
        Transaction destinationTransaction = Transaction.builder()
            .account(destinationAccount)
            .type(TransactionType.DEPOSIT)
            .amount(amount)
            .balanceAfter(newDestinationBalance)
            .description("Transfer from account")
            .reference(UUID.randomUUID().toString())
            .transferReference(transferReference)
            .build();

        Transaction savedSourceTransaction = transactionRepository.save(sourceTransaction);
        Transaction savedDestinationTransaction = transactionRepository.save(destinationTransaction);

        return TransferResponse.builder()
            .transferReference(transferReference)
            .sourceTransaction(TransactionMapper.toResponse(savedSourceTransaction))
            .destinationTransaction(TransactionMapper.toResponse(savedDestinationTransaction))
            .build();
        }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getAccountTransactions(Long accountId, String authenticatedEmail) {
        validateAccountId(accountId);

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        ensureOwner(account, authenticatedEmail);

        return transactionRepository.findByAccount_AccountIdOrderByCreatedAtDesc(accountId)
                .stream()
                .map(TransactionMapper::toResponse)
                .toList();
    }

    private void validateAccountId(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException("Account ID must not be null or non-positive");
        }
    }

    private void validateRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("Transaction request must not be null");
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (amount.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Amount must have at most 17 integer digits and 2 decimal places");
        }

        if (amount.compareTo(MAX_BALANCE) > 0) {
            throw new IllegalArgumentException("Amount must have at most 17 integer digits and 2 decimal places");
        }
    }

    private BigDecimal requireAccountBalance(Account account) {
        if (account.getBalance() == null) {
            throw new IllegalStateException("Account balance is invalid");
        }
        return account.getBalance();
    }

    private void ensureOwner(Account account, String authenticatedEmail) {
        Customer customer = account.getCustomer();
        if (authenticatedEmail == null || customer == null ||
                !authenticatedEmail.equalsIgnoreCase(customer.getEmail())) {
            throw new CustomerAccessDeniedException("Customer access denied");
        }
    }

    private void ensureAccountIsActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account must be active for this transaction");
        }
    }
}

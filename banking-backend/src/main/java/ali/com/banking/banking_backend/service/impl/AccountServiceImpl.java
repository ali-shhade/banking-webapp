package ali.com.banking.banking_backend.service.impl;

import ali.com.banking.banking_backend.dto.AccountCreateRequest;
import ali.com.banking.banking_backend.dto.AccountResponse;
import ali.com.banking.banking_backend.entity.Account;
import ali.com.banking.banking_backend.entity.AccountStatus;
import ali.com.banking.banking_backend.entity.Customer;
import ali.com.banking.banking_backend.exception.AccountNotFoundException;
import ali.com.banking.banking_backend.exception.CustomerAccessDeniedException;
import ali.com.banking.banking_backend.mapper.AccountMapper;
import ali.com.banking.banking_backend.repository.AccountRepository;
import ali.com.banking.banking_backend.repository.CustomerRepository;
import ali.com.banking.banking_backend.service.AccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    public AccountServiceImpl(AccountRepository accountRepository, CustomerRepository customerRepository) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional
    public AccountResponse createAccount(AccountCreateRequest request, String authenticatedEmail) {
        if (request == null) {
            throw new IllegalArgumentException("Account creation request must not be null");
        }

        Customer customer = findAuthenticatedCustomer(authenticatedEmail);

        Account account = AccountMapper.toEntity(request);
        account.setCustomer(customer);
        account.setAccountNumber(generateUniqueAccountNumber());
        account.setBalance(BigDecimal.ZERO);
        account.setStatus(AccountStatus.ACTIVE);

        Account savedAccount = accountRepository.save(account);

        return AccountMapper.toResponse(savedAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long accountId, String authenticatedEmail) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID must not be null");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));

        ensureOwner(account, authenticatedEmail);

        return AccountMapper.toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getMyAccounts(String authenticatedEmail) {
        Customer customer = findAuthenticatedCustomer(authenticatedEmail);
        Long customerId = customer.getCustomerId();

        return accountRepository.findByCustomerCustomerId(customerId).stream()
                .map(AccountMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void closeAccount(Long accountId, String authenticatedEmail) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID must not be null");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        ensureOwner(account, authenticatedEmail);

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new IllegalStateException("Account is already closed");
        }

        account.setStatus(AccountStatus.CLOSED);
        accountRepository.save(account);
    }

    @Override
    @Transactional
    public void suspendAccount(Long accountId, String authenticatedEmail) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID must not be null");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        ensureOwner(account, authenticatedEmail);

        if (account.getStatus() == AccountStatus.SUSPENDED) {
            throw new IllegalStateException("Account is already suspended");
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new IllegalStateException("Closed account cannot be suspended");
        }

        account.setStatus(AccountStatus.SUSPENDED);
        accountRepository.save(account);
    }

    @Override
    @Transactional
    public void activateAccount(Long accountId, String authenticatedEmail) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID must not be null");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        ensureOwner(account, authenticatedEmail);

        if (account.getStatus() == AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is already active");
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new IllegalStateException("Closed account cannot be activated");
        }

        account.setStatus(AccountStatus.ACTIVE);
        accountRepository.save(account);
    }

    private Customer findAuthenticatedCustomer(String authenticatedEmail) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new CustomerAccessDeniedException("Customer access denied");
        }

        return customerRepository.findByEmail(authenticatedEmail.trim().toLowerCase())
                .orElseThrow(() -> new CustomerAccessDeniedException("Customer access denied"));
    }

    private void ensureOwner(Account account, String authenticatedEmail) {
        if (authenticatedEmail == null || account.getCustomer() == null ||
                !authenticatedEmail.equalsIgnoreCase(account.getCustomer().getEmail())) {
            throw new CustomerAccessDeniedException("Customer access denied");
        }
    }

    private String generateUniqueAccountNumber() {
        String accountNumber;

        do {
            long randomNumber = Math.abs(ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 9_999_999_999_999L));
            accountNumber = "ACC-" + randomNumber;
        } while (accountRepository.existsByAccountNumber(accountNumber));

        return accountNumber;
    }
}

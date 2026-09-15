package ali.com.banking.banking_backend.service.impl;

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
import ali.com.banking.banking_backend.transaction.repository.TransactionRepository;
import ali.com.banking.banking_backend.transaction.service.TransactionService;
import ali.com.banking.banking_backend.transaction.service.impl.TransactionServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private TransactionService service() {
        return new TransactionServiceImpl(accountRepository, transactionRepository);
    }

    @Test
    void deposit_whenValid_shouldIncreaseBalanceAndPersistTransaction() {
        Account account = accountWithStatus(1L, AccountStatus.ACTIVE, new BigDecimal("100.00"));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            transaction.setCreatedAt(LocalDateTime.of(2025, 1, 15, 9, 30));
            return transaction;
        });

        TransactionResponse response = service().deposit(
                1L,
                DepositRequest.builder().amount(new BigDecimal("25.50")).build(),
                "alice@example.com"
        );

        assertEquals("ACC-123456", response.getAccountNumber());
        assertEquals(TransactionType.DEPOSIT, response.getType());
        assertEquals(new BigDecimal("25.50"), response.getAmount());
        assertEquals(new BigDecimal("125.50"), response.getBalanceAfter());
        assertEquals(new BigDecimal("125.50"), account.getBalance());
        assertNotNull(response.getReference());
        assertEquals(LocalDateTime.of(2025, 1, 15, 9, 30), response.getCreatedAt());

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        Transaction savedTransaction = transactionCaptor.getValue();
        assertEquals(account, savedTransaction.getAccount());
        assertEquals(TransactionType.DEPOSIT, savedTransaction.getType());
        assertEquals(new BigDecimal("25.50"), savedTransaction.getAmount());
        assertEquals(new BigDecimal("125.50"), savedTransaction.getBalanceAfter());
        assertNotNull(savedTransaction.getReference());
        assertEquals(LocalDateTime.of(2025, 1, 15, 9, 30), savedTransaction.getCreatedAt());
    }

        @Test
        void deposit_whenAmountHasSeventeenIntegerDigitsAndTwoDecimals_shouldAccept() {
                Account account = accountWithStatus(1L, AccountStatus.ACTIVE, BigDecimal.ZERO);
                when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
                when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
                        Transaction transaction = invocation.getArgument(0);
                        transaction.setCreatedAt(LocalDateTime.of(2025, 4, 8, 9, 30));
                        return transaction;
                });

                TransactionResponse response = service().deposit(
                                1L,
                                DepositRequest.builder().amount(new BigDecimal("12345678901234567.89")).build(),
                                "alice@example.com"
                );

                assertEquals(new BigDecimal("12345678901234567.89"), response.getAmount());
                assertEquals(new BigDecimal("12345678901234567.89"), response.getBalanceAfter());
                assertEquals(new BigDecimal("12345678901234567.89"), account.getBalance());
        }

        @Test
        void deposit_whenAmountIsMaxAllowedBalance_shouldAccept() {
                Account account = accountWithStatus(1L, AccountStatus.ACTIVE, BigDecimal.ZERO);
                when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
                when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
                        Transaction transaction = invocation.getArgument(0);
                        transaction.setCreatedAt(LocalDateTime.of(2025, 4, 9, 10, 15));
                        return transaction;
                });

                TransactionResponse response = service().deposit(
                                1L,
                                DepositRequest.builder().amount(new BigDecimal("99999999999999999.99")).build(),
                                "alice@example.com"
                );

                assertEquals(new BigDecimal("99999999999999999.99"), response.getAmount());
                assertEquals(new BigDecimal("99999999999999999.99"), response.getBalanceAfter());
                assertEquals(new BigDecimal("99999999999999999.99"), account.getBalance());
        }

    @Test
    void withdraw_whenPartialAmount_shouldDecreaseBalanceAndPersistTransaction() {
        Account account = accountWithStatus(1L, AccountStatus.ACTIVE, new BigDecimal("200.00"));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            transaction.setCreatedAt(LocalDateTime.of(2025, 2, 10, 12, 45));
            return transaction;
        });

        TransactionResponse response = service().withdraw(
                1L,
                WithdrawalRequest.builder().amount(new BigDecimal("75.25")).build(),
                "alice@example.com"
        );

        assertEquals(TransactionType.WITHDRAWAL, response.getType());
        assertEquals(new BigDecimal("75.25"), response.getAmount());
        assertEquals(new BigDecimal("124.75"), response.getBalanceAfter());
        assertEquals(new BigDecimal("124.75"), account.getBalance());
        assertEquals("Withdrawal from account", response.getDescription());
        assertNotNull(response.getReference());
        assertEquals(LocalDateTime.of(2025, 2, 10, 12, 45), response.getCreatedAt());

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        Transaction savedTransaction = transactionCaptor.getValue();
        assertEquals(account, savedTransaction.getAccount());
        assertEquals(TransactionType.WITHDRAWAL, savedTransaction.getType());
        assertEquals(new BigDecimal("75.25"), savedTransaction.getAmount());
        assertEquals(new BigDecimal("124.75"), savedTransaction.getBalanceAfter());
        assertEquals("Withdrawal from account", savedTransaction.getDescription());
        assertNotNull(savedTransaction.getReference());
    }

    @Test
    void withdraw_whenExactBalance_shouldLeaveZeroAndPersistTransaction() {
        Account account = accountWithStatus(1L, AccountStatus.ACTIVE, new BigDecimal("100.00"));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            transaction.setCreatedAt(LocalDateTime.of(2025, 3, 1, 8, 0));
            return transaction;
        });

        TransactionResponse response = service().withdraw(
                1L,
                WithdrawalRequest.builder().amount(new BigDecimal("100.00")).build(),
                "alice@example.com"
        );

        assertEquals(TransactionType.WITHDRAWAL, response.getType());
        assertEquals(new BigDecimal("100.00"), response.getAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), response.getBalanceAfter());
        assertEquals(BigDecimal.ZERO.setScale(2), account.getBalance());
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void withdraw_whenInsufficientFunds_shouldReject() {
        Account account = accountWithStatus(1L, AccountStatus.ACTIVE, new BigDecimal("50.00"));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service().withdraw(1L, WithdrawalRequest.builder().amount(new BigDecimal("60.00")).build(), "alice@example.com"));

        assertTrue(ex.getMessage().toLowerCase().contains("insufficient funds"));
        assertEquals(new BigDecimal("50.00"), account.getBalance());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void deposit_whenRequestIsNull_shouldRejectBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> service().deposit(1L, null, "alice@example.com"));
        verifyNoInteractions(accountRepository, transactionRepository);
    }

    @Test
    void deposit_whenAmountIsNull_shouldRejectBeforeRepositoryAccess() {
        DepositRequest request = DepositRequest.builder().amount(null).build();

        assertThrows(IllegalArgumentException.class,
                () -> service().deposit(1L, request, "alice@example.com"));
        verifyNoInteractions(accountRepository, transactionRepository);
    }

    @Test
    void withdraw_whenRequestIsNull_shouldRejectBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> service().withdraw(1L, null, "alice@example.com"));
        verifyNoInteractions(accountRepository, transactionRepository);
    }

    @Test
    void withdraw_whenAmountIsNull_shouldRejectBeforeRepositoryAccess() {
        WithdrawalRequest request = WithdrawalRequest.builder().amount(null).build();

        assertThrows(IllegalArgumentException.class,
                () -> service().withdraw(1L, request, "alice@example.com"));
        verifyNoInteractions(accountRepository, transactionRepository);
    }

    @Test
    void deposit_andWithdrawal_whenAccountIdIsNullOrNonPositive_shouldRejectBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> service().deposit(null, validDeposit(), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().deposit(0L, validDeposit(), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().deposit(-1L, validDeposit(), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().withdraw(null, validWithdrawal(), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().withdraw(0L, validWithdrawal(), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().withdraw(-1L, validWithdrawal(), "alice@example.com"));

        verifyNoInteractions(accountRepository, transactionRepository);
    }

    @Test
    void deposit_whenAmountIsZeroOrNegativeOrHasTooMuchPrecision_shouldReject() {
        assertThrows(IllegalArgumentException.class,
                () -> service().deposit(1L, DepositRequest.builder().amount(BigDecimal.ZERO).build(), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().deposit(1L, DepositRequest.builder().amount(new BigDecimal("-10.00")).build(), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().deposit(1L, DepositRequest.builder().amount(new BigDecimal("10.001")).build(), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().deposit(1L, DepositRequest.builder().amount(new BigDecimal("1.234")).build(), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().deposit(1L, DepositRequest.builder().amount(new BigDecimal("1E+17")).build(), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().deposit(1L, DepositRequest.builder().amount(new BigDecimal("123456789012345678.90")).build(), "alice@example.com"));

        verifyNoInteractions(accountRepository, transactionRepository);
    }

    @Test
    void withdraw_whenAmountIsZeroOrNegativeOrHasTooMuchPrecision_shouldReject() {
        assertThrows(IllegalArgumentException.class,
                () -> service().withdraw(1L, WithdrawalRequest.builder().amount(BigDecimal.ZERO).build(), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().withdraw(1L, WithdrawalRequest.builder().amount(new BigDecimal("-10.00")).build(), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().withdraw(1L, WithdrawalRequest.builder().amount(new BigDecimal("10.001")).build(), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().withdraw(1L, WithdrawalRequest.builder().amount(new BigDecimal("1.234")).build(), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().withdraw(1L, WithdrawalRequest.builder().amount(new BigDecimal("1E+17")).build(), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().withdraw(1L, WithdrawalRequest.builder().amount(new BigDecimal("123456789012345678.90")).build(), "alice@example.com"));

        verifyNoInteractions(accountRepository, transactionRepository);
    }

    @Test
    void deposit_whenAccountDoesNotExist_shouldThrowAccountNotFound() {
        when(accountRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> service().deposit(99L, validDeposit(), "alice@example.com"));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void withdraw_whenWrongOwner_shouldRejectAndLeaveBalanceUntouched() {
        Account account = accountWithStatus(1L, AccountStatus.ACTIVE, new BigDecimal("100.00"));
        account.setCustomer(customer("bob@example.com"));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

        assertThrows(CustomerAccessDeniedException.class,
                () -> service().withdraw(1L, validWithdrawal(), "alice@example.com"));

        assertEquals(new BigDecimal("100.00"), account.getBalance());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void deposit_whenWrongOwner_shouldRejectAndLeaveBalanceUntouched() {
        Account account = accountWithStatus(1L, AccountStatus.ACTIVE, new BigDecimal("100.00"));
        account.setCustomer(customer("bob@example.com"));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

        assertThrows(CustomerAccessDeniedException.class,
                () -> service().deposit(1L, validDeposit(), "alice@example.com"));

        assertEquals(new BigDecimal("100.00"), account.getBalance());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

        @Test
        void deposit_whenAuthenticatedEmailIsNull_shouldRejectAndLeaveBalanceUntouched() {
                Account account = accountWithStatus(1L, AccountStatus.ACTIVE, new BigDecimal("100.00"));
                when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

                assertThrows(CustomerAccessDeniedException.class,
                                () -> service().deposit(1L, validDeposit(), null));

                assertEquals(new BigDecimal("100.00"), account.getBalance());
                verify(transactionRepository, never()).save(any(Transaction.class));
        }

        @Test
        void deposit_whenAccountHasNoCustomer_shouldRejectAndLeaveBalanceUntouched() {
                Account account = accountWithStatus(1L, AccountStatus.ACTIVE, new BigDecimal("100.00"));
                account.setCustomer(null);
                when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

                assertThrows(CustomerAccessDeniedException.class,
                                () -> service().deposit(1L, validDeposit(), "alice@example.com"));

                assertEquals(new BigDecimal("100.00"), account.getBalance());
                verify(transactionRepository, never()).save(any(Transaction.class));
        }

        @Test
        void deposit_whenAccountBalanceIsNull_shouldRejectAndLeaveBalanceUntouched() {
                Account account = accountWithStatus(1L, AccountStatus.ACTIVE, null);
                when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

                assertThrows(IllegalStateException.class,
                                () -> service().deposit(1L, validDeposit(), "alice@example.com"));

                assertEquals(null, account.getBalance());
                verify(transactionRepository, never()).save(any(Transaction.class));
        }

    @Test
    void deposit_andWithdraw_whenStatusIsSuspendedOrClosed_shouldReject() {
        Account suspended = accountWithStatus(1L, AccountStatus.SUSPENDED, new BigDecimal("100.00"));
        Account closed = accountWithStatus(2L, AccountStatus.CLOSED, new BigDecimal("100.00"));

        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(suspended));
        assertThrows(IllegalStateException.class,
                () -> service().deposit(1L, validDeposit(), "alice@example.com"));
        assertEquals(new BigDecimal("100.00"), suspended.getBalance());
        verify(transactionRepository, never()).save(any(Transaction.class));

        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(closed));
        assertThrows(IllegalStateException.class,
                () -> service().withdraw(2L, validWithdrawal(), "alice@example.com"));
        assertEquals(new BigDecimal("100.00"), closed.getBalance());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void deposit_whenBalanceOverflow_shouldReject() {
                Account account = accountWithStatus(1L, AccountStatus.ACTIVE, new BigDecimal("99999999999999999.99"));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

        assertThrows(IllegalArgumentException.class,
                () -> service().deposit(1L, DepositRequest.builder().amount(new BigDecimal("0.01")).build(), "alice@example.com"));

                assertEquals(new BigDecimal("99999999999999999.99"), account.getBalance());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transfer_whenValid_shouldUpdateBothBalancesAndPersistLinkedTransactions() {
        Account source = accountWithStatus(10L, AccountStatus.ACTIVE, new BigDecimal("200.00"));
        Account destination = accountWithStatus(5L, AccountStatus.ACTIVE, new BigDecimal("50.00"));
        when(accountRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(destination));
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(source));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransferResponse response = service().transfer(
                10L,
                validTransfer(5L, "75.25"),
                "alice@example.com");

        assertEquals(new BigDecimal("124.75"), source.getBalance());
        assertEquals(new BigDecimal("125.25"), destination.getBalance());

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(transactionCaptor.capture());
        List<Transaction> savedTransactions = transactionCaptor.getAllValues();
        Transaction sourceTransaction = savedTransactions.get(0);
        Transaction destinationTransaction = savedTransactions.get(1);

        assertEquals(source, sourceTransaction.getAccount());
        assertEquals(TransactionType.WITHDRAWAL, sourceTransaction.getType());
        assertEquals(new BigDecimal("75.25"), sourceTransaction.getAmount());
        assertEquals(new BigDecimal("124.75"), sourceTransaction.getBalanceAfter());
        assertEquals("Transfer to account", sourceTransaction.getDescription());

        assertEquals(destination, destinationTransaction.getAccount());
        assertEquals(TransactionType.DEPOSIT, destinationTransaction.getType());
        assertEquals(new BigDecimal("75.25"), destinationTransaction.getAmount());
        assertEquals(new BigDecimal("125.25"), destinationTransaction.getBalanceAfter());
        assertEquals("Transfer from account", destinationTransaction.getDescription());

        assertNotNull(sourceTransaction.getReference());
        assertNotNull(destinationTransaction.getReference());
        assertNotEquals(sourceTransaction.getReference(), destinationTransaction.getReference());
        assertNotNull(sourceTransaction.getTransferReference());
        assertEquals(sourceTransaction.getTransferReference(), destinationTransaction.getTransferReference());
        assertEquals(sourceTransaction.getTransferReference(), response.getTransferReference());
        assertEquals(TransactionType.WITHDRAWAL, response.getSourceTransaction().getType());
        assertEquals(TransactionType.DEPOSIT, response.getDestinationTransaction().getType());

        InOrder order = inOrder(accountRepository);
        order.verify(accountRepository).findByIdForUpdate(5L);
        order.verify(accountRepository).findByIdForUpdate(10L);
    }

    @Test
    void transfer_whenEntireSourceBalanceIsTransferred_shouldLeaveSourceAtZero() {
        Account source = accountWithStatus(1L, AccountStatus.ACTIVE, new BigDecimal("100.00"));
        Account destination = accountWithStatus(2L, AccountStatus.ACTIVE, new BigDecimal("25.00"));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(destination));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().transfer(1L, validTransfer(2L, "100.00"), "alice@example.com");

        assertEquals(BigDecimal.ZERO.setScale(2), source.getBalance());
        assertEquals(new BigDecimal("125.00"), destination.getBalance());
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    void transfer_whenInsufficientFunds_shouldRejectWithoutChangesOrPersistence() {
        Account source = accountWithStatus(1L, AccountStatus.ACTIVE, new BigDecimal("50.00"));
        Account destination = accountWithStatus(2L, AccountStatus.ACTIVE, new BigDecimal("25.00"));
        stubLockedAccounts(source, destination);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service().transfer(1L, validTransfer(2L, "50.01"), "alice@example.com"));

        assertEquals("Insufficient funds", exception.getMessage());
        assertEquals(new BigDecimal("50.00"), source.getBalance());
        assertEquals(new BigDecimal("25.00"), destination.getBalance());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transfer_whenSourceAccountDoesNotExist_shouldThrowAccountNotFoundBeforeDestinationLock() {
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> service().transfer(1L, validTransfer(2L, "10.00"), "alice@example.com"));

        verify(accountRepository).findByIdForUpdate(1L);
        verify(accountRepository, never()).findByIdForUpdate(2L);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void transfer_whenDestinationAccountDoesNotExist_shouldThrowAccountNotFoundWithoutChanges() {
        Account source = accountWithStatus(1L, AccountStatus.ACTIVE, new BigDecimal("100.00"));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> service().transfer(1L, validTransfer(2L, "10.00"), "alice@example.com"));

        assertEquals(new BigDecimal("100.00"), source.getBalance());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transfer_whenSourceAndDestinationAreTheSame_shouldRejectBeforeLocking() {
        Account source = accountWithStatus(1L, AccountStatus.ACTIVE, new BigDecimal("100.00"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service().transfer(1L, validTransfer(1L, "10.00"), "alice@example.com"));

        assertEquals("Source and destination accounts must be different", exception.getMessage());
        assertEquals(new BigDecimal("100.00"), source.getBalance());
        verifyNoInteractions(accountRepository, transactionRepository);
    }

    @Test
    void transfer_whenSourceIsNotOwnedByAuthenticatedCustomer_shouldRejectWithoutPersistence() {
        Account source = accountWithStatus(1L, AccountStatus.ACTIVE, new BigDecimal("100.00"));
        source.setCustomer(customer("bob@example.com"));
        Account destination = accountWithStatus(2L, AccountStatus.ACTIVE, new BigDecimal("25.00"));
        stubLockedAccounts(source, destination);

        assertThrows(CustomerAccessDeniedException.class,
                () -> service().transfer(1L, validTransfer(2L, "10.00"), "alice@example.com"));

        assertEquals(new BigDecimal("100.00"), source.getBalance());
        assertEquals(new BigDecimal("25.00"), destination.getBalance());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transfer_whenSourceOrDestinationIsSuspendedOrClosed_shouldRejectWithoutChanges() {
        Account active = accountWithStatus(1L, AccountStatus.ACTIVE, new BigDecimal("100.00"));
        Account suspended = accountWithStatus(2L, AccountStatus.SUSPENDED, new BigDecimal("25.00"));
        Account closed = accountWithStatus(3L, AccountStatus.CLOSED, new BigDecimal("25.00"));

        stubLockedAccounts(active, suspended);
        assertThrows(IllegalStateException.class,
                () -> service().transfer(1L, validTransfer(2L, "10.00"), "alice@example.com"));
        assertEquals(new BigDecimal("100.00"), active.getBalance());
        assertEquals(new BigDecimal("25.00"), suspended.getBalance());

        reset(accountRepository, transactionRepository);
        stubLockedAccounts(active, closed);
        assertThrows(IllegalStateException.class,
                () -> service().transfer(1L, validTransfer(3L, "10.00"), "alice@example.com"));
        assertEquals(new BigDecimal("100.00"), active.getBalance());
        assertEquals(new BigDecimal("25.00"), closed.getBalance());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transfer_whenDestinationBalanceWouldOverflow_shouldRejectWithoutChangesOrPersistence() {
        Account source = accountWithStatus(1L, AccountStatus.ACTIVE, new BigDecimal("100.00"));
        Account destination = accountWithStatus(2L, AccountStatus.ACTIVE,
                new BigDecimal("99999999999999999.99"));
        stubLockedAccounts(source, destination);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service().transfer(1L, validTransfer(2L, "0.01"), "alice@example.com"));

        assertEquals("Transfer would exceed the maximum allowed destination account balance", exception.getMessage());
        assertEquals(new BigDecimal("100.00"), source.getBalance());
        assertEquals(new BigDecimal("99999999999999999.99"), destination.getBalance());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transfer_whenRequestOrAmountIsInvalid_shouldRejectBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> service().transfer(1L, null, "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().transfer(1L, TransferRequest.builder().destinationAccountId(2L).build(), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().transfer(1L, validTransfer(2L, "0"), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().transfer(1L, validTransfer(2L, "-1.00"), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().transfer(1L, validTransfer(2L, "10.001"), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().transfer(1L, validTransfer(2L, "123456789012345678.90"), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().transfer(null, validTransfer(2L, "10.00"), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().transfer(0L, validTransfer(2L, "10.00"), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().transfer(1L, validTransfer(null, "10.00"), "alice@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service().transfer(1L, validTransfer(0L, "10.00"), "alice@example.com"));

        verifyNoInteractions(accountRepository, transactionRepository);
    }

    @Test
    void getAccountTransactions_whenNoTransactions_shouldReturnEmptyList() {
        Account account = accountWithStatus(1L, AccountStatus.ACTIVE, new BigDecimal("100.00"));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.findByAccount_AccountIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        List<TransactionResponse> history = service().getAccountTransactions(1L, "alice@example.com");

        assertNotNull(history);
        assertTrue(history.isEmpty());
    }

    @Test
    void getAccountTransactions_whenTransactionsExist_shouldMapThemToResponses() {
        Account account = accountWithStatus(1L, AccountStatus.SUSPENDED, new BigDecimal("150.00"));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        Transaction first = Transaction.builder()
                .transactionId(10L)
                .account(account)
                .type(TransactionType.DEPOSIT)
                .amount(new BigDecimal("50.00"))
                .balanceAfter(new BigDecimal("100.00"))
                .description("Deposit to account")
                .reference("ref-1")
                .createdAt(LocalDateTime.of(2025, 1, 10, 8, 30))
                .build();

        Transaction second = Transaction.builder()
                .transactionId(11L)
                .account(account)
                .type(TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("25.00"))
                .balanceAfter(new BigDecimal("75.00"))
                .description("Withdrawal from account")
                .reference("ref-2")
                .createdAt(LocalDateTime.of(2025, 1, 11, 9, 15))
                .build();

        when(transactionRepository.findByAccount_AccountIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(second, first));

        List<TransactionResponse> history = service().getAccountTransactions(1L, "alice@example.com");

        assertEquals(2, history.size());
        assertEquals("ref-2", history.get(0).getReference());
        assertEquals("ACC-123456", history.get(0).getAccountNumber());
        assertEquals(TransactionType.WITHDRAWAL, history.get(0).getType());
        assertEquals(new BigDecimal("75.00"), history.get(0).getBalanceAfter());
        assertEquals("ref-1", history.get(1).getReference());
        assertEquals(TransactionType.DEPOSIT, history.get(1).getType());
    }

    @Test
    void getAccountTransactions_whenAccountIsSuspendedOrClosed_shouldStillAllowHistoryAccess() {
        Account suspended = accountWithStatus(1L, AccountStatus.SUSPENDED, new BigDecimal("150.00"));
        Account closed = accountWithStatus(2L, AccountStatus.CLOSED, new BigDecimal("150.00"));

        when(accountRepository.findById(1L)).thenReturn(Optional.of(suspended));
        when(transactionRepository.findByAccount_AccountIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        List<TransactionResponse> suspendedHistory = service().getAccountTransactions(1L, "alice@example.com");
        assertNotNull(suspendedHistory);

        when(accountRepository.findById(2L)).thenReturn(Optional.of(closed));
        when(transactionRepository.findByAccount_AccountIdOrderByCreatedAtDesc(2L)).thenReturn(List.of());
        List<TransactionResponse> closedHistory = service().getAccountTransactions(2L, "alice@example.com");
        assertNotNull(closedHistory);
    }

    @Test
    void getAccountTransactions_whenAccountDoesNotExist_shouldThrowAccountNotFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> service().getAccountTransactions(99L, "alice@example.com"));
        verify(transactionRepository, never()).findByAccount_AccountIdOrderByCreatedAtDesc(anyLong());
    }

    @Test
    void getAccountTransactions_whenWrongOwner_shouldRejectWithoutQueryingHistory() {
        Account account = accountWithStatus(1L, AccountStatus.ACTIVE, new BigDecimal("100.00"));
        account.setCustomer(customer("bob@example.com"));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThrows(CustomerAccessDeniedException.class,
                () -> service().getAccountTransactions(1L, "alice@example.com"));

        verify(transactionRepository, never()).findByAccount_AccountIdOrderByCreatedAtDesc(anyLong());
    }

    private DepositRequest validDeposit() {
        return DepositRequest.builder().amount(new BigDecimal("10.00")).build();
    }

    private WithdrawalRequest validWithdrawal() {
        return WithdrawalRequest.builder().amount(new BigDecimal("10.00")).build();
    }

        private TransferRequest validTransfer(Long destinationAccountId, String amount) {
                return TransferRequest.builder()
                                .destinationAccountId(destinationAccountId)
                                .amount(new BigDecimal(amount))
                                .build();
        }

        private void stubLockedAccounts(Account source, Account destination) {
                Long firstAccountId = Math.min(source.getAccountId(), destination.getAccountId());
                Long secondAccountId = Math.max(source.getAccountId(), destination.getAccountId());
                Account firstAccount = source.getAccountId().equals(firstAccountId) ? source : destination;
                Account secondAccount = source.getAccountId().equals(secondAccountId) ? source : destination;
                when(accountRepository.findByIdForUpdate(firstAccountId)).thenReturn(Optional.of(firstAccount));
                when(accountRepository.findByIdForUpdate(secondAccountId)).thenReturn(Optional.of(secondAccount));
        }

        private Account accountWithStatus(Long accountId, AccountStatus status, BigDecimal balance) {
        Account account = Account.builder()
                                .accountId(accountId)
                .accountNumber("ACC-123456")
                .customer(customer("alice@example.com"))
                .status(status)
                .balance(balance)
                .build();
        return account;
    }

    private Customer customer(String email) {
        return Customer.builder()
                .customerId(1L)
                .email(email)
                .build();
    }
}

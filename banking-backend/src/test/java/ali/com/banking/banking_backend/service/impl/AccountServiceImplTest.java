package ali.com.banking.banking_backend.service.impl;

import ali.com.banking.banking_backend.account.entity.Account;
import ali.com.banking.banking_backend.account.entity.AccountStatus;
import ali.com.banking.banking_backend.account.repository.AccountRepository;
import ali.com.banking.banking_backend.account.service.impl.AccountServiceImpl;
import ali.com.banking.banking_backend.customer.entity.Customer;
import ali.com.banking.banking_backend.customer.repository.CustomerRepository;
import ali.com.banking.banking_backend.exception.AccountNotFoundException;
import ali.com.banking.banking_backend.exception.CustomerAccessDeniedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Test
    void closeAccount_whenActive_changesStatusToClosedAndSaves() {
        Account account = accountWithStatus(AccountStatus.ACTIVE);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        service().closeAccount(1L, "alice@example.com");

        assertEquals(AccountStatus.CLOSED, account.getStatus());
        verify(accountRepository).save(account);
    }

    @Test
    void closeAccount_whenSuspended_changesStatusToClosedAndSaves() {
        Account account = accountWithStatus(AccountStatus.SUSPENDED);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        service().closeAccount(1L, "alice@example.com");

        assertEquals(AccountStatus.CLOSED, account.getStatus());
        verify(accountRepository).save(account);
    }

    @Test
    void closeAccount_whenClosed_throwsIllegalStateExceptionWithoutSaving() {
        Account account = accountWithStatus(AccountStatus.CLOSED);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThrows(IllegalStateException.class,
                () -> service().closeAccount(1L, "alice@example.com"));

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void closeAccount_whenAccountDoesNotExist_throwsAccountNotFoundExceptionWithoutSaving() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> service().closeAccount(99L, "alice@example.com"));

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void closeAccount_whenAnotherCustomerOwnsAccount_throwsAccessDeniedWithoutSaving() {
        Account account = accountWithStatus(AccountStatus.ACTIVE);
        account.setCustomer(customer("bob@example.com"));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThrows(CustomerAccessDeniedException.class,
                () -> service().closeAccount(1L, "alice@example.com"));

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void suspendAccount_whenActive_changesStatusToSuspendedAndSaves() {
        Account account = accountWithStatus(AccountStatus.ACTIVE);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        service().suspendAccount(1L, "alice@example.com");

        assertEquals(AccountStatus.SUSPENDED, account.getStatus());
        verify(accountRepository).save(account);
    }

    @Test
    void suspendAccount_whenSuspended_throwsIllegalStateExceptionWithoutSaving() {
        Account account = accountWithStatus(AccountStatus.SUSPENDED);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThrows(IllegalStateException.class,
                () -> service().suspendAccount(1L, "alice@example.com"));

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void suspendAccount_whenClosed_throwsIllegalStateExceptionWithoutSaving() {
        Account account = accountWithStatus(AccountStatus.CLOSED);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThrows(IllegalStateException.class,
                () -> service().suspendAccount(1L, "alice@example.com"));

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void suspendAccount_whenAccountDoesNotExist_throwsAccountNotFoundExceptionWithoutSaving() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> service().suspendAccount(99L, "alice@example.com"));

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void suspendAccount_whenAnotherCustomerOwnsAccount_throwsAccessDeniedWithoutSaving() {
        Account account = accountWithStatus(AccountStatus.ACTIVE);
        account.setCustomer(customer("bob@example.com"));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThrows(CustomerAccessDeniedException.class,
                () -> service().suspendAccount(1L, "alice@example.com"));

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void activateAccount_whenSuspended_changesStatusToActiveAndSaves() {
        Account account = accountWithStatus(AccountStatus.SUSPENDED);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        service().activateAccount(1L, "alice@example.com");

        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        verify(accountRepository).save(account);
    }

    @Test
    void activateAccount_whenActive_throwsIllegalStateExceptionWithoutSaving() {
        Account account = accountWithStatus(AccountStatus.ACTIVE);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThrows(IllegalStateException.class,
                () -> service().activateAccount(1L, "alice@example.com"));

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void activateAccount_whenClosed_throwsIllegalStateExceptionWithoutSaving() {
        Account account = accountWithStatus(AccountStatus.CLOSED);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThrows(IllegalStateException.class,
                () -> service().activateAccount(1L, "alice@example.com"));

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void activateAccount_whenAccountDoesNotExist_throwsAccountNotFoundExceptionWithoutSaving() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> service().activateAccount(99L, "alice@example.com"));

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void activateAccount_whenAnotherCustomerOwnsAccount_throwsAccessDeniedWithoutSaving() {
        Account account = accountWithStatus(AccountStatus.SUSPENDED);
        account.setCustomer(customer("bob@example.com"));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThrows(CustomerAccessDeniedException.class,
                () -> service().activateAccount(1L, "alice@example.com"));

        verify(accountRepository, never()).save(any(Account.class));
    }

    private AccountServiceImpl service() {
        return new AccountServiceImpl(accountRepository, customerRepository);
    }

    private Account accountWithStatus(AccountStatus status) {
        return Account.builder()
                .accountId(1L)
                .customer(customer("alice@example.com"))
                .status(status)
                .build();
    }

    private Customer customer(String email) {
        return Customer.builder()
                .customerId(1L)
                .email(email)
                .build();
    }
}

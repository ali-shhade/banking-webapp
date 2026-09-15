package ali.com.banking.banking_backend.integration.transaction;

import ali.com.banking.banking_backend.account.dto.AccountCreateRequest;
import ali.com.banking.banking_backend.account.entity.Account;
import ali.com.banking.banking_backend.account.entity.AccountStatus;
import ali.com.banking.banking_backend.account.entity.AccountType;
import ali.com.banking.banking_backend.account.repository.AccountRepository;
import ali.com.banking.banking_backend.customer.dto.CustomerRegistrationRequest;
import ali.com.banking.banking_backend.customer.entity.Customer;
import ali.com.banking.banking_backend.customer.repository.CustomerRepository;
import ali.com.banking.banking_backend.integration.BaseIntegrationTest;
import ali.com.banking.banking_backend.transaction.dto.DepositRequest;
import ali.com.banking.banking_backend.transaction.dto.TransferRequest;
import ali.com.banking.banking_backend.transaction.dto.WithdrawalRequest;
import ali.com.banking.banking_backend.transaction.entity.Transaction;
import ali.com.banking.banking_backend.transaction.entity.TransactionType;
import ali.com.banking.banking_backend.transaction.repository.TransactionRepository;
import ali.com.banking.banking_backend.transaction.service.TransactionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TransactionIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "StrongPass123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

        @Autowired
        private TransactionService transactionService;

    @Autowired
    private CustomerRepository customerRepository;

    @BeforeEach
    void cleanDatabase() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void deposit_increasesBalanceAndPersistsTransaction() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account account = createAccount(session.token(), AccountType.CHECKING);

        MvcResult result = mockMvc.perform(post(transactionUrl(account, "deposit"))
                        .header("Authorization", bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new DepositRequest(new BigDecimal("100.25")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").isNumber())
                .andExpect(jsonPath("$.accountNumber").value(account.getAccountNumber()))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.amount").value(100.25))
                .andExpect(jsonPath("$.balanceAfter").value(100.25))
                .andExpect(jsonPath("$.reference", not(blankOrNullString())))
                .andExpect(jsonPath("$.createdAt", not(blankOrNullString())))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        Transaction saved = transactionRepository.findById(response.get("transactionId").asLong()).orElseThrow();
        Account updated = accountRepository.findById(account.getAccountId()).orElseThrow();

        assertEquals(0, updated.getBalance().compareTo(new BigDecimal("100.25")));
        assertEquals(account.getAccountId(), saved.getAccount().getAccountId());
        assertEquals(TransactionType.DEPOSIT, saved.getType());
        assertEquals(0, saved.getAmount().compareTo(new BigDecimal("100.25")));
        assertEquals(0, saved.getBalanceAfter().compareTo(new BigDecimal("100.25")));
        assertNotNull(saved.getReference());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void multipleDeposits_accumulateBalanceAndPersistBothTransactions() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account account = createAccount(session.token(), AccountType.SAVINGS);

        deposit(account, session.token(), "100.00");
        deposit(account, session.token(), "50.00");

        Account updated = accountRepository.findById(account.getAccountId()).orElseThrow();
        List<Transaction> transactions = transactionRepository
                .findByAccount_AccountIdOrderByCreatedAtDesc(account.getAccountId());

        assertEquals(0, updated.getBalance().compareTo(new BigDecimal("150.00")));
        assertEquals(2, transactions.size());
        assertEquals(0, transactions.get(0).getBalanceAfter().compareTo(new BigDecimal("150.00")));
        assertEquals(0, transactions.get(1).getBalanceAfter().compareTo(new BigDecimal("100.00")));
    }

    @Test
    void withdrawal_decreasesBalanceAndPersistsTransaction() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account account = createAccount(session.token(), AccountType.CHECKING);
        setBalance(account, "200.00");

        MvcResult result = mockMvc.perform(post(transactionUrl(account, "withdraw"))
                        .header("Authorization", bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new WithdrawalRequest(new BigDecimal("75.25")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").isNumber())
                .andExpect(jsonPath("$.accountNumber").value(account.getAccountNumber()))
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.amount").value(75.25))
                .andExpect(jsonPath("$.balanceAfter").value(124.75))
                .andExpect(jsonPath("$.reference", not(blankOrNullString())))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        Account updated = accountRepository.findById(account.getAccountId()).orElseThrow();
        Transaction saved = transactionRepository.findById(response.get("transactionId").asLong()).orElseThrow();

        assertEquals(0, updated.getBalance().compareTo(new BigDecimal("124.75")));
        assertEquals(account.getAccountId(), saved.getAccount().getAccountId());
        assertEquals(TransactionType.WITHDRAWAL, saved.getType());
        assertEquals(0, saved.getAmount().compareTo(new BigDecimal("75.25")));
        assertEquals(0, saved.getBalanceAfter().compareTo(new BigDecimal("124.75")));
    }

    @Test
    void exactWithdrawal_leavesZeroBalanceAndPersistsTransaction() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account account = createAccount(session.token(), AccountType.CHECKING);
        setBalance(account, "100.00");

        JsonNode response = responseOf(mockMvc.perform(post(transactionUrl(account, "withdraw"))
                        .header("Authorization", bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new WithdrawalRequest(new BigDecimal("100.00")))))
                .andExpect(status().isOk())
                .andReturn());

        Account updated = accountRepository.findById(account.getAccountId()).orElseThrow();
        Transaction saved = transactionRepository.findById(response.get("transactionId").asLong()).orElseThrow();
        assertEquals(0, updated.getBalance().compareTo(BigDecimal.ZERO));
        assertEquals(0, saved.getBalanceAfter().compareTo(BigDecimal.ZERO));
    }

    @Test
    void insufficientFunds_returnsBadRequestWithoutChangingDatabase() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account account = createAccount(session.token(), AccountType.CHECKING);
        setBalance(account, "100.00");

        mockMvc.perform(post(transactionUrl(account, "withdraw"))
                        .header("Authorization", bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new WithdrawalRequest(new BigDecimal("150.00")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Insufficient funds"));

        assertEquals(0, accountRepository.findById(account.getAccountId()).orElseThrow()
                .getBalance().compareTo(new BigDecimal("100.00")));
        List<Transaction> transactions = transactionRepository
                .findByAccount_AccountIdOrderByCreatedAtDesc(account.getAccountId());
        assertEquals(0, transactions.size());
    }

    @Test
    void depositExceedingMaximumBalance_returnsBadRequestWithoutChangingDatabase() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account account = createAccount(session.token(), AccountType.CHECKING);
        setBalance(account, "99999999999999999.00");

        mockMvc.perform(post(transactionUrl(account, "deposit"))
                        .header("Authorization", bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new DepositRequest(new BigDecimal("1.01")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Deposit would exceed the maximum allowed account balance"));

        assertEquals(0, accountRepository.findById(account.getAccountId()).orElseThrow()
                .getBalance().compareTo(new BigDecimal("99999999999999999.00")));
        List<Transaction> transactions = transactionRepository
                .findByAccount_AccountIdOrderByCreatedAtDesc(account.getAccountId());
        assertEquals(0, transactions.size());
    }

    @Test
    void transfer_betweenDifferentCustomers_updatesBothAccountsAndPersistsLinkedTransactions() throws Exception {
        CustomerSession sourceSession = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        CustomerSession destinationSession = createCustomerAndLogin("bob@example.com", "+1234567891", "1234567891");
        Account source = createAccount(sourceSession.token(), AccountType.CHECKING);
        Account destination = createAccount(destinationSession.token(), AccountType.SAVINGS);
        setBalance(source, "200.00");
        setBalance(destination, "50.00");

        JsonNode response = responseOf(mockMvc.perform(post(transactionUrl(source, "transfer"))
                        .header("Authorization", bearer(sourceSession.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TransferRequest(destination.getAccountId(), new BigDecimal("75.25")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferReference", not(blankOrNullString())))
                .andExpect(jsonPath("$.sourceTransaction.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.sourceTransaction.amount").value(75.25))
                .andExpect(jsonPath("$.sourceTransaction.balanceAfter").value(124.75))
                .andExpect(jsonPath("$.destinationTransaction.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.destinationTransaction.amount").value(75.25))
                .andExpect(jsonPath("$.destinationTransaction.balanceAfter").value(125.25))
                .andReturn());

        Account updatedSource = accountRepository.findById(source.getAccountId()).orElseThrow();
        Account updatedDestination = accountRepository.findById(destination.getAccountId()).orElseThrow();
        List<Transaction> sourceTransactions = transactionsFor(source);
        List<Transaction> destinationTransactions = transactionsFor(destination);

        assertEquals(0, updatedSource.getBalance().compareTo(new BigDecimal("124.75")));
        assertEquals(0, updatedDestination.getBalance().compareTo(new BigDecimal("125.25")));
        assertEquals(sourceSession.customer().getCustomerId(), updatedSource.getCustomer().getCustomerId());
        assertEquals(destinationSession.customer().getCustomerId(), updatedDestination.getCustomer().getCustomerId());
        assertEquals(1, sourceTransactions.size());
        assertEquals(1, destinationTransactions.size());

        Transaction sourceTransaction = sourceTransactions.get(0);
        Transaction destinationTransaction = destinationTransactions.get(0);
        assertEquals(TransactionType.WITHDRAWAL, sourceTransaction.getType());
        assertEquals(TransactionType.DEPOSIT, destinationTransaction.getType());
        assertEquals(0, sourceTransaction.getAmount().compareTo(new BigDecimal("75.25")));
        assertEquals(0, destinationTransaction.getAmount().compareTo(new BigDecimal("75.25")));
        assertEquals(0, sourceTransaction.getBalanceAfter().compareTo(updatedSource.getBalance()));
        assertEquals(0, destinationTransaction.getBalanceAfter().compareTo(updatedDestination.getBalance()));
        assertEquals(sourceTransaction.getTransferReference(), destinationTransaction.getTransferReference());
        assertEquals(response.get("transferReference").asText(), sourceTransaction.getTransferReference());
        assertNotNull(sourceTransaction.getReference());
        assertNotNull(destinationTransaction.getReference());
        assertNotEquals(sourceTransaction.getReference(), destinationTransaction.getReference());
    }

    @Test
    void transfer_whenEntireSourceBalanceIsTransferred_leavesSourceAtZero() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account source = createAccount(session.token(), AccountType.CHECKING);
        Account destination = createAccount(session.token(), AccountType.SAVINGS);
        setBalance(source, "100.00");

        mockMvc.perform(post(transactionUrl(source, "transfer"))
                        .header("Authorization", bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TransferRequest(destination.getAccountId(), new BigDecimal("100.00")))))
                .andExpect(status().isOk());

        assertEquals(0, accountRepository.findById(source.getAccountId()).orElseThrow()
                .getBalance().compareTo(BigDecimal.ZERO));
        assertEquals(0, accountRepository.findById(destination.getAccountId()).orElseThrow()
                .getBalance().compareTo(new BigDecimal("100.00")));
        assertEquals(1, transactionsFor(source).size());
        assertEquals(1, transactionsFor(destination).size());
    }

    @Test
    void transfer_whenInsufficientFunds_returnsBadRequestWithoutChangingDatabase() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account source = createAccount(session.token(), AccountType.CHECKING);
        Account destination = createAccount(session.token(), AccountType.SAVINGS);
        setBalance(source, "100.00");
        setBalance(destination, "25.00");

        mockMvc.perform(post(transactionUrl(source, "transfer"))
                        .header("Authorization", bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TransferRequest(destination.getAccountId(), new BigDecimal("150.00")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Insufficient funds"));

        assertEquals(0, accountRepository.findById(source.getAccountId()).orElseThrow()
                .getBalance().compareTo(new BigDecimal("100.00")));
        assertEquals(0, accountRepository.findById(destination.getAccountId()).orElseThrow()
                .getBalance().compareTo(new BigDecimal("25.00")));
        assertEquals(0, transactionsFor(source).size());
        assertEquals(0, transactionsFor(destination).size());
    }

    @Test
    void transfer_whenAccountsAreMissing_returnsNotFoundWithoutPersistence() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account source = createAccount(session.token(), AccountType.CHECKING);
        setBalance(source, "100.00");

        mockMvc.perform(post("/api/accounts/99999/transactions/transfer")
                        .header("Authorization", bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TransferRequest(source.getAccountId(), new BigDecimal("10.00")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));

        mockMvc.perform(post(transactionUrl(source, "transfer"))
                        .header("Authorization", bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TransferRequest(99999L, new BigDecimal("10.00")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));

        assertEquals(0, accountRepository.findById(source.getAccountId()).orElseThrow()
                .getBalance().compareTo(new BigDecimal("100.00")));
        assertEquals(0, transactionRepository.count());
    }

    @Test
    void transfer_whenSourceAndDestinationAreTheSame_returnsBadRequestWithoutPersistence() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account account = createAccount(session.token(), AccountType.CHECKING);
        setBalance(account, "100.00");

        mockMvc.perform(post(transactionUrl(account, "transfer"))
                        .header("Authorization", bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TransferRequest(account.getAccountId(), new BigDecimal("10.00")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Source and destination accounts must be different"));

        assertEquals(0, accountRepository.findById(account.getAccountId()).orElseThrow()
                .getBalance().compareTo(new BigDecimal("100.00")));
        assertEquals(0, transactionRepository.count());
    }

    @Test
    void transfer_whenSourceIsNotOwnedByAuthenticatedCustomer_returnsForbidden() throws Exception {
        CustomerSession alice = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        CustomerSession bob = createCustomerAndLogin("bob@example.com", "+1234567891", "1234567891");
        Account source = createAccount(alice.token(), AccountType.CHECKING);
        Account destination = createAccount(bob.token(), AccountType.SAVINGS);
        setBalance(source, "100.00");
        setBalance(destination, "25.00");

        mockMvc.perform(post(transactionUrl(source, "transfer"))
                        .header("Authorization", bearer(bob.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TransferRequest(destination.getAccountId(), new BigDecimal("10.00")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Customer access denied"));

        assertEquals(0, accountRepository.findById(source.getAccountId()).orElseThrow()
                .getBalance().compareTo(new BigDecimal("100.00")));
        assertEquals(0, accountRepository.findById(destination.getAccountId()).orElseThrow()
                .getBalance().compareTo(new BigDecimal("25.00")));
        assertEquals(0, transactionRepository.count());
    }

    @Test
    void transfer_whenEitherAccountIsInactive_returnsBadRequestWithoutChanges() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account suspendedSource = createAccount(session.token(), AccountType.CHECKING);
        Account closedSource = createAccount(session.token(), AccountType.CHECKING);
        Account activeSourceForSuspendedDestination = createAccount(session.token(), AccountType.CHECKING);
        Account activeSourceForClosedDestination = createAccount(session.token(), AccountType.CHECKING);
        Account suspendedDestination = createAccount(session.token(), AccountType.SAVINGS);
        Account closedDestination = createAccount(session.token(), AccountType.SAVINGS);
        setBalance(suspendedSource, "100.00");
        setBalance(activeSourceForSuspendedDestination, "100.00");
        setBalance(activeSourceForClosedDestination, "100.00");
        setBalance(suspendedDestination, "25.00");
        changeAccountStatus(suspendedSource, "suspend", session.token());
        changeAccountStatus(closedSource, "close", session.token());
        changeAccountStatus(suspendedDestination, "suspend", session.token());
        changeAccountStatus(closedDestination, "close", session.token());

        assertInactiveTransferRejected(session.token(), suspendedSource, activeSourceForSuspendedDestination);
        assertInactiveTransferRejected(session.token(), closedSource, activeSourceForSuspendedDestination);
        assertInactiveTransferRejected(session.token(), activeSourceForSuspendedDestination, suspendedDestination);
        assertInactiveTransferRejected(session.token(), activeSourceForClosedDestination, closedDestination);

        assertEquals(0, transactionRepository.count());
        assertEquals(AccountStatus.SUSPENDED, accountRepository.findById(suspendedSource.getAccountId()).orElseThrow().getStatus());
        assertEquals(AccountStatus.CLOSED, accountRepository.findById(closedSource.getAccountId()).orElseThrow().getStatus());
        assertEquals(0, accountRepository.findById(activeSourceForSuspendedDestination.getAccountId()).orElseThrow()
                .getBalance().compareTo(new BigDecimal("100.00")));
        assertEquals(0, accountRepository.findById(activeSourceForClosedDestination.getAccountId()).orElseThrow()
                .getBalance().compareTo(new BigDecimal("100.00")));
        assertEquals(AccountStatus.SUSPENDED, accountRepository.findById(suspendedDestination.getAccountId()).orElseThrow().getStatus());
        assertEquals(AccountStatus.CLOSED, accountRepository.findById(closedDestination.getAccountId()).orElseThrow().getStatus());
    }

    private void assertInactiveTransferRejected(String token, Account source, Account destination) throws Exception {
        mockMvc.perform(post(transactionUrl(source, "transfer"))
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TransferRequest(destination.getAccountId(), new BigDecimal("10.00")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account must be active for this transaction"));
    }

    @Test
    void transfer_whenDestinationBalanceWouldOverflow_returnsBadRequestWithoutChanges() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account source = createAccount(session.token(), AccountType.CHECKING);
        Account destination = createAccount(session.token(), AccountType.SAVINGS);
        setBalance(source, "100.00");
        setBalance(destination, "99999999999999999.99");

        mockMvc.perform(post(transactionUrl(source, "transfer"))
                        .header("Authorization", bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TransferRequest(destination.getAccountId(), new BigDecimal("0.01")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Transfer would exceed the maximum allowed destination account balance"));

        assertEquals(0, accountRepository.findById(source.getAccountId()).orElseThrow()
                .getBalance().compareTo(new BigDecimal("100.00")));
        assertEquals(0, accountRepository.findById(destination.getAccountId()).orElseThrow()
                .getBalance().compareTo(new BigDecimal("99999999999999999.99")));
        assertEquals(0, transactionRepository.count());
    }

        @Test
        void transfer_whenSourceAccountIdIsInvalid_returnsBadRequestWithoutDatabaseChanges() throws Exception {
                CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
                Account destination = createAccount(session.token(), AccountType.SAVINGS);
                setBalance(destination, "25.00");

                mockMvc.perform(post("/api/accounts/0/transactions/transfer")
                                                .header("Authorization", bearer(session.token()))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(json(new TransferRequest(destination.getAccountId(), new BigDecimal("10.00")))))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value("Account ID must be positive"));

                assertEquals(0, accountRepository.findById(destination.getAccountId()).orElseThrow()
                                .getBalance().compareTo(new BigDecimal("25.00")));
                assertEquals(0, transactionRepository.count());
        }

    @ParameterizedTest
    @MethodSource("invalidTransferRequests")
    void transfer_whenRequestIsInvalid_returnsBadRequestWithoutDatabaseChanges(
            TransferRequest request, String message) throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account source = createAccount(session.token(), AccountType.CHECKING);
        setBalance(source, "100.00");

        mockMvc.perform(post(transactionUrl(source, "transfer"))
                        .header("Authorization", bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(message));

        assertEquals(0, accountRepository.findById(source.getAccountId()).orElseThrow()
                .getBalance().compareTo(new BigDecimal("100.00")));
        assertEquals(0, transactionRepository.count());
    }

        @Test
        void oppositeDirectionTransfers_concurrentlyCompleteWithoutDeadlock() throws Exception {
                CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
                Account first = createAccount(session.token(), AccountType.CHECKING);
                Account second = createAccount(session.token(), AccountType.SAVINGS);
                setBalance(first, "100.00");
                setBalance(second, "100.00");

                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                ExecutorService executor = Executors.newFixedThreadPool(2);

                try {
                        Future<Integer> firstToSecond = executor.submit(() -> {
                                ready.countDown();
                                assertTrue(start.await(5, TimeUnit.SECONDS));
                                return mockMvc.perform(post(transactionUrl(first, "transfer"))
                                                                .header("Authorization", bearer(session.token()))
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .content(json(new TransferRequest(second.getAccountId(), new BigDecimal("40.00")))))
                                                .andReturn().getResponse().getStatus();
                        });
                        Future<Integer> secondToFirst = executor.submit(() -> {
                                ready.countDown();
                                assertTrue(start.await(5, TimeUnit.SECONDS));
                                return mockMvc.perform(post(transactionUrl(second, "transfer"))
                                                                .header("Authorization", bearer(session.token()))
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .content(json(new TransferRequest(first.getAccountId(), new BigDecimal("40.00")))))
                                                .andReturn().getResponse().getStatus();
                        });

                        assertTrue(ready.await(5, TimeUnit.SECONDS), "Both transfers must be ready before release");
                        start.countDown();
                        assertEquals(200, firstToSecond.get(10, TimeUnit.SECONDS));
                        assertEquals(200, secondToFirst.get(10, TimeUnit.SECONDS));
                } finally {
                        executor.shutdownNow();
                        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Transfer workers must terminate");
                }

                assertEquals(0, accountRepository.findById(first.getAccountId()).orElseThrow()
                                .getBalance().compareTo(new BigDecimal("100.00")));
                assertEquals(0, accountRepository.findById(second.getAccountId()).orElseThrow()
                                .getBalance().compareTo(new BigDecimal("100.00")));
                List<Transaction> firstTransactions = transactionsFor(first);
                List<Transaction> secondTransactions = transactionsFor(second);
                assertEquals(2, firstTransactions.size());
                assertEquals(2, secondTransactions.size());
                assertEquals(1, firstTransactions.stream().filter(transaction -> transaction.getType() == TransactionType.WITHDRAWAL).count());
                assertEquals(1, firstTransactions.stream().filter(transaction -> transaction.getType() == TransactionType.DEPOSIT).count());
                assertEquals(1, secondTransactions.stream().filter(transaction -> transaction.getType() == TransactionType.WITHDRAWAL).count());
                assertEquals(1, secondTransactions.stream().filter(transaction -> transaction.getType() == TransactionType.DEPOSIT).count());
        }

    private static Stream<Arguments> invalidTransferRequests() {
        return Stream.of(
                Arguments.of(new TransferRequest(2L, null), "Amount is required"),
                Arguments.of(new TransferRequest(2L, BigDecimal.ZERO), "Amount must be greater than zero"),
                Arguments.of(new TransferRequest(2L, new BigDecimal("-1.00")), "Amount must be greater than zero"),
                Arguments.of(new TransferRequest(2L, new BigDecimal("10.001")),
                        "Amount must have at most 17 integer digits and 2 decimal places"),
                Arguments.of(new TransferRequest(2L, new BigDecimal("123456789012345678.90")),
                        "Amount must have at most 17 integer digits and 2 decimal places"),
                Arguments.of(new TransferRequest(null, new BigDecimal("10.00")), "Destination account ID is required"),
                Arguments.of(new TransferRequest(0L, new BigDecimal("10.00")), "Destination account ID must be positive"),
                Arguments.of(new TransferRequest(-1L, new BigDecimal("10.00")), "Destination account ID must be positive"));
    }

    @Test
        // Protects against both withdrawals reading 100.00 and overwriting each other's update.
    void concurrentWithdrawals_areSerializedByPessimisticAccountLock() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account account = createAccount(session.token(), AccountType.CHECKING);
        setBalance(account, "100.00");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        BigDecimal firstAmount = new BigDecimal("70.00");
        BigDecimal secondAmount = new BigDecimal("50.00");

        try {
            Future<WithdrawalAttempt> first = executor.submit(withdrawalTask(
                    account.getAccountId(), session.customer().getEmail(), firstAmount, ready, start));
            Future<WithdrawalAttempt> second = executor.submit(withdrawalTask(
                    account.getAccountId(), session.customer().getEmail(), secondAmount, ready, start));

            assertTrue(ready.await(5, TimeUnit.SECONDS), "Both withdrawals must be ready before release");
            start.countDown();

            WithdrawalAttempt firstResult = getAttempt(first);
            WithdrawalAttempt secondResult = getAttempt(second);

            assertTrue(firstResult.succeeded() ^ secondResult.succeeded(),
                    "Exactly one withdrawal should succeed");
            WithdrawalAttempt successfulAttempt = firstResult.succeeded() ? firstResult : secondResult;
            WithdrawalAttempt failedAttempt = firstResult.succeeded() ? secondResult : firstResult;
            assertTrue(failedAttempt.failureMessage().contains("Insufficient funds"));

            Account updated = accountRepository.findById(account.getAccountId()).orElseThrow();
            List<Transaction> transactions = transactionRepository
                    .findByAccount_AccountIdOrderByCreatedAtDesc(account.getAccountId());

            BigDecimal finalBalance = updated.getBalance();
            assertTrue(finalBalance.compareTo(BigDecimal.ZERO) >= 0);
            assertTrue(finalBalance.compareTo(new BigDecimal("30.00")) == 0
                    || finalBalance.compareTo(new BigDecimal("50.00")) == 0);
            assertEquals(1, transactions.size());
            Transaction saved = transactions.get(0);
            assertEquals(TransactionType.WITHDRAWAL, saved.getType());
            assertEquals(0, saved.getAmount().compareTo(successfulAttempt.amount()));
            assertEquals(0, saved.getBalanceAfter().compareTo(finalBalance));
            assertTrue(saved.getAmount().compareTo(failedAttempt.amount()) != 0,
                    "The failed withdrawal must not create a transaction");
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS),
                    "Withdrawal workers must terminate");
        }
    }

    @ParameterizedTest
    @MethodSource("invalidAmounts")
    void amountValidation_rejectsInvalidAmounts(String operation, String amount, String message) throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account account = createAccount(session.token(), AccountType.CHECKING);

        Object request = operation.equals("deposit")
                ? new DepositRequest(amount == null ? null : new BigDecimal(amount))
                : new WithdrawalRequest(amount == null ? null : new BigDecimal(amount));

        mockMvc.perform(post(transactionUrl(account, operation))
                        .header("Authorization", bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(message));

        assertEquals(0, transactionRepository.count());
        assertEquals(0, accountRepository.findById(account.getAccountId()).orElseThrow()
                .getBalance().compareTo(BigDecimal.ZERO));
    }

    private static Stream<Arguments> invalidAmounts() {
        return Stream.of(
                Arguments.of("deposit", null, "Amount is required"),
                Arguments.of("withdraw", "0", "Amount must be greater than zero"),
                Arguments.of("deposit", "-1.00", "Amount must be greater than zero"),
                Arguments.of("withdraw", "10.001", "Amount must have at most 17 integer digits and 2 decimal places"),
                Arguments.of("deposit", "123456789012345678.90", "Amount must have at most 17 integer digits and 2 decimal places"));
    }

        private Callable<WithdrawalAttempt> withdrawalTask(Long accountId, String email, BigDecimal amount,
                                                                                                           CountDownLatch ready, CountDownLatch start) {
                return () -> {
                        ready.countDown();
                        if (!start.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Withdrawal start timed out");
                        }

                        try {
                                transactionService.withdraw(accountId,
                                                new WithdrawalRequest(amount), email);
                                return new WithdrawalAttempt(amount, true, null);
                        } catch (RuntimeException exception) {
                                return new WithdrawalAttempt(amount, false, exception.getMessage());
                        }
                };
        }

        private WithdrawalAttempt getAttempt(Future<WithdrawalAttempt> future) throws Exception {
                try {
                        return future.get(10, TimeUnit.SECONDS);
                } catch (ExecutionException exception) {
                        throw new AssertionError("Concurrent withdrawal worker failed", exception.getCause());
                }
        }

        private record WithdrawalAttempt(BigDecimal amount, boolean succeeded, String failureMessage) {
        }

    @Test
    void accountIdValidation_andMissingAccount_returnExpectedErrors() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        DepositRequest request = new DepositRequest(new BigDecimal("10.00"));

        mockMvc.perform(post("/api/accounts/0/transactions/deposit")
                        .header("Authorization", bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account ID must be positive"));
        mockMvc.perform(post("/api/accounts/not-a-number/transactions/deposit")
                        .header("Authorization", bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ID must be a valid number"));
        mockMvc.perform(post("/api/accounts/99999/transactions/deposit")
                        .header("Authorization", bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));
        mockMvc.perform(get("/api/accounts/99999/transactions")
                        .header("Authorization", bearer(session.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));

        assertEquals(0, transactionRepository.count());
    }

    @Test
    void suspendedAndClosedAccounts_rejectTransactionsWithoutPersistence() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account suspended = createAccount(session.token(), AccountType.CHECKING);
        Account closed = createAccount(session.token(), AccountType.SAVINGS);
        changeAccountStatus(suspended, "suspend", session.token());
        changeAccountStatus(closed, "close", session.token());

        for (Account account : List.of(suspended, closed)) {
            mockMvc.perform(post(transactionUrl(account, "deposit"))
                            .header("Authorization", bearer(session.token()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new DepositRequest(new BigDecimal("10.00")))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Account must be active for this transaction"));
        }

        assertEquals(0, transactionRepository.count());
        assertEquals(AccountStatus.SUSPENDED, accountRepository.findById(suspended.getAccountId()).orElseThrow().getStatus());
        assertEquals(AccountStatus.CLOSED, accountRepository.findById(closed.getAccountId()).orElseThrow().getStatus());
    }

    @Test
    void ownershipAndAuthentication_areEnforcedForTransactionEndpoints() throws Exception {
        CustomerSession alice = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        CustomerSession bob = createCustomerAndLogin("bob@example.com", "+1234567891", "1234567891");
        Account bobAccount = createAccount(bob.token(), AccountType.CHECKING);
        DepositRequest request = new DepositRequest(new BigDecimal("10.00"));

        mockMvc.perform(post(transactionUrl(bobAccount, "deposit"))
                        .header("Authorization", bearer(alice.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Customer access denied"));
        mockMvc.perform(get(transactionUrl(bobAccount, null))
                        .header("Authorization", bearer(alice.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Customer access denied"));

        mockMvc.perform(post(transactionUrl(bobAccount, "deposit"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(transactionUrl(bobAccount, "withdraw"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new WithdrawalRequest(new BigDecimal("1.00")))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(transactionUrl(bobAccount, null)))
                .andExpect(status().isUnauthorized());

        assertEquals(0, transactionRepository.count());
        assertEquals(0, accountRepository.findById(bobAccount.getAccountId()).orElseThrow()
                .getBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void transactionHistory_returnsEmptyThenNewestFirstWithCompleteFields() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account account = createAccount(session.token(), AccountType.CHECKING);

        mockMvc.perform(get(transactionUrl(account, null))
                        .header("Authorization", bearer(session.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        deposit(account, session.token(), "100.00");
        deposit(account, session.token(), "50.00");
        withdraw(account, session.token(), "25.00");

        MvcResult result = mockMvc.perform(get(transactionUrl(account, null))
                        .header("Authorization", bearer(session.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$[0].transactionId").isNumber())
                .andExpect(jsonPath("$[0].accountNumber").value(account.getAccountNumber()))
                .andExpect(jsonPath("$[0].type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$[0].amount").value(25.00))
                .andExpect(jsonPath("$[0].balanceAfter").value(125.00))
                .andExpect(jsonPath("$[0].reference", not(blankOrNullString())))
                .andExpect(jsonPath("$[1].type").value("DEPOSIT"))
                .andExpect(jsonPath("$[1].amount").value(50.00))
                .andExpect(jsonPath("$[1].balanceAfter").value(150.00))
                .andExpect(jsonPath("$[2].type").value("DEPOSIT"))
                .andExpect(jsonPath("$[2].amount").value(100.00))
                .andReturn();

        JsonNode history = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNotNull(history.get(0).get("createdAt"));
        assertNotNull(history.get(1).get("createdAt"));
        assertNotNull(history.get(2).get("createdAt"));
        assertEquals(3, transactionRepository.findByAccount_AccountIdOrderByCreatedAtDesc(account.getAccountId()).size());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String transactionUrl(Account account, String operation) {
        String url = "/api/accounts/" + account.getAccountId() + "/transactions";
        return operation == null ? url : url + "/" + operation;
    }

    private CustomerSession createCustomerAndLogin(String email, String phone, String nationalId) throws Exception {
        CustomerRegistrationRequest request = CustomerRegistrationRequest.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email(email)
                .phone(phone)
                .password(PASSWORD)
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId(nationalId)
                .build();

        MvcResult registration = mockMvc.perform(post("/api/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andReturn();
        long customerId = objectMapper.readTree(registration.getResponse().getContentAsString())
                .get("customerId").asLong();

        MvcResult login = mockMvc.perform(post("/api/customers/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();
        Customer customer = customerRepository.findById(customerId).orElseThrow();
        return new CustomerSession(customer, token);
    }

    private Account createAccount(String token, AccountType type) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AccountCreateRequest(type))))
                .andExpect(status().isCreated())
                .andReturn();
        long accountId = objectMapper.readTree(result.getResponse().getContentAsString()).get("accountId").asLong();
        return accountRepository.findById(accountId).orElseThrow();
    }

    private Transaction deposit(Account account, String token, String amount) throws Exception {
        JsonNode response = responseOf(mockMvc.perform(post(transactionUrl(account, "deposit"))
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new DepositRequest(new BigDecimal(amount)))))
                .andExpect(status().isOk())
                .andReturn());
        return transactionRepository.findById(response.get("transactionId").asLong()).orElseThrow();
    }

    private Transaction withdraw(Account account, String token, String amount) throws Exception {
        JsonNode response = responseOf(mockMvc.perform(post(transactionUrl(account, "withdraw"))
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new WithdrawalRequest(new BigDecimal(amount)))))
                .andExpect(status().isOk())
                .andReturn());
        return transactionRepository.findById(response.get("transactionId").asLong()).orElseThrow();
    }

        private List<Transaction> transactionsFor(Account account) {
                return transactionRepository.findByAccount_AccountIdOrderByCreatedAtDesc(account.getAccountId());
        }

    private JsonNode responseOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void setBalance(Account account, String balance) {
        account.setBalance(new BigDecimal(balance));
        accountRepository.saveAndFlush(account);
    }

    private void changeAccountStatus(Account account, String operation, String token) throws Exception {
        mockMvc.perform(patch("/api/accounts/" + account.getAccountId() + "/" + operation)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private record CustomerSession(Customer customer, String token) {
    }
}

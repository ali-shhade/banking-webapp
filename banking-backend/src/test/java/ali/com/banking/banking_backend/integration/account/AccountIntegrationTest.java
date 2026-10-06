package ali.com.banking.banking_backend.integration.account;

import ali.com.banking.banking_backend.account.dto.AccountCreateRequest;
import ali.com.banking.banking_backend.account.entity.Account;
import ali.com.banking.banking_backend.account.entity.AccountStatus;
import ali.com.banking.banking_backend.account.entity.AccountType;
import ali.com.banking.banking_backend.account.repository.AccountRepository;
import ali.com.banking.banking_backend.customer.dto.CustomerRegistrationRequest;
import ali.com.banking.banking_backend.customer.entity.Customer;
import ali.com.banking.banking_backend.customer.repository.CustomerRepository;
import ali.com.banking.banking_backend.integration.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AccountIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "StrongPass123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @ParameterizedTest
    @EnumSource(AccountType.class)
    void createAccount_persistsExpectedDefaultsForEachAccountType(AccountType accountType) throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");

        MvcResult result = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + session.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AccountCreateRequest(accountType))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").isNumber())
                .andExpect(jsonPath("$.accountNumber", not(blankOrNullString())))
                .andExpect(jsonPath("$.accountType").value(accountType.name()))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.status").value(AccountStatus.ACTIVE.name()))
                .andExpect(jsonPath("$.createdAt", not(blankOrNullString())))
                .andExpect(jsonPath("$.customer").doesNotExist())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        Account saved = accountRepository.findById(response.get("accountId").asLong()).orElseThrow();
        assertEquals(session.customer().getCustomerId(), saved.getCustomer().getCustomerId());
        assertEquals(accountType, saved.getAccountType());
        assertEquals(0, saved.getBalance().compareTo(BigDecimal.ZERO));
        assertEquals(AccountStatus.ACTIVE, saved.getStatus());
        assertNotNull(saved.getCreatedAt());
        assertEquals(saved.getAccountNumber(), response.get("accountNumber").asText());
    }

    @Test
    void createAccount_withMissingType_returnsBadRequestAndDoesNotPersist() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");

        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + session.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Account type is required"));

        assertEquals(0, accountRepository.count());
    }

    @Test
    void createAccount_withMalformedBody_returnsBadRequestAndDoesNotPersist() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");

        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + session.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\": "))
                .andExpect(status().isBadRequest());

        assertEquals(0, accountRepository.count());
    }

    @Test
    void accountEndpoints_withoutJwt_returnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AccountCreateRequest(AccountType.CHECKING))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/accounts/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/accounts/1/suspend")).andExpect(status().isUnauthorized());
    }

    @Test
    void getAccountById_returnsPersistedOwnedAccount() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account account = createAccount(session.token(), AccountType.SAVINGS);

        mockMvc.perform(get("/api/accounts/{accountId}", account.getAccountId())
                        .header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.getAccountId()))
                .andExpect(jsonPath("$.accountNumber").value(account.getAccountNumber()))
                .andExpect(jsonPath("$.accountType").value("SAVINGS"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void getAccountById_handlesMissingAndInvalidIds() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");

        mockMvc.perform(get("/api/accounts/99999")
                        .header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));

        mockMvc.perform(get("/api/accounts/0")
                        .header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account ID must be positive"));

        mockMvc.perform(get("/api/accounts/not-a-number")
                        .header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ID must be a valid number"));
    }

    @Test
    void getMyAccounts_returnsOnlyAuthenticatedCustomersAccounts() throws Exception {
        CustomerSession alice = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        CustomerSession bob = createCustomerAndLogin("bob@example.com", "+1234567891", "1234567891");
        Account aliceChecking = createAccount(alice.token(), AccountType.CHECKING);
        Account aliceSavings = createAccount(alice.token(), AccountType.SAVINGS);
        Account bobAccount = createAccount(bob.token(), AccountType.CHECKING);

        mockMvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer " + alice.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.accountId == " + aliceChecking.getAccountId() + ")]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.accountId == " + aliceSavings.getAccountId() + ")]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.accountId == " + bobAccount.getAccountId() + ")]").isEmpty())
                .andExpect(jsonPath("$[?(@.status == 'ACTIVE')]").isNotEmpty());
    }

    @Test
    void customerCannotReadOrChangeAnotherCustomersAccount() throws Exception {
        CustomerSession alice = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        CustomerSession bob = createCustomerAndLogin("bob@example.com", "+1234567891", "1234567891");
        Account aliceAccount = createAccount(alice.token(), AccountType.CHECKING);

        mockMvc.perform(get("/api/accounts/{accountId}", aliceAccount.getAccountId())
                        .header("Authorization", "Bearer " + bob.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Customer access denied"));

        mockMvc.perform(patch("/api/accounts/{accountId}/suspend", aliceAccount.getAccountId())
                        .header("Authorization", "Bearer " + bob.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Customer access denied"));

        Account unchanged = accountRepository.findById(aliceAccount.getAccountId()).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, unchanged.getStatus());
        assertEquals(alice.customer().getCustomerId(), unchanged.getCustomer().getCustomerId());
    }

    @Test
    void suspendAccount_changesActiveAccountToSuspendedAndRejectsRepeat() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account account = createAccount(session.token(), AccountType.CHECKING);

        mockMvc.perform(patch("/api/accounts/{accountId}/suspend", account.getAccountId())
                        .header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isNoContent());
        assertEquals(AccountStatus.SUSPENDED, accountRepository.findById(account.getAccountId()).orElseThrow().getStatus());

        mockMvc.perform(patch("/api/accounts/{accountId}/suspend", account.getAccountId())
                        .header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account is already suspended"));
    }

    @Test
    void activateAccount_changesSuspendedAccountToActiveAndRejectsRepeat() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account account = createAccount(session.token(), AccountType.CHECKING);
        updateStatus(account.getAccountId(), "suspend", session.token(), status().isNoContent());

        mockMvc.perform(patch("/api/accounts/{accountId}/activate", account.getAccountId())
                        .header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isNoContent());
        assertEquals(AccountStatus.ACTIVE, accountRepository.findById(account.getAccountId()).orElseThrow().getStatus());

        mockMvc.perform(patch("/api/accounts/{accountId}/activate", account.getAccountId())
                        .header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account is already active"));
    }

    @Test
    void closeAccount_changesZeroBalanceAccountToClosedAndRejectsRepeat() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account account = createAccount(session.token(), AccountType.CHECKING);

        mockMvc.perform(patch("/api/accounts/{accountId}/close", account.getAccountId())
                        .header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isNoContent());
        assertEquals(AccountStatus.CLOSED, accountRepository.findById(account.getAccountId()).orElseThrow().getStatus());

        mockMvc.perform(patch("/api/accounts/{accountId}/close", account.getAccountId())
                        .header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account is already closed"));
    }

    @Test
    void closeAccount_withNonZeroBalance_returnsBadRequestAndKeepsAccountOpen() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account account = createAccount(session.token(), AccountType.CHECKING);
        account.setBalance(new BigDecimal("10.00"));
        accountRepository.saveAndFlush(account);

        mockMvc.perform(patch("/api/accounts/{accountId}/close", account.getAccountId())
                        .header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account balance must be zero before closing"));

        Account unchanged = accountRepository.findById(account.getAccountId()).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, unchanged.getStatus());
        assertEquals(new BigDecimal("10.00"), unchanged.getBalance());
    }

    @Test
    void closedAccount_cannotBeSuspendedOrActivated() throws Exception {
        CustomerSession session = createCustomerAndLogin("alice@example.com", "+1234567890", "1234567890");
        Account account = createAccount(session.token(), AccountType.CHECKING);
        updateStatus(account.getAccountId(), "close", session.token(), status().isNoContent());

        mockMvc.perform(patch("/api/accounts/{accountId}/suspend", account.getAccountId())
                        .header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Closed account cannot be suspended"));
        mockMvc.perform(patch("/api/accounts/{accountId}/activate", account.getAccountId())
                        .header("Authorization", "Bearer " + session.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Closed account cannot be activated"));

        assertEquals(AccountStatus.CLOSED, accountRepository.findById(account.getAccountId()).orElseThrow().getStatus());
    }

    private Account createAccount(String token, AccountType accountType) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AccountCreateRequest(accountType))))
                .andExpect(status().isCreated())
                .andReturn();
        long accountId = objectMapper.readTree(result.getResponse().getContentAsString()).get("accountId").asLong();
        return accountRepository.findById(accountId).orElseThrow();
    }

    private void updateStatus(Long accountId, String operation, String token,
                              org.springframework.test.web.servlet.ResultMatcher expectedStatus) throws Exception {
        mockMvc.perform(patch("/api/accounts/{accountId}/" + operation, accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(expectedStatus);
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
                .andExpect(jsonPath("$.token", not(blankOrNullString())))
                .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();
        Customer customer = customerRepository.findById(customerId).orElseThrow();
        return new CustomerSession(customer, token);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private record CustomerSession(Customer customer, String token) {
    }
}

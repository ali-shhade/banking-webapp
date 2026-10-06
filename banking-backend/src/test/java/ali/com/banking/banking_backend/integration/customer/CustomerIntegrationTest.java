package ali.com.banking.banking_backend.integration.customer;

import ali.com.banking.banking_backend.customer.dto.CustomerRegistrationRequest;
import ali.com.banking.banking_backend.customer.dto.CustomerUpdateRequest;
import ali.com.banking.banking_backend.customer.entity.Customer;
import ali.com.banking.banking_backend.customer.repository.CustomerRepository;
import ali.com.banking.banking_backend.integration.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CustomerIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "StrongPass123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void registerCustomer_persistsCustomerAndDoesNotReturnPassword() throws Exception {
        CustomerRegistrationRequest request = registration("alice@example.com", "+1234567890", "1234567890");

        mockMvc.perform(post("/api/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").isNumber())
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.nationalId").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        Customer saved = customerRepository.findByEmail("alice@example.com").orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(saved.getCustomerId());
        org.junit.jupiter.api.Assertions.assertNotEquals(PASSWORD, saved.getPassword());
        org.junit.jupiter.api.Assertions.assertEquals(1, customerRepository.count());
    }

    @Test
    void registrationValidation_returnsActualValidationMessageAndDoesNotPersist() throws Exception {
        CustomerRegistrationRequest request = registration("bademail@gmail.com", "707654123", "9876543345678876");
        request.setFirstName("");
        request.setPassword("weakPass00rd@");

        mockMvc.perform(post("/api/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("First name is required"));

        org.junit.jupiter.api.Assertions.assertEquals(0, customerRepository.count());
    }

    @Test
    void registrationValidation_rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": "))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertEquals(0, customerRepository.count());
    }

    @ParameterizedTest
    @MethodSource("invalidRegistrationRequests")
    void registrationValidation_rejectsInvalidCustomerFields(
            CustomerRegistrationRequest request, String message) throws Exception {
        mockMvc.perform(post("/api/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(message));

        org.junit.jupiter.api.Assertions.assertEquals(0, customerRepository.count());
    }

    private static Stream<Arguments> invalidRegistrationRequests() {
        return Stream.of(
                Arguments.of(registrationRequest("not-an-email", "+1234567890", "1234567890"),
                        "Email should be a valid email address"),
                Arguments.of(registrationRequest("valid@example.com", "123", "1234567890"),
                        "Phone number must be valid"),
                Arguments.of(registrationRequest("valid@example.com", "+1234567890", "abc"),
                        "National ID must contain only digits"),
                Arguments.of(invalidPasswordRegistrationRequest(),
                        "Password must be between 8 and 64 characters"));
    }

    private static CustomerRegistrationRequest invalidPasswordRegistrationRequest() {
        CustomerRegistrationRequest request = registrationRequest(
                "valid@example.com", "+1234567890", "1234567890");
        request.setPassword("weak");
        return request;
    }

    @Test
    void duplicateEmailPhoneAndNationalId_returnConflict() throws Exception {
        register(registration("first@example.com", "+1234567890", "1234567890"));

        duplicateRegistration(registration("first@example.com", "+1234567891", "1234567891"), "Email already exists");
        duplicateRegistration(registration("second@example.com", "+1234567890", "1234567891"), "Phone number already exists");
        duplicateRegistration(registration("third@example.com", "+1234567892", "1234567890"), "National ID already exists");

        org.junit.jupiter.api.Assertions.assertEquals(1, customerRepository.count());
    }

    @Test
    void login_returnsJwtAndRejectsInvalidCredentials() throws Exception {
        register(registration("alice@example.com", "+1234567890", "1234567890"));

        String token = login("alice@example.com", PASSWORD);
        org.junit.jupiter.api.Assertions.assertFalse(token.isBlank());

        mockMvc.perform(post("/api/customers/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "alice@example.com", "password", "WrongPass123!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void authenticatedCustomer_canReadListAndOwnRecord() throws Exception {
        Customer customer = register(registration("alice@example.com", "+1234567890", "1234567890"));
        String token = login("alice@example.com", PASSWORD);

        mockMvc.perform(get("/api/customers/{customerId}", customer.getCustomerId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customer.getCustomerId()))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value("alice@example.com"))
                .andExpect(jsonPath("$[0].nationalId").doesNotExist());
    }

    @Test
    void protectedEndpoints_withoutJwt_returnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/customers/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/customers")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update("alice@example.com", "+1234567890", "1234567890"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/customers/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotAccessOrModifyAnotherCustomer() throws Exception {
        Customer alice = register(registration("alice@example.com", "+1234567890", "1234567890"));
        Customer bob = register(registration("bob@example.com", "+1234567891", "1234567891"));
        String aliceToken = login("alice@example.com", PASSWORD);

        mockMvc.perform(get("/api/customers/{customerId}", bob.getCustomerId())
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Customer access denied"));

        mockMvc.perform(put("/api/customers/{customerId}", bob.getCustomerId())
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update("changed@example.com", "+1234567892", "1234567892"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Customer access denied"));

        Customer unchanged = customerRepository.findById(bob.getCustomerId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("bob@example.com", unchanged.getEmail());
        org.junit.jupiter.api.Assertions.assertEquals(2, customerRepository.count());
    }

    @Test
    void updateCustomer_persistsChanges() throws Exception {
        Customer customer = register(registration("alice@example.com", "+1234567890", "1234567890"));
        String token = login("alice@example.com", PASSWORD);

        mockMvc.perform(put("/api/customers/{customerId}", customer.getCustomerId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update("alice.updated@example.com", "+1234567892", "1234567892"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice.updated@example.com"))
                .andExpect(jsonPath("$.address").value("456 Main St"))
                .andExpect(jsonPath("$.password").doesNotExist());

        Customer updated = customerRepository.findById(customer.getCustomerId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("alice.updated@example.com", updated.getEmail());
        org.junit.jupiter.api.Assertions.assertEquals("456 Main St", updated.getAddress());
    }

    @Test
    void invalidCustomerIds_returnBadRequest() throws Exception {
        register(registration("alice@example.com", "+1234567890", "1234567890"));
        String token = login("alice@example.com", PASSWORD);

        for (String id : new String[]{"0", "-1", "not-a-number"}) {
            mockMvc.perform(get("/api/customers/{customerId}", id)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void deleteCustomer_removesCustomerAndInvalidatesSubsequentLookup() throws Exception {
        Customer customer = register(registration("alice@example.com", "+1234567890", "1234567890"));
        String token = login("alice@example.com", PASSWORD);

        mockMvc.perform(delete("/api/customers/{customerId}", customer.getCustomerId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertFalse(customerRepository.existsById(customer.getCustomerId()));
        mockMvc.perform(get("/api/customers/{customerId}", customer.getCustomerId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private CustomerRegistrationRequest registration(String email, String phone, String nationalId) {
        CustomerRegistrationRequest request = registrationRequest(email, phone, nationalId);
        request.setPassword(PASSWORD);
        return request;
    }

    private static CustomerRegistrationRequest registrationRequest(String email, String phone, String nationalId) {
        return CustomerRegistrationRequest.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email(email)
                .phone(phone)
                .password("StrongPass123!")
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId(nationalId)
                .build();
    }

    private CustomerUpdateRequest update(String email, String phone, String nationalId) {
        return CustomerUpdateRequest.builder()
                .firstName("Updated")
                .lastName("Smith")
                .email(email)
                .phone(phone)
                .password(PASSWORD)
                .address("456 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId(nationalId)
                .build();
    }

    private Customer register(CustomerRegistrationRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return customerRepository.findById(response.get("customerId").asLong()).orElseThrow();
    }

    private void duplicateRegistration(CustomerRegistrationRequest request, String message) throws Exception {
        mockMvc.perform(post("/api/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(message));
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/customers/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(blankOrNullString())))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}

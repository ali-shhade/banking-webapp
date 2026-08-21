package ali.com.banking.banking_backend.controller;

import ali.com.banking.banking_backend.dto.CustomerRegistrationRequest;
import ali.com.banking.banking_backend.dto.CustomerResponse;
import ali.com.banking.banking_backend.dto.CustomerUpdateRequest;
import ali.com.banking.banking_backend.dto.LoginRequest;
import ali.com.banking.banking_backend.dto.LoginResponse;
import ali.com.banking.banking_backend.exception.CustomerNotFoundException;
import ali.com.banking.banking_backend.exception.DuplicateEmailException;
import ali.com.banking.banking_backend.exception.GlobalExceptionHandler;
import ali.com.banking.banking_backend.exception.InvalidCredentialsException;
import ali.com.banking.banking_backend.security.JwtAuthenticationFilter;
import ali.com.banking.banking_backend.service.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void registerCustomer_withValidRequest_returnsCreatedCustomer() throws Exception {
        CustomerRegistrationRequest request = validRegistrationRequest();
        CustomerResponse response = customerResponse();
        when(customerService.registerCustomer(any(CustomerRegistrationRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(1))
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.lastName").value("Smith"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.phone").value("+1234567890"));

        verify(customerService).registerCustomer(any(CustomerRegistrationRequest.class));
    }

    @Test
    void registerCustomer_withInvalidRequest_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(customerService);
    }

    @Test
    void login_withValidRequest_returnsToken() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("alice@example.com")
                .password("StrongPass123")
                .build();
        when(customerService.login(any(LoginRequest.class))).thenReturn(new LoginResponse("jwt-token"));

        mockMvc.perform(post("/api/customers/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));

        verify(customerService).login(any(LoginRequest.class));
    }

    @Test
    void getCustomerById_whenCustomerExists_returnsCustomer() throws Exception {
        when(customerService.getCustomerById(1L)).thenReturn(customerResponse());

        mockMvc.perform(get("/api/customers/{customerId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(1))
                .andExpect(jsonPath("$.email").value("alice@example.com"));

        verify(customerService).getCustomerById(1L);
    }

    @Test
    void getCustomerById_whenCustomerDoesNotExist_returnsNotFound() throws Exception {
        when(customerService.getCustomerById(99L))
                .thenThrow(new CustomerNotFoundException("Customer not found"));

        mockMvc.perform(get("/api/customers/{customerId}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Customer not found"));
    }

    @Test
    void getAllCustomers_returnsCustomers() throws Exception {
        CustomerResponse response = customerResponse();
        when(customerService.getAllCustomers()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value(1))
                .andExpect(jsonPath("$[0].firstName").value("Alice"))
                .andExpect(jsonPath("$[0].email").value("alice@example.com"));

        verify(customerService).getAllCustomers();
    }

    @Test
    void updateCustomer_withValidRequest_returnsUpdatedCustomer() throws Exception {
        CustomerUpdateRequest request = validUpdateRequest();
        CustomerResponse response = customerResponse();
        response.setFirstName("Updated");
        when(customerService.updateCustomer(eq(1L), any(CustomerUpdateRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/customers/{customerId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(1))
                .andExpect(jsonPath("$.firstName").value("Updated"));

        verify(customerService).updateCustomer(eq(1L), any(CustomerUpdateRequest.class));
    }

    @Test
    void deleteCustomer_whenSuccessful_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/customers/{customerId}", 1L))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(customerService).deleteCustomer(1L);
    }

    @Test
    void deleteCustomer_whenCustomerDoesNotExist_returnsNotFound() throws Exception {
        doThrow(new CustomerNotFoundException("Customer not found"))
                .when(customerService).deleteCustomer(99L);

        mockMvc.perform(delete("/api/customers/{customerId}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Customer not found"));
    }

    @Test
    void registerCustomer_whenEmailIsDuplicate_returnsConflict() throws Exception {
        when(customerService.registerCustomer(any(CustomerRegistrationRequest.class)))
                .thenThrow(new DuplicateEmailException("Email is already registered"));

        mockMvc.perform(post("/api/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegistrationRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    void login_withInvalidCredentials_returnsUnauthorized() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("alice@example.com")
                .password("WrongPass123")
                .build();
        when(customerService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/customers/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    private CustomerRegistrationRequest validRegistrationRequest() {
        return CustomerRegistrationRequest.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("+1234567890")
                .password("StrongPass123")
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .build();
    }

    private CustomerUpdateRequest validUpdateRequest() {
        return CustomerUpdateRequest.builder()
                .firstName("Updated")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("+1234567890")
                .password("StrongPass123")
                .address("456 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .build();
    }

    private CustomerResponse customerResponse() {
        return CustomerResponse.builder()
                .customerId(1L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("+1234567890")
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .createdAt(LocalDateTime.of(2026, 1, 1, 12, 0))
                .build();
    }
}

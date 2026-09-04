package ali.com.banking.banking_backend.controller;

import ali.com.banking.banking_backend.dto.AccountCreateRequest;
import ali.com.banking.banking_backend.dto.AccountResponse;
import ali.com.banking.banking_backend.entity.AccountStatus;
import ali.com.banking.banking_backend.entity.AccountType;
import ali.com.banking.banking_backend.exception.AccountNotFoundException;
import ali.com.banking.banking_backend.exception.CustomerAccessDeniedException;
import ali.com.banking.banking_backend.security.JwtAuthenticationFilter;
import ali.com.banking.banking_backend.service.AccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void createAccount_withValidRequest_returnsCreated() throws Exception {
        AccountCreateRequest request = validCreateRequest();
        AccountResponse response = validAccountResponse();

        when(accountService.createAccount(any(AccountCreateRequest.class), eq("alice@example.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/accounts")
                        .principal(new UsernamePasswordAuthenticationToken("alice@example.com", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(1))
                .andExpect(jsonPath("$.accountType").value("CHECKING"));

        verify(accountService).createAccount(any(AccountCreateRequest.class), eq("alice@example.com"));
    }

    @Test
    void createAccount_withMissingAccountType_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .principal(new UsernamePasswordAuthenticationToken("alice@example.com", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account type is required"));

        verifyNoInteractions(accountService);
    }

    @Test
    void getAccountById_withValidId_returnsAccount() throws Exception {
        AccountResponse response = validAccountResponse();

        when(accountService.getAccountById(1L, "alice@example.com")).thenReturn(response);

        mockMvc.perform(get("/api/accounts/{accountId}", 1L)
                        .principal(new UsernamePasswordAuthenticationToken("alice@example.com", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1))
                .andExpect(jsonPath("$.accountType").value("CHECKING"));

        verify(accountService).getAccountById(1L, "alice@example.com");
    }

    @Test
    void getAccountById_withZeroId_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/accounts/{accountId}", 0L)
                        .principal(new UsernamePasswordAuthenticationToken("alice@example.com", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account ID must be positive"));

        verifyNoInteractions(accountService);
    }

    @Test
    void getAccountById_withNegativeId_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/accounts/{accountId}", -1L)
                        .principal(new UsernamePasswordAuthenticationToken("alice@example.com", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account ID must be positive"));

        verifyNoInteractions(accountService);
    }

    @Test
    void getAccountById_whenAccountNotFound_returnsNotFound() throws Exception {
        when(accountService.getAccountById(99L, "alice@example.com"))
                .thenThrow(new AccountNotFoundException("Account not found"));

        mockMvc.perform(get("/api/accounts/{accountId}", 99L)
                        .principal(new UsernamePasswordAuthenticationToken("alice@example.com", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));
    }

    @Test
    void getAccountById_whenCustomerAccessDenied_returnsForbidden() throws Exception {
        when(accountService.getAccountById(2L, "alice@example.com"))
                .thenThrow(new CustomerAccessDeniedException("Customer access denied"));

        mockMvc.perform(get("/api/accounts/{accountId}", 2L)
                        .principal(new UsernamePasswordAuthenticationToken("alice@example.com", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Customer access denied"));
    }

    @Test
    void getMyAccounts_withAccounts_returnsAccountList() throws Exception {
        AccountResponse response = validAccountResponse();

        when(accountService.getMyAccounts("alice@example.com")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/accounts")
                        .principal(new UsernamePasswordAuthenticationToken("alice@example.com", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value(1))
                .andExpect(jsonPath("$[0].accountType").value("CHECKING"));

        verify(accountService).getMyAccounts("alice@example.com");
    }

    @Test
    void getMyAccounts_withNoAccounts_returnsEmptyList() throws Exception {
        when(accountService.getMyAccounts("alice@example.com")).thenReturn(List.of());

        mockMvc.perform(get("/api/accounts")
                        .principal(new UsernamePasswordAuthenticationToken("alice@example.com", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(accountService).getMyAccounts("alice@example.com");
    }

    @Test
    void getMyAccounts_whenCustomerAccessDenied_returnsForbidden() throws Exception {
        when(accountService.getMyAccounts("alice@example.com"))
                .thenThrow(new CustomerAccessDeniedException("Customer access denied"));

        mockMvc.perform(get("/api/accounts")
                        .principal(new UsernamePasswordAuthenticationToken("alice@example.com", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Customer access denied"));

        verify(accountService).getMyAccounts("alice@example.com");
    }

    @Test
    void closeAccount_withValidOwner_returnsNoContent() throws Exception {
        mockMvc.perform(patch("/api/accounts/{accountId}/close", 1L)
                        .principal(new UsernamePasswordAuthenticationToken("alice@example.com", null)))
                .andExpect(status().isNoContent());

        verify(accountService).closeAccount(1L, "alice@example.com");
    }

    @Test
    void closeAccount_whenAccountNotFound_returnsNotFound() throws Exception {
        doThrow(new AccountNotFoundException("Account not found"))
                .when(accountService).closeAccount(99L, "alice@example.com");

        mockMvc.perform(patch("/api/accounts/{accountId}/close", 99L)
                        .principal(new UsernamePasswordAuthenticationToken("alice@example.com", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));
    }

    @Test
    void closeAccount_whenCustomerAccessDenied_returnsForbidden() throws Exception {
        doThrow(new CustomerAccessDeniedException("Customer access denied"))
                .when(accountService).closeAccount(2L, "alice@example.com");

        mockMvc.perform(patch("/api/accounts/{accountId}/close", 2L)
                        .principal(new UsernamePasswordAuthenticationToken("alice@example.com", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Customer access denied"));
    }

    @Test
    void closeAccount_withZeroId_returnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/accounts/{accountId}/close", 0L)
                        .principal(new UsernamePasswordAuthenticationToken("alice@example.com", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account ID must be positive"));

        verifyNoInteractions(accountService);
    }

    @Test
    void closeAccount_withNegativeId_returnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/accounts/{accountId}/close", -1L)
                        .principal(new UsernamePasswordAuthenticationToken("alice@example.com", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account ID must be positive"));

        verifyNoInteractions(accountService);
    }

    @Test
    void suspendAccount_withValidOwner_returnsNoContent() throws Exception {
        mockMvc.perform(patch("/api/accounts/{accountId}/suspend", 1L)
                        .principal(new UsernamePasswordAuthenticationToken("alice@example.com", null)))
                .andExpect(status().isNoContent());

        verify(accountService).suspendAccount(1L, "alice@example.com");
    }

    @Test
    void suspendAccount_whenAccountNotFound_returnsNotFound() throws Exception {
        doThrow(new AccountNotFoundException("Account not found"))
                .when(accountService).suspendAccount(99L, "alice@example.com");

        mockMvc.perform(patch("/api/accounts/{accountId}/suspend", 99L)
                        .principal(new UsernamePasswordAuthenticationToken("alice@example.com", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));
    }

    @Test
    void suspendAccount_whenCustomerAccessDenied_returnsForbidden() throws Exception {
        doThrow(new CustomerAccessDeniedException("Customer access denied"))
                .when(accountService).suspendAccount(2L, "alice@example.com");

        mockMvc.perform(patch("/api/accounts/{accountId}/suspend", 2L)
                        .principal(new UsernamePasswordAuthenticationToken("alice@example.com", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Customer access denied"));
    }

    @Test
    void activateAccount_withValidOwner_returnsNoContent() throws Exception {
        mockMvc.perform(patch("/api/accounts/{accountId}/activate", 1L)
                        .principal(new UsernamePasswordAuthenticationToken("alice@example.com", null)))
                .andExpect(status().isNoContent());

        verify(accountService).activateAccount(1L, "alice@example.com");
    }

    @Test
    void activateAccount_whenAccountNotFound_returnsNotFound() throws Exception {
        doThrow(new AccountNotFoundException("Account not found"))
                .when(accountService).activateAccount(99L, "alice@example.com");

        mockMvc.perform(patch("/api/accounts/{accountId}/activate", 99L)
                        .principal(new UsernamePasswordAuthenticationToken("alice@example.com", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));
    }

    @Test
    void activateAccount_whenCustomerAccessDenied_returnsForbidden() throws Exception {
        doThrow(new CustomerAccessDeniedException("Customer access denied"))
                .when(accountService).activateAccount(2L, "alice@example.com");

        mockMvc.perform(patch("/api/accounts/{accountId}/activate", 2L)
                        .principal(new UsernamePasswordAuthenticationToken("alice@example.com", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Customer access denied"));
    }

    private AccountCreateRequest validCreateRequest() {
        return AccountCreateRequest.builder()
                .accountType(AccountType.CHECKING)
                .build();
    }

    private AccountResponse validAccountResponse() {
        return AccountResponse.builder()
                .accountId(1L)
                .accountNumber("ACC-123456789012")
                .accountType(AccountType.CHECKING)
                .balance(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.of(2026, 9, 2, 10, 0))
                .build();
    }
}

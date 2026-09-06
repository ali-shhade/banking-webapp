package ali.com.banking.banking_backend.security;

import ali.com.banking.banking_backend.config.JwtService;
import ali.com.banking.banking_backend.customer.dto.CustomerResponse;
import ali.com.banking.banking_backend.customer.entity.Customer;
import ali.com.banking.banking_backend.customer.repository.CustomerRepository;
import ali.com.banking.banking_backend.customer.service.CustomerService;
import ali.com.banking.banking_backend.exception.CustomerAccessDeniedException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-that-is-long-enough-for-hmac-sha",
        "jwt.expiration=86400000"
})
class JwtAuthenticationTest {

    private static final String ALICE_EMAIL = "alice@example.com";
    private static final String BOB_EMAIL = "bob@example.com";
    private static final String JWT_SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private CustomerRepository customerRepository;

    @MockitoBean
    private CustomerService customerService;

    @Test
    void getCustomerById_withoutJwt_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/customers/{customerId}", 1L))
                .andExpect(status().isUnauthorized());

        verify(customerService, never()).getCustomerById(eq(1L), eq(ALICE_EMAIL));
    }

    @Test
    void getCustomerById_withInvalidJwt_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/customers/{customerId}", 1L)
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCustomerById_withExpiredJwt_returnsUnauthorized() throws Exception {
        String token = expiredToken(ALICE_EMAIL);

        mockMvc.perform(get("/api/customers/{customerId}", 1L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCustomerById_withValidJwt_allowsAuthenticatedAccess() throws Exception {
        when(customerRepository.findByEmail(ALICE_EMAIL)).thenReturn(Optional.of(customer(ALICE_EMAIL)));
        when(customerService.getCustomerById(1L, ALICE_EMAIL)).thenReturn(customerResponse(1L, ALICE_EMAIL));

        mockMvc.perform(get("/api/customers/{customerId}", 1L)
                        .header("Authorization", "Bearer " + jwtService.generateToken(ALICE_EMAIL)))
                .andExpect(status().isOk());

        verify(customerService).getCustomerById(1L, ALICE_EMAIL);
    }

    @Test
    void getCustomerById_withValidJwtForAnotherCustomer_returnsForbidden() throws Exception {
        when(customerRepository.findByEmail(BOB_EMAIL)).thenReturn(Optional.of(customer(BOB_EMAIL)));
        when(customerService.getCustomerById(1L, BOB_EMAIL))
                .thenThrow(new CustomerAccessDeniedException("Customer access denied"));

        mockMvc.perform(get("/api/customers/{customerId}", 1L)
                        .header("Authorization", "Bearer " + jwtService.generateToken(BOB_EMAIL)))
                .andExpect(status().isForbidden());

        verify(customerService).getCustomerById(1L, BOB_EMAIL);
    }

    private String expiredToken(String email) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        Date issuedAt = new Date(System.currentTimeMillis() - 120_000);
        Date expiration = new Date(System.currentTimeMillis() - 60_000);

        return Jwts.builder()
                .subject(email)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    private Customer customer(String email) {
        return Customer.builder()
                .customerId(email.equals(ALICE_EMAIL) ? 1L : 2L)
                .email(email)
                .build();
    }

    private CustomerResponse customerResponse(Long customerId, String email) {
        return CustomerResponse.builder()
                .customerId(customerId)
                .email(email)
                .firstName("Test")
                .lastName("Customer")
                .phone("+1234567890")
                .address("Test address")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .build();
    }
}

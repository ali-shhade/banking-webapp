package ali.com.banking.banking_backend.controller;

import ali.com.banking.banking_backend.dto.CustomerRegistrationRequest;
import ali.com.banking.banking_backend.dto.CustomerResponse;
import ali.com.banking.banking_backend.dto.CustomerSummaryResponse;
import ali.com.banking.banking_backend.dto.CustomerUpdateRequest;
import ali.com.banking.banking_backend.dto.LoginRequest;
import ali.com.banking.banking_backend.dto.LoginResponse;
import ali.com.banking.banking_backend.service.CustomerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@Validated
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping("/register")
    public ResponseEntity<CustomerResponse> registerCustomer(@Valid @RequestBody CustomerRegistrationRequest request) {
        CustomerResponse response = customerService.registerCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerResponse> getCustomerById(
            @PathVariable @Positive(message = "Customer ID must be positive") Long customerId,
            Authentication authentication) {
        CustomerResponse response = customerService.getCustomerById(customerId, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<List<CustomerSummaryResponse>> getAllCustomers(Authentication authentication) {
        return ResponseEntity.ok(customerService.getAllCustomers(authentication.getName()));
    }

    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{customerId}")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable @Positive(message = "Customer ID must be positive") Long customerId,
            @Valid @RequestBody CustomerUpdateRequest request,
            Authentication authentication) {
        CustomerResponse response = customerService.updateCustomer(
                customerId, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{customerId}")
    public ResponseEntity<Void> deleteCustomer(
            @PathVariable @Positive(message = "Customer ID must be positive") Long customerId,
            Authentication authentication) {
        customerService.deleteCustomer(customerId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(customerService.login(request));
    }
}

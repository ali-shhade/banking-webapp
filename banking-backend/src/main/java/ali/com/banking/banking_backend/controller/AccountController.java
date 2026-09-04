package ali.com.banking.banking_backend.controller;

import ali.com.banking.banking_backend.dto.AccountCreateRequest;
import ali.com.banking.banking_backend.dto.AccountResponse;
import ali.com.banking.banking_backend.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@Validated
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody AccountCreateRequest request,
            Authentication authentication) {
        AccountResponse response = accountService.createAccount(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccountById(
            @PathVariable @Positive(message = "Account ID must be positive") Long accountId,
            Authentication authentication) {
        AccountResponse response = accountService.getAccountById(accountId, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{accountId}/close")
    public ResponseEntity<Void> closeAccount(
            @PathVariable @Positive(message = "Account ID must be positive") Long accountId,
            Authentication authentication) {
        accountService.closeAccount(accountId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{accountId}/suspend")
    public ResponseEntity<Void> suspendAccount(
            @PathVariable @Positive(message = "Account ID must be positive") Long accountId,
            Authentication authentication) {
        accountService.suspendAccount(accountId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{accountId}/activate")
    public ResponseEntity<Void> activateAccount(
            @PathVariable @Positive(message = "Account ID must be positive") Long accountId,
            Authentication authentication) {
        accountService.activateAccount(accountId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<List<AccountResponse>> getMyAccounts(Authentication authentication) {
        List<AccountResponse> response = accountService.getMyAccounts(authentication.getName());
        return ResponseEntity.ok(response);
    }
}

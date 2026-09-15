package ali.com.banking.banking_backend.transaction.controller;

import ali.com.banking.banking_backend.transaction.dto.DepositRequest;
import ali.com.banking.banking_backend.transaction.dto.TransactionResponse;
import ali.com.banking.banking_backend.transaction.dto.TransferRequest;
import ali.com.banking.banking_backend.transaction.dto.TransferResponse;
import ali.com.banking.banking_backend.transaction.dto.WithdrawalRequest;
import ali.com.banking.banking_backend.transaction.service.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts/{accountId}/transactions")
@RequiredArgsConstructor
@Validated
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @PathVariable @Positive(message = "Account ID must be positive") Long accountId,
            @Valid @RequestBody DepositRequest request,
            Authentication authentication) {
        TransactionResponse response = transactionService.deposit(
                accountId, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @PathVariable @Positive(message = "Account ID must be positive") Long accountId,
            @Valid @RequestBody WithdrawalRequest request,
            Authentication authentication) {
        TransactionResponse response = transactionService.withdraw(
                accountId, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> transfer(
            @PathVariable @Positive(message = "Account ID must be positive") Long accountId,
            @Valid @RequestBody TransferRequest request,
            Authentication authentication) {
        TransferResponse response = transactionService.transfer(
                accountId, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getAccountTransactions(
            @PathVariable @Positive(message = "Account ID must be positive") Long accountId,
            Authentication authentication) {
        List<TransactionResponse> response = transactionService.getAccountTransactions(
                accountId, authentication.getName());
        return ResponseEntity.ok(response);
    }
}

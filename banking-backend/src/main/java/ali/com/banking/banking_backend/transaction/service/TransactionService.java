package ali.com.banking.banking_backend.transaction.service;

import ali.com.banking.banking_backend.transaction.dto.DepositRequest;
import ali.com.banking.banking_backend.transaction.dto.TransactionResponse;
import ali.com.banking.banking_backend.transaction.dto.TransferRequest;
import ali.com.banking.banking_backend.transaction.dto.TransferResponse;
import ali.com.banking.banking_backend.transaction.dto.WithdrawalRequest;

import java.util.List;

public interface TransactionService {

    TransactionResponse deposit(Long accountId, DepositRequest request, String authenticatedEmail);

    TransactionResponse withdraw(Long accountId, WithdrawalRequest request, String authenticatedEmail);

    TransferResponse transfer(Long sourceAccountId, TransferRequest request, String authenticatedEmail);

    List<TransactionResponse> getAccountTransactions(Long accountId, String authenticatedEmail);
}

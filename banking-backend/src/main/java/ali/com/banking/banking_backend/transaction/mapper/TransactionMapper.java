package ali.com.banking.banking_backend.transaction.mapper;

import ali.com.banking.banking_backend.transaction.dto.TransactionResponse;
import ali.com.banking.banking_backend.transaction.entity.Transaction;

public class TransactionMapper {

    public static TransactionResponse toResponse(Transaction transaction) {
        if (transaction == null) {
            return null;
        }

        return TransactionResponse.builder()
                .transactionId(transaction.getTransactionId())
                .accountNumber(transaction.getAccount() != null ? transaction.getAccount().getAccountNumber() : null)
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .balanceAfter(transaction.getBalanceAfter())
                .description(transaction.getDescription())
                .reference(transaction.getReference())
                .transferReference(transaction.getTransferReference())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}

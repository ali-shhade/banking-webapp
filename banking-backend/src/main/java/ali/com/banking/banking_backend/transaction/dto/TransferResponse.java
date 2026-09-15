package ali.com.banking.banking_backend.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransferResponse {

    private String transferReference;
    private TransactionResponse sourceTransaction;
    private TransactionResponse destinationTransaction;
}
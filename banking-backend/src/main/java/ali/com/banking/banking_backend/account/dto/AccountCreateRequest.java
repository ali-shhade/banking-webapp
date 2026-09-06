package ali.com.banking.banking_backend.account.dto;

import ali.com.banking.banking_backend.account.entity.AccountType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountCreateRequest {

    @NotNull(message = "Account type is required")
    private AccountType accountType;
}

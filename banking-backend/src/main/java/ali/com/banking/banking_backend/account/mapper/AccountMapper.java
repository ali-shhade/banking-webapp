package ali.com.banking.banking_backend.account.mapper;

import ali.com.banking.banking_backend.account.dto.AccountCreateRequest;
import ali.com.banking.banking_backend.account.dto.AccountResponse;
import ali.com.banking.banking_backend.account.entity.Account;

public class AccountMapper {

    public static Account toEntity(AccountCreateRequest request) {
        if (request == null) {
            return null;
        }

        return Account.builder()
                .accountType(request.getAccountType())
                .build();
    }

    public static AccountResponse toResponse(Account account) {
        if (account == null) {
            return null;
        }

        return AccountResponse.builder()
                .accountId(account.getAccountId())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType())
                .balance(account.getBalance())
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .build();
    }
}

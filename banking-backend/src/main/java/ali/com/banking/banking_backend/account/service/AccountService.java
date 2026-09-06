package ali.com.banking.banking_backend.account.service;

import ali.com.banking.banking_backend.account.dto.AccountCreateRequest;
import ali.com.banking.banking_backend.account.dto.AccountResponse;

import java.util.List;

public interface AccountService {

    AccountResponse createAccount(AccountCreateRequest request, String authenticatedEmail);

    AccountResponse getAccountById(Long accountId, String authenticatedEmail);

    List<AccountResponse> getMyAccounts(String authenticatedEmail);

    void closeAccount(Long accountId, String authenticatedEmail);

    void suspendAccount(Long accountId, String authenticatedEmail);

    void activateAccount(Long accountId, String authenticatedEmail);
}

package ali.com.banking.banking_backend.service;

import ali.com.banking.banking_backend.dto.CustomerResponse;
import ali.com.banking.banking_backend.dto.CustomerSummaryResponse;
import ali.com.banking.banking_backend.dto.CustomerUpdateRequest;
import ali.com.banking.banking_backend.dto.LoginRequest;
import ali.com.banking.banking_backend.dto.LoginResponse;

import java.util.List;

public interface CustomerService {

    CustomerResponse registerCustomer(ali.com.banking.banking_backend.dto.CustomerRegistrationRequest request);

    CustomerResponse getCustomerById(Long customerId, String authenticatedEmail);

    List<CustomerSummaryResponse> getAllCustomers(String authenticatedEmail);

    CustomerResponse updateCustomer(Long customerId, CustomerUpdateRequest request, String authenticatedEmail);

    void deleteCustomer(Long customerId, String authenticatedEmail);

    LoginResponse login(LoginRequest request);
}

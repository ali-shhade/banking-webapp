package ali.com.banking.banking_backend.customer.service;

import ali.com.banking.banking_backend.customer.dto.CustomerRegistrationRequest;
import ali.com.banking.banking_backend.customer.dto.CustomerResponse;
import ali.com.banking.banking_backend.customer.dto.CustomerSummaryResponse;
import ali.com.banking.banking_backend.customer.dto.CustomerUpdateRequest;
import ali.com.banking.banking_backend.customer.dto.LoginRequest;
import ali.com.banking.banking_backend.customer.dto.LoginResponse;

import java.util.List;

public interface CustomerService {

    CustomerResponse registerCustomer(CustomerRegistrationRequest request);

    CustomerResponse getCustomerById(Long customerId, String authenticatedEmail);

    List<CustomerSummaryResponse> getAllCustomers(String authenticatedEmail);

    CustomerResponse updateCustomer(Long customerId, CustomerUpdateRequest request, String authenticatedEmail);

    void deleteCustomer(Long customerId, String authenticatedEmail);

    LoginResponse login(LoginRequest request);
}

package ali.com.banking.banking_backend.service;

import ali.com.banking.banking_backend.dto.CustomerRegistrationRequest;
import ali.com.banking.banking_backend.dto.CustomerResponse;
import ali.com.banking.banking_backend.dto.LoginRequest;
import ali.com.banking.banking_backend.dto.LoginResponse;

import java.util.List;

public interface CustomerService {

    CustomerResponse registerCustomer(CustomerRegistrationRequest request);

    CustomerResponse getCustomerById(Long customerId);

    List<CustomerResponse> getAllCustomers();

    CustomerResponse updateCustomer(Long customerId, CustomerRegistrationRequest request);

    void deleteCustomer(Long customerId);

    LoginResponse login(LoginRequest request);
}

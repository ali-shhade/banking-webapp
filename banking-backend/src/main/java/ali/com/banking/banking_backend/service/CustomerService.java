package ali.com.banking.banking_backend.service;

import ali.com.banking.banking_backend.dto.CustomerResponse;
import ali.com.banking.banking_backend.dto.CustomerUpdateRequest;
import ali.com.banking.banking_backend.dto.LoginRequest;
import ali.com.banking.banking_backend.dto.LoginResponse;

import java.util.List;

public interface CustomerService {

    CustomerResponse registerCustomer(ali.com.banking.banking_backend.dto.CustomerRegistrationRequest request);

    CustomerResponse getCustomerById(Long customerId);

    List<CustomerResponse> getAllCustomers();

    CustomerResponse updateCustomer(Long customerId, CustomerUpdateRequest request);

    void deleteCustomer(Long customerId);

    LoginResponse login(LoginRequest request);
}

package ali.com.banking.banking_backend.customer.mapper;

import ali.com.banking.banking_backend.customer.dto.CustomerRegistrationRequest;
import ali.com.banking.banking_backend.customer.dto.CustomerResponse;
import ali.com.banking.banking_backend.customer.dto.CustomerSummaryResponse;
import ali.com.banking.banking_backend.customer.entity.Customer;

public class CustomerMapper {

    public static Customer toEntity(CustomerRegistrationRequest request) {
        if (request == null) {
            return null;
        }

    return Customer.builder()
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .email(request.getEmail().trim().toLowerCase())
            .phone(request.getPhone().trim())
            .password(request.getPassword())
            .address(request.getAddress())
            .dateOfBirth(request.getDateOfBirth())
            .nationalId(request.getNationalId().trim())
            .build();
}

    public static CustomerResponse toResponse(Customer customer) {
        if (customer == null) {
            return null;
        }

        return CustomerResponse.builder()
                .customerId(customer.getCustomerId())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .address(customer.getAddress())
                .dateOfBirth(customer.getDateOfBirth())
                .nationalId(customer.getNationalId())
                .createdAt(customer.getCreatedAt())
                .build();
    }

    public static CustomerSummaryResponse toSummaryResponse(Customer customer) {
        if (customer == null) {
            return null;
        }

        return CustomerSummaryResponse.builder()
                .customerId(customer.getCustomerId())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .address(customer.getAddress())
                .dateOfBirth(customer.getDateOfBirth())
                .createdAt(customer.getCreatedAt())
                .build();
    }
}

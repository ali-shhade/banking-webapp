package ali.com.banking.banking_backend.service.impl;

import ali.com.banking.banking_backend.dto.CustomerRegistrationRequest;
import ali.com.banking.banking_backend.dto.CustomerResponse;
import ali.com.banking.banking_backend.dto.CustomerUpdateRequest;
import ali.com.banking.banking_backend.dto.LoginRequest;
import ali.com.banking.banking_backend.dto.LoginResponse;
import ali.com.banking.banking_backend.config.JwtService;
import ali.com.banking.banking_backend.entity.Customer;
import ali.com.banking.banking_backend.exception.CustomerNotFoundException;
import ali.com.banking.banking_backend.exception.DuplicateEmailException;
import ali.com.banking.banking_backend.exception.DuplicateNationalIdException;
import ali.com.banking.banking_backend.exception.DuplicatePhoneException;
import ali.com.banking.banking_backend.exception.InvalidCredentialsException;
import ali.com.banking.banking_backend.mapper.CustomerMapper;
import ali.com.banking.banking_backend.repository.CustomerRepository;
import ali.com.banking.banking_backend.service.CustomerService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public CustomerServiceImpl(
            CustomerRepository customerRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
public CustomerResponse registerCustomer(CustomerRegistrationRequest request) {

    if (request == null) {
        throw new IllegalArgumentException("Registration request must not be null");
    }

    String email = request.getEmail().trim().toLowerCase();
    String phone = request.getPhone().trim();
    String nationalId = request.getNationalId().trim();


    if (customerRepository.existsByEmail(email)) {
        throw new DuplicateEmailException("Email already exists");
    }


    if (customerRepository.existsByPhone(phone)) {
        throw new DuplicatePhoneException("Phone number already exists");
    }


    if (customerRepository.existsByNationalId(nationalId)) {
        throw new DuplicateNationalIdException("National ID already exists");
    }
    
    Customer customer = CustomerMapper.toEntity(request);

    String encodedPassword = passwordEncoder.encode(request.getPassword());

    customer.setPassword(encodedPassword);

    Customer savedCustomer = customerRepository.save(customer);

    return CustomerMapper.toResponse(savedCustomer);
}

@Override
public CustomerResponse getCustomerById(Long customerId) {
    if (customerId == null) {
        throw new IllegalArgumentException("Customer ID must not be null");
    }

    Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));

    return CustomerMapper.toResponse(customer);
}

    @Override
    public List<CustomerResponse> getAllCustomers() {
        List<Customer> customers = customerRepository.findAll();
        return customers.stream()
                .map(CustomerMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CustomerResponse updateCustomer(Long customerId, CustomerUpdateRequest request) {
        // 1. Validate request is not null
        if (request == null) {
            throw new IllegalArgumentException("Registration request must not be null");
        }

        // 2. Find existing customer
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));

        // 3-6. Normalize incoming data
        String email = request.getEmail().trim().toLowerCase();
        String phone = request.getPhone().trim();
        String nationalId = request.getNationalId().trim();

        // 7. Check email uniqueness (only if changed)
        if (!email.equals(customer.getEmail()) && customerRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("Email already exists");
        }

        // 8. Check phone uniqueness (only if changed)
        if (!phone.equals(customer.getPhone()) && customerRepository.existsByPhone(phone)) {
            throw new DuplicatePhoneException("Phone number already exists");
        }

        // 9. Check national ID uniqueness (only if changed)
        if (!nationalId.equals(customer.getNationalId()) && customerRepository.existsByNationalId(nationalId)) {
            throw new DuplicateNationalIdException("National ID already exists");
        }

        // 10. Update customer fields
        customer.setFirstName(request.getFirstName());
        customer.setLastName(request.getLastName());
        customer.setEmail(email);
        customer.setPhone(phone);
        customer.setAddress(request.getAddress());
        customer.setDateOfBirth(request.getDateOfBirth());
        customer.setNationalId(nationalId);

        // 11. Update password if provided
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            String encodedPassword = passwordEncoder.encode(request.getPassword());
            customer.setPassword(encodedPassword);
        }

        // 12. Save updated customer
        Customer updatedCustomer = customerRepository.save(customer);

        // 13-14. Convert and return response
        return CustomerMapper.toResponse(updatedCustomer);
    }

    @Override
    @Transactional
    public void deleteCustomer(Long customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID must not be null");
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));

        customerRepository.delete(customer);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Login request must not be null");
        }

        String email = request.getEmail().trim().toLowerCase();
        String password = request.getPassword();

        Customer customer = customerRepository.findByEmail(email)
            .filter(foundCustomer -> passwordEncoder.matches(password, foundCustomer.getPassword()))
            .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        return LoginResponse.builder()
            .token(jwtService.generateToken(customer.getEmail()))
            .build();
    }
}

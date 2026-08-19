package ali.com.banking.banking_backend.service.impl;

import ali.com.banking.banking_backend.config.JwtService;
import ali.com.banking.banking_backend.dto.CustomerRegistrationRequest;
import ali.com.banking.banking_backend.dto.CustomerResponse;
import ali.com.banking.banking_backend.dto.CustomerUpdateRequest;
import ali.com.banking.banking_backend.dto.LoginRequest;
import ali.com.banking.banking_backend.dto.LoginResponse;
import ali.com.banking.banking_backend.entity.Customer;
import ali.com.banking.banking_backend.exception.CustomerNotFoundException;
import ali.com.banking.banking_backend.exception.DuplicateEmailException;
import ali.com.banking.banking_backend.exception.DuplicateNationalIdException;
import ali.com.banking.banking_backend.exception.DuplicatePhoneException;
import ali.com.banking.banking_backend.exception.InvalidCredentialsException;
import ali.com.banking.banking_backend.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private CustomerRegistrationRequest validRegistrationRequest() {
        return CustomerRegistrationRequest.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("+1234567890")
                .password("StrongPass123")
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .build();
    }

    private CustomerUpdateRequest validUpdateRequest() {
        return CustomerUpdateRequest.builder()
                .firstName("Updated")
                .lastName("Customer")
                .email("updated@example.com")
                .phone("+1987654321")
                .password(null)
                .address("456 New St")
                .dateOfBirth(LocalDate.of(1991, 2, 3))
                .nationalId("9876543210")
                .build();
    }

    @Test
    void registerCustomer_shouldRegisterSuccessfully() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);
        CustomerRegistrationRequest request = validRegistrationRequest();

        when(customerRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(customerRepository.existsByPhone("+1234567890")).thenReturn(false);
        when(customerRepository.existsByNationalId("1234567890")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass123")).thenReturn("encoded-password");

        Customer savedCustomer = Customer.builder()
                .customerId(1L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("+1234567890")
                .password("encoded-password")
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .createdAt(LocalDateTime.now())
                .build();
        when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);

        CustomerResponse response = service.registerCustomer(request);

        assertEquals(1L, response.getCustomerId());
        assertEquals("Alice", response.getFirstName());
        assertEquals("Smith", response.getLastName());
        assertEquals("alice@example.com", response.getEmail());
        assertEquals("+1234567890", response.getPhone());
        assertEquals("123 Main St", response.getAddress());
        assertEquals(LocalDate.of(1990, 1, 1), response.getDateOfBirth());
        assertEquals("1234567890", response.getNationalId());

        verify(passwordEncoder).encode("StrongPass123");
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void login_shouldReturnTokenWhenCredentialsAreValid() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);
        LoginRequest request = LoginRequest.builder()
                .email("  Alice@Example.com  ")
                .password("StrongPass123")
                .build();

        Customer customer = Customer.builder()
                .customerId(1L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("+1234567890")
                .password("encoded-password")
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .build();

        when(customerRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("StrongPass123", "encoded-password")).thenReturn(true);
        when(jwtService.generateToken("alice@example.com")).thenReturn("test-jwt");

        LoginResponse response = service.login(request);

        assertEquals("test-jwt", response.getToken());
        verify(jwtService).generateToken("alice@example.com");
    }

    @Test
    void login_shouldThrowUnauthorizedWhenPasswordIsWrong() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);
        LoginRequest request = LoginRequest.builder()
                .email("alice@example.com")
                .password("WrongPass123")
                .build();

        Customer customer = Customer.builder()
                .customerId(1L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("+1234567890")
                .password("encoded-password")
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .build();

        when(customerRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("WrongPass123", "encoded-password")).thenReturn(false);

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> service.login(request)
        );

        assertEquals("Invalid email or password", exception.getMessage());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_shouldThrowUnauthorizedWhenEmailDoesNotExist() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);
        LoginRequest request = LoginRequest.builder()
                .email("missing@example.com")
                .password("StrongPass123")
                .build();

        when(customerRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> service.login(request)
        );

        assertEquals("Invalid email or password", exception.getMessage());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_shouldThrowIllegalArgumentExceptionWhenRequestIsNull() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.login(null)
        );

        assertEquals("Login request must not be null", exception.getMessage());
        verifyNoInteractions(customerRepository, passwordEncoder, jwtService);
    }

    @Test
    void login_shouldNormalizeEmailBeforeLookup() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);
        LoginRequest request = LoginRequest.builder()
                .email("  Alice@Example.com  ")
                .password("StrongPass123")
                .build();

        Customer customer = Customer.builder()
                .customerId(1L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("+1234567890")
                .password("encoded-password")
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .build();

        when(customerRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("StrongPass123", "encoded-password")).thenReturn(true);
        when(jwtService.generateToken("alice@example.com")).thenReturn("test-jwt");

        service.login(request);

        verify(customerRepository).findByEmail(eq("alice@example.com"));
    }

    @Test
    void registerCustomer_shouldThrowConflictWhenEmailAlreadyExists() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);
        CustomerRegistrationRequest request = validRegistrationRequest();

        when(customerRepository.existsByEmail("alice@example.com")).thenReturn(true);

        DuplicateEmailException exception = assertThrows(
                DuplicateEmailException.class,
                () -> service.registerCustomer(request)
        );

        assertEquals("Email already exists", exception.getMessage());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void registerCustomer_shouldThrowConflictWhenPhoneAlreadyExists() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);
        CustomerRegistrationRequest request = validRegistrationRequest();

        when(customerRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(customerRepository.existsByPhone("+1234567890")).thenReturn(true);

        DuplicatePhoneException exception = assertThrows(
                DuplicatePhoneException.class,
                () -> service.registerCustomer(request)
        );

        assertEquals("Phone number already exists", exception.getMessage());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void registerCustomer_shouldThrowConflictWhenNationalIdAlreadyExists() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);
        CustomerRegistrationRequest request = validRegistrationRequest();

        when(customerRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(customerRepository.existsByPhone("+1234567890")).thenReturn(false);
        when(customerRepository.existsByNationalId("1234567890")).thenReturn(true);

        DuplicateNationalIdException exception = assertThrows(
                DuplicateNationalIdException.class,
                () -> service.registerCustomer(request)
        );

        assertEquals("National ID already exists", exception.getMessage());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void registerCustomer_shouldThrowIllegalArgumentExceptionWhenRequestIsNull() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.registerCustomer(null)
        );

        assertEquals("Registration request must not be null", exception.getMessage());
        verifyNoInteractions(customerRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerCustomer_shouldEncodePasswordBeforeSaving() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);
        CustomerRegistrationRequest request = validRegistrationRequest();

        when(customerRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(customerRepository.existsByPhone("+1234567890")).thenReturn(false);
        when(customerRepository.existsByNationalId("1234567890")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass123")).thenReturn("encoded-password");

        Customer savedCustomer = Customer.builder()
                .customerId(2L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("+1234567890")
                .password("encoded-password")
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .createdAt(LocalDateTime.now())
                .build();
        when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);

        service.registerCustomer(request);

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());

        Customer customerPassedToSave = customerCaptor.getValue();
        assertEquals("encoded-password", customerPassedToSave.getPassword());
        assertNotEquals("StrongPass123", customerPassedToSave.getPassword());
    }

    @Test
    void getCustomerById_shouldReturnCustomerResponseForExistingCustomer() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);
        Customer customer = Customer.builder()
                .customerId(7L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("+1234567890")
                .password("encoded-password")
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .createdAt(LocalDateTime.now())
                .build();

        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));

        CustomerResponse response = service.getCustomerById(7L);

        assertEquals(7L, response.getCustomerId());
        assertEquals("Alice", response.getFirstName());
        assertEquals("Smith", response.getLastName());
        assertEquals("alice@example.com", response.getEmail());
        assertEquals("+1234567890", response.getPhone());
        assertEquals("123 Main St", response.getAddress());
        assertEquals(LocalDate.of(1990, 1, 1), response.getDateOfBirth());
        assertEquals("1234567890", response.getNationalId());
        assertEquals(customer.getCreatedAt(), response.getCreatedAt());
        verify(customerRepository).findById(7L);
    }

    @Test
    void getCustomerById_shouldThrowNotFoundWhenCustomerDoesNotExist() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);

        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        CustomerNotFoundException exception = assertThrows(
                CustomerNotFoundException.class,
                () -> service.getCustomerById(99L)
        );

        assertEquals("Customer not found", exception.getMessage());
    }

    @Test
    void getCustomerById_shouldRejectNullCustomerId() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.getCustomerById(null)
        );

        assertEquals("Customer ID must not be null", exception.getMessage());
        verifyNoInteractions(customerRepository);
    }

    @Test
    void getAllCustomers_shouldReturnMappedCustomerResponses() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);

        Customer firstCustomer = Customer.builder()
                .customerId(1L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("+1234567890")
                .password("encoded-password")
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .createdAt(LocalDateTime.of(2024, 1, 1, 10, 0))
                .build();

        Customer secondCustomer = Customer.builder()
                .customerId(2L)
                .firstName("Bob")
                .lastName("Jones")
                .email("bob@example.com")
                .phone("+1987654321")
                .password("encoded-password-2")
                .address("456 Oak Ave")
                .dateOfBirth(LocalDate.of(1985, 5, 15))
                .nationalId("0987654321")
                .createdAt(LocalDateTime.of(2024, 2, 2, 11, 30))
                .build();

        when(customerRepository.findAll()).thenReturn(java.util.List.of(firstCustomer, secondCustomer));

        java.util.List<CustomerResponse> response = service.getAllCustomers();

        assertEquals(2, response.size());
        assertEquals("Alice", response.get(0).getFirstName());
        assertEquals("Smith", response.get(0).getLastName());
        assertEquals("alice@example.com", response.get(0).getEmail());
        assertEquals("123 Main St", response.get(0).getAddress());
        assertEquals("Bob", response.get(1).getFirstName());
        assertEquals("Jones", response.get(1).getLastName());
        assertEquals("bob@example.com", response.get(1).getEmail());
        assertEquals("456 Oak Ave", response.get(1).getAddress());
        verify(customerRepository).findAll();
    }

    @Test
    void getAllCustomers_shouldReturnEmptyListWhenNoCustomersExist() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);

        when(customerRepository.findAll()).thenReturn(java.util.Collections.emptyList());

        java.util.List<CustomerResponse> response = service.getAllCustomers();

        assertEquals(0, response.size());
        assertEquals(java.util.Collections.emptyList(), response);
        verify(customerRepository).findAll();
    }

    @Test
    void getAllCustomers_shouldCallRepositoryExactlyOnce() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);
        when(customerRepository.findAll()).thenReturn(java.util.Collections.emptyList());

        service.getAllCustomers();

        verify(customerRepository).findAll();
    }

    @Test
    void updateCustomer_shouldUpdateFieldsWithoutChangingPassword() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);
        Customer existingCustomer = Customer.builder()
                .customerId(1L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("+1234567890")
                .password("old-encoded-password")
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .createdAt(LocalDateTime.now())
                .build();

        CustomerUpdateRequest request = CustomerUpdateRequest.builder()
                .firstName("Updated")
                .lastName("User")
                .email("alice@example.com")
                .phone("+1234567890")
                .password(null)
                .address("456 New St")
                .dateOfBirth(LocalDate.of(1991, 2, 3))
                .nationalId("1234567890")
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse response = service.updateCustomer(1L, request);

        assertEquals("Updated", response.getFirstName());
        assertEquals("User", response.getLastName());
        assertEquals("456 New St", response.getAddress());
        assertEquals(LocalDate.of(1991, 2, 3), response.getDateOfBirth());
        assertEquals("alice@example.com", response.getEmail());
        assertEquals("+1234567890", response.getPhone());
        assertEquals("1234567890", response.getNationalId());
        verify(passwordEncoder, never()).encode(any());
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void updateCustomer_shouldUpdatePasswordWhenNewPasswordIsProvided() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);
        Customer existingCustomer = Customer.builder()
                .customerId(2L)
                .firstName("Bob")
                .lastName("Jones")
                .email("bob@example.com")
                .phone("+1987654321")
                .password("old-encoded-password")
                .address("456 Oak Ave")
                .dateOfBirth(LocalDate.of(1985, 5, 15))
                .nationalId("9876543210")
                .createdAt(LocalDateTime.now())
                .build();

        CustomerUpdateRequest request = CustomerUpdateRequest.builder()
                .firstName("Bob")
                .lastName("Jones")
                .email("bob@example.com")
                .phone("+1987654321")
                .password("NewPassword123")
                .address("456 Oak Ave")
                .dateOfBirth(LocalDate.of(1985, 5, 15))
                .nationalId("9876543210")
                .build();

        when(customerRepository.findById(2L)).thenReturn(Optional.of(existingCustomer));
        when(passwordEncoder.encode("NewPassword123")).thenReturn("new-encoded-password");
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateCustomer(2L, request);

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        Customer savedCustomer = customerCaptor.getValue();

        assertEquals("new-encoded-password", savedCustomer.getPassword());
        assertNotEquals("NewPassword123", savedCustomer.getPassword());
        verify(passwordEncoder).encode("NewPassword123");
    }

    @Test
    void updateCustomer_shouldThrowNotFoundWhenCustomerDoesNotExist() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);
        CustomerUpdateRequest request = validUpdateRequest();

        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        CustomerNotFoundException exception = assertThrows(
                CustomerNotFoundException.class,
                () -> service.updateCustomer(99L, request)
        );

        assertEquals("Customer not found", exception.getMessage());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void updateCustomer_shouldThrowConflictWhenEmailAlreadyExists() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);
        Customer existingCustomer = Customer.builder()
                .customerId(1L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("+1234567890")
                .password("old-encoded-password")
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .build();

        CustomerUpdateRequest request = CustomerUpdateRequest.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("other@example.com")
                .phone("+1234567890")
                .password(null)
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer));
        when(customerRepository.existsByEmail("other@example.com")).thenReturn(true);

        DuplicateEmailException exception = assertThrows(
                DuplicateEmailException.class,
                () -> service.updateCustomer(1L, request)
        );

        assertEquals("Email already exists", exception.getMessage());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void updateCustomer_shouldThrowConflictWhenPhoneAlreadyExists() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);
        Customer existingCustomer = Customer.builder()
                .customerId(1L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("+1234567890")
                .password("old-encoded-password")
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .build();

        CustomerUpdateRequest request = CustomerUpdateRequest.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("+9999999999")
                .password(null)
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer));
        when(customerRepository.existsByPhone("+9999999999")).thenReturn(true);

        DuplicatePhoneException exception = assertThrows(
                DuplicatePhoneException.class,
                () -> service.updateCustomer(1L, request)
        );

        assertEquals("Phone number already exists", exception.getMessage());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void updateCustomer_shouldThrowConflictWhenNationalIdAlreadyExists() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);
        Customer existingCustomer = Customer.builder()
                .customerId(1L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("+1234567890")
                .password("old-encoded-password")
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .build();

        CustomerUpdateRequest request = CustomerUpdateRequest.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("+1234567890")
                .password(null)
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("9999999999")
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer));
        when(customerRepository.existsByNationalId("9999999999")).thenReturn(true);

        DuplicateNationalIdException exception = assertThrows(
                DuplicateNationalIdException.class,
                () -> service.updateCustomer(1L, request)
        );

        assertEquals("National ID already exists", exception.getMessage());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void updateCustomer_shouldAllowExistingEmailWithoutRejectingAsDuplicate() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);
        Customer existingCustomer = Customer.builder()
                .customerId(1L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("+1234567890")
                .password("old-encoded-password")
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .build();

        CustomerUpdateRequest request = CustomerUpdateRequest.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("+1111111111")
                .password(null)
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("3333333333")
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateCustomer(1L, request);

        verify(customerRepository, never()).existsByEmail("alice@example.com");
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void updateCustomer_shouldAllowExistingPhoneWithoutRejectingAsDuplicate() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);
        Customer existingCustomer = Customer.builder()
                .customerId(1L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("+1234567890")
                .password("old-encoded-password")
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .build();

        CustomerUpdateRequest request = CustomerUpdateRequest.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("new@example.com")
                .phone("+1234567890")
                .password(null)
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("3333333333")
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer));
        when(customerRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateCustomer(1L, request);

        verify(customerRepository, never()).existsByPhone("+1234567890");
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void updateCustomer_shouldAllowExistingNationalIdWithoutRejectingAsDuplicate() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);
        Customer existingCustomer = Customer.builder()
                .customerId(1L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("+1234567890")
                .password("old-encoded-password")
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .build();

        CustomerUpdateRequest request = CustomerUpdateRequest.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("new@example.com")
                .phone("+1111111111")
                .password(null)
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("1234567890")
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer));
        when(customerRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(customerRepository.existsByPhone("+1111111111")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateCustomer(1L, request);

        verify(customerRepository, never()).existsByNationalId("1234567890");
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void updateCustomer_shouldThrowIllegalArgumentExceptionWhenRequestIsNull() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateCustomer(1L, null)
        );

        assertEquals("Registration request must not be null", exception.getMessage());
        verifyNoInteractions(customerRepository, passwordEncoder, jwtService);
    }

    @Test
    void deleteCustomer_shouldDeleteExistingCustomer() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);
        Customer customer = Customer.builder()
                .customerId(1L)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .phone("1234567890")
                .password("encoded-password")
                .address("123 Main St")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .nationalId("ABC123")
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        service.deleteCustomer(1L);

        verify(customerRepository).delete(customer);
    }

    @Test
    void deleteCustomer_shouldThrowNotFoundWhenCustomerDoesNotExist() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);

        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        CustomerNotFoundException exception = assertThrows(
                CustomerNotFoundException.class,
                () -> service.deleteCustomer(99L)
        );

        assertEquals("Customer not found", exception.getMessage());
    }

    @Test
    void deleteCustomer_shouldRejectNullCustomerId() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, passwordEncoder, jwtService);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.deleteCustomer(null)
        );

        assertEquals("Customer ID must not be null", exception.getMessage());
    }
}

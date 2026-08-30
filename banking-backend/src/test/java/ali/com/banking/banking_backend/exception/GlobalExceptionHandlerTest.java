package ali.com.banking.banking_backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleCustomerNotFound_returnsNotFoundWithMessage() {
        assertExceptionResponse(
                handler.handleCustomerNotFound(new CustomerNotFoundException("Customer was not found")),
                HttpStatus.NOT_FOUND,
                "Customer was not found");
    }

    @Test
    void handleDuplicateEmail_returnsConflictWithMessage() {
        assertExceptionResponse(
                handler.handleDuplicateEmail(new DuplicateEmailException("Email is already registered")),
                HttpStatus.CONFLICT,
                "Email is already registered");
    }

    @Test
    void handleDuplicatePhone_returnsConflictWithMessage() {
        assertExceptionResponse(
                handler.handleDuplicatePhone(new DuplicatePhoneException("Phone is already registered")),
                HttpStatus.CONFLICT,
                "Phone is already registered");
    }

    @Test
    void handleDuplicateNationalId_returnsConflictWithMessage() {
        assertExceptionResponse(
                handler.handleDuplicateNationalId(new DuplicateNationalIdException("National ID is already registered")),
                HttpStatus.CONFLICT,
                "National ID is already registered");
    }

    @Test
    void handleInvalidCredentials_returnsUnauthorizedWithMessage() {
        assertExceptionResponse(
                handler.handleInvalidCredentials(new InvalidCredentialsException("Invalid email or password")),
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password");
    }

    @Test
    void handleCustomerAccessDenied_returnsForbiddenWithMessage() {
        assertExceptionResponse(
                handler.handleCustomerAccessDenied(new CustomerAccessDeniedException("Customer access denied")),
                HttpStatus.FORBIDDEN,
                "Customer access denied");
    }

    @Test
    void handleMethodArgumentNotValid_returnsBadRequestWithValidationMessage() throws NoSuchMethodException {
        Method method = ValidationTarget.class.getDeclaredMethod("submit", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new ValidationTarget(), "request");
        bindingResult.addError(new FieldError("request", "name", "Name is required"));

        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);

        assertExceptionResponse(
                handler.handleMethodArgumentNotValid(exception),
                HttpStatus.BAD_REQUEST,
                "Name is required");
    }

    @Test
    void handleMethodArgumentTypeMismatch_returnsBadRequestWithoutInternalDetails() {
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "customerId", null, new NumberFormatException("internal details"));

        ResponseEntity<Map<String, Object>> response = handler.handleMethodArgumentTypeMismatch(exception);

        assertExceptionResponse(response, HttpStatus.BAD_REQUEST, "Customer ID must be a valid number");
    }

    @Test
    void handleDataIntegrityViolation_returnsConflictWithoutDatabaseDetails() {
        ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("SQL table and constraint details"));

        assertExceptionResponse(response, HttpStatus.CONFLICT, "Customer details already exist");
    }

    private void assertExceptionResponse(ResponseEntity<Map<String, Object>> response,
                                        HttpStatus expectedStatus,
                                        String expectedMessage) {
        assertEquals(expectedStatus, response.getStatusCode());
        assertEquals(expectedMessage, response.getBody().get("message"));
    }

    private static class ValidationTarget {
        @SuppressWarnings("unused")
        private void submit(String name) {
        }
    }
}

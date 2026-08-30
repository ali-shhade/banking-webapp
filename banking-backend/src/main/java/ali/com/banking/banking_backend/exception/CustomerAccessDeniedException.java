package ali.com.banking.banking_backend.exception;

public class CustomerAccessDeniedException extends RuntimeException {

    public CustomerAccessDeniedException(String message) {
        super(message);
    }
}
package ali.com.banking.banking_backend.exception;

public class DuplicateNationalIdException extends RuntimeException {

    public DuplicateNationalIdException(String message) {
        super(message);
    }
}

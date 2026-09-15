package ali.com.banking.banking_backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {
    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isBlank()) {
            return true;
        }

        if (password.length() < 8 || password.length() > 64) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Password must be between 8 and 64 characters")
                    .addConstraintViolation();
            return false;
        }

        if (!(password.matches(".*[a-z].*")
                && password.matches(".*[A-Z].*")
                && password.matches(".*\\d.*")
                && password.matches(".*[^A-Za-z0-9\\s].*"))) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                            "Password must contain uppercase, lowercase, digit, and special character")
                    .addConstraintViolation();
            return false;
        }

        return true;
    }
}

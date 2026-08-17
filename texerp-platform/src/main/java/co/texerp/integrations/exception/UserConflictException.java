package co.texerp.integrations.exception;

public class UserConflictException extends RuntimeException {

    private final String field;

    public UserConflictException(
            String field,
            String message
    ) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
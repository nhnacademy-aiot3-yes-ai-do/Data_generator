package site.yesaido.data_generator.exception;

import java.io.Serial;

public class InvalidActuatorCommandException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidActuatorCommandException(String message) {
        super(message);
    }

    public InvalidActuatorCommandException(String message, Throwable cause) {
        super(message, cause);
    }
}
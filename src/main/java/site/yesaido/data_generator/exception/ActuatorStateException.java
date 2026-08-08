package site.yesaido.data_generator.exception;

import java.io.Serial;

public class ActuatorStateException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ActuatorStateException(String message) {
        super(message);
    }
    public ActuatorStateException(String message, Throwable cause) {
        super(message, cause);
    }

}

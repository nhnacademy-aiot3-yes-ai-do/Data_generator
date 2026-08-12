package site.yesaido.data_generator.exception;

import java.io.Serial;

public class ActuatorCommandUnavailableException extends RuntimeException {


    @Serial
    private static final long serialVersionUID = 1L;

    public ActuatorCommandUnavailableException(String message, Throwable cause){
        super(message, cause);
    }
    public ActuatorCommandUnavailableException(String message) {
        super(message);
    }
}

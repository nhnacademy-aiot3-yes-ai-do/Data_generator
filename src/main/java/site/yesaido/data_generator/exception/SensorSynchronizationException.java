package site.yesaido.data_generator.exception;

import java.io.Serial;

public class SensorSynchronizationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SensorSynchronizationException(String message) {
        super(message);
    }

    public SensorSynchronizationException(String message, Throwable cause) {
        super(message, cause);
    }
}

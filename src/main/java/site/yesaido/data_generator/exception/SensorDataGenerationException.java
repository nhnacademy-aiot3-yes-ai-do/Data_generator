package site.yesaido.data_generator.exception;

import java.io.Serial;

public class SensorDataGenerationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SensorDataGenerationException(String message) {
        super(message);
    }
}

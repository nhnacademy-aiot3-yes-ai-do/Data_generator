package site.yesaido.data_generator.exception;

import java.io.Serial;

public class SensorCacheException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SensorCacheException(String message) {
        super(message);
    }
}

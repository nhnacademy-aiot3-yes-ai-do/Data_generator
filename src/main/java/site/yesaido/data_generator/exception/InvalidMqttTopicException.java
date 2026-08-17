package site.yesaido.data_generator.exception;

import java.io.Serial;

public class InvalidMqttTopicException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidMqttTopicException(String message) {
        super(message);
    }
}

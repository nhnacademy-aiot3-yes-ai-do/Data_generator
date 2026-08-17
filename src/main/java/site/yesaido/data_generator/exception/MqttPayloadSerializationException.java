package site.yesaido.data_generator.exception;

import java.io.Serial;

public class MqttPayloadSerializationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MqttPayloadSerializationException(String message) {
        super(message);
    }

    public MqttPayloadSerializationException(String message, Throwable cause){
        super(message,cause);
    }
}

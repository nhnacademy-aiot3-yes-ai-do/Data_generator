package site.yesaido.data_generator.exception;

import java.io.Serial;

public class MqttOperationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MqttOperationException(String message) {
        super(message);
    }

    public MqttOperationException(String message, Throwable cause){
        super(message,cause);
    }
}

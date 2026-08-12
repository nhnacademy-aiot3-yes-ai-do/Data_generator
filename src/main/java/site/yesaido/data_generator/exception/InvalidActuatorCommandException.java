package site.yesaido.data_generator.exception;

import java.io.Serial;
// Rule Engine 요청의 누락 필드나 잘못된 시간 범위 exception
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
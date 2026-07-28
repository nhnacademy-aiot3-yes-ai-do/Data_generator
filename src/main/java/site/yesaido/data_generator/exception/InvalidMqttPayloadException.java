package site.yesaido.data_generator.exception;

import java.io.Serial;

public class InvalidMqttPayloadException extends RuntimeException {
    // 직렬화·역직렬화 시 클래스 버전 호환성을 확인하기 위한 식별자
    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidMqttPayloadException(String message) {
        super(message);
    }
}

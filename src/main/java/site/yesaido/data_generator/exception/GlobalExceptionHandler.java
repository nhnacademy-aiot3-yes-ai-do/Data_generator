package site.yesaido.data_generator.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;
import site.yesaido.data_generator.dto.response.ApiErrorResponse;

import java.time.Clock;
import java.time.Instant;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final String INVALID_INPUT_MESSAGE = "요청 경로 또는 본문 형식이 올바르지 않습니다.";
    private final Clock clock;

    @ExceptionHandler({
            InvalidActuatorCommandException.class,
            ActuatorStateException.class
    })
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(RuntimeException exception) {
        return createErrorResponse(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(ActuatorCommandUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnavailableCommand(
            ActuatorCommandUnavailableException exception
    ) {
        return createErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidWebInput (
            ServerWebInputException exception
    ) {
        return createErrorResponse(HttpStatus.BAD_REQUEST, findInputErrorMessage(exception));
    }

    private static String findInputErrorMessage(ServerWebInputException exception) {
        Throwable currentCause = exception;

        while ( currentCause != null) {
            if( currentCause instanceof InvalidActuatorCommandException
            || currentCause instanceof ActuatorStateException) {
                return currentCause.getMessage();
            }

            currentCause = currentCause.getCause();
        }

        return INVALID_INPUT_MESSAGE;
    }

    private ResponseEntity<ApiErrorResponse> createErrorResponse(HttpStatus httpStatus, String message) {
        ApiErrorResponse apiErrorResponse = new ApiErrorResponse(
                Instant.now(clock), httpStatus.value(), httpStatus.getReasonPhrase(), message
        );
        return ResponseEntity.status(httpStatus).body(apiErrorResponse);
    }
}

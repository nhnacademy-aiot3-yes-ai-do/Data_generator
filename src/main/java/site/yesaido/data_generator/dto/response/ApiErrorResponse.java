package site.yesaido.data_generator.dto.response;

import java.time.Instant;

public record ApiErrorResponse(
        Instant time,
        int status,
        String error,
        String message
) {
}

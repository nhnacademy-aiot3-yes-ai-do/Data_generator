package site.yesaido.data_generator.dto.request;

import site.yesaido.data_generator.domain.ActuatorState;
import site.yesaido.data_generator.exception.InvalidActuatorCommandException;

import java.time.Instant;
import java.util.UUID;

public record ActuatorCommandRequest(
        UUID controlId,
        UUID commandId,
        ActuatorState desiredState,
        Instant requestedAt,
        Instant expiresAt
) {
    public ActuatorCommandRequest {
        requireNonNull(controlId, "controlId");
        requireNonNull(commandId, "commandId");
        requireNonNull(desiredState, "desiredState");
        requireNonNull(requestedAt, "requestedAt");
        requireNonNull(expiresAt, "expiresAt");

        if (!expiresAt.isAfter(requestedAt)) {
            throw new InvalidActuatorCommandException("expiresAt은 requestedAt보다 나중이어야 합니다.");
        }
    }

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new InvalidActuatorCommandException(fieldName + "은 null일 수 없습니다.");
        }
    }
}

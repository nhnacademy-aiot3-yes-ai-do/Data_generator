package site.yesaido.data_generator.dto.response;

import site.yesaido.data_generator.domain.ActuatorCommandStatus;
import site.yesaido.data_generator.domain.ActuatorState;
import site.yesaido.data_generator.exception.InvalidActuatorCommandException;

import java.time.Instant;
import java.util.UUID;

public record ActuatorCommandResponse(
        UUID controlId,
        UUID commandId,
        ActuatorCommandStatus status,
        ActuatorState actualState,
        Instant appliedAt
) {
    public ActuatorCommandResponse {
        requireNonNull(controlId, "controlId");
        requireNonNull(commandId, "commandId");
        requireNonNull(status, "status");
        requireNonNull(actualState, "actualState");

        if (status == ActuatorCommandStatus.APPLIED && appliedAt == null) {
            throw new InvalidActuatorCommandException("APPLIED 응답의 appliedAt은 null일 수 없습니다.");
        }

        if (status != ActuatorCommandStatus.APPLIED && appliedAt != null) {
            throw new InvalidActuatorCommandException("거절 응답의 appliedAt은 null이어야 합니다.");
        }
    }

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new InvalidActuatorCommandException(fieldName + "은 null일 수 없습니다.");
        }
    }
}
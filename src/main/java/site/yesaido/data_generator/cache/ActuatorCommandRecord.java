package site.yesaido.data_generator.cache;

import site.yesaido.data_generator.domain.ActuatorCommandStatus;
import site.yesaido.data_generator.domain.ActuatorStateKey;
import site.yesaido.data_generator.dto.request.ActuatorCommandRequest;
import site.yesaido.data_generator.dto.response.ActuatorCommandResponse;
import site.yesaido.data_generator.exception.InvalidActuatorCommandException;

public record ActuatorCommandRecord(
        ActuatorStateKey actuatorStateKey,
        ActuatorCommandRequest actuatorCommandRequest,
        ActuatorCommandResponse actuatorCommandResponse
) {
    public ActuatorCommandRecord {
        requireNonNull(actuatorStateKey, "actuatorStateKey");
        requireNonNull(actuatorCommandRequest, "actuatorCommandRequest");
        requireNonNull(actuatorCommandResponse, "actuatorCommandResponse");

        if (!actuatorCommandRequest.controlId().equals(actuatorCommandResponse.controlId())) {
            throw new InvalidActuatorCommandException("요청과 응답의 controlId가 일치해야 합니다.");
        }

        if (!actuatorCommandRequest.commandId().equals(actuatorCommandResponse.commandId())) {
            throw new InvalidActuatorCommandException("요청과 응답의 commandId가 일치해야 합니다.");
        }

        if (actuatorCommandResponse.status() == ActuatorCommandStatus.APPLIED
                && actuatorCommandRequest.desiredState() != actuatorCommandResponse.actualState()) {
            throw new InvalidActuatorCommandException("APPLIED 응답의 actualState는 desiredState와 일치해야 합니다.");
        }
    }

    public boolean matchesCommand(
            ActuatorStateKey comparedActuatorStateKey, ActuatorCommandRequest comparedActuatorCommandRequest
    ) {
        return actuatorStateKey.equals(comparedActuatorStateKey) && actuatorCommandRequest.equals(comparedActuatorCommandRequest);
    }

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new InvalidActuatorCommandException(fieldName + "은 null일 수 없습니다.");
        }
    }
}

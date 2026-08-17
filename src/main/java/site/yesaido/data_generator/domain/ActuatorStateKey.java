package site.yesaido.data_generator.domain;

import site.yesaido.data_generator.exception.ActuatorStateException;

public record ActuatorStateKey(
        long cultivationId,
        ActuatorType actuatorType
) {
    public ActuatorStateKey {
        if(cultivationId <= 0) {
            throw new ActuatorStateException("cultivationId는 0보다 커야 합니다.");
        }
        if(actuatorType == null ) {
            throw new ActuatorStateException("actuatorType은 null일 수 없습니다.");
        }
    }
}

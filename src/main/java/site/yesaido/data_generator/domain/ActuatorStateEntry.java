package site.yesaido.data_generator.domain;

import site.yesaido.data_generator.exception.ActuatorStateException;

import java.time.Instant;

public record ActuatorStateEntry(
        ActuatorState actualState,
        Instant lastRequestedAt
) {
    public ActuatorStateEntry {
        if (actualState == null ) {
            throw new ActuatorStateException("actualState는 null일 수 없습니다.");
        }
        if (lastRequestedAt == null) {
            throw new ActuatorStateException("lastRequestedAt는 null일 수 없습니다.");
        }
    }
}

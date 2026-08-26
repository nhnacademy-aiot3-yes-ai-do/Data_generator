package site.yesaido.data_generator.domain;

import site.yesaido.data_generator.exception.SensorDataGenerationException;

// 하나의 공용 환경에서 특정 EUI 센서의 관측 상태를 식별하는 불변 키
public record SensorObservationKey(
        EnvironmentStateKey environmentStateKey,
        String deviceEui
) {

    public SensorObservationKey {
        if (environmentStateKey == null) {
            throw new SensorDataGenerationException("environmentStateKey는 null일 수 없습니다.");
        }

        if (deviceEui == null || deviceEui.isBlank()) {
            throw new SensorDataGenerationException("deviceEui는 null이거나 빈 문자열 또는 공백 문자열일 수 없습니다.");
        }

        deviceEui = deviceEui.strip();
    }
}

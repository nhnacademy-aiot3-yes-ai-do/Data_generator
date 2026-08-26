package site.yesaido.data_generator.domain;

import site.yesaido.data_generator.exception.SensorDataGenerationException;

// 한 센서 채널을 재배 공용 환경에서 생성하기 위해 해석된 불변 계획
public record SensorGenerationPlan(
        EnvironmentStateKey environmentStateKey,
        MeasurementConfiguration measurementConfiguration
) {

    public SensorGenerationPlan {
        if (environmentStateKey == null) {
            throw new SensorDataGenerationException("environmentStateKey는 null일 수 없습니다.");
        }

        if (measurementConfiguration == null) {
            throw new SensorDataGenerationException("measurementConfiguration은 null일 수 없습니다.");
        }
    }
}
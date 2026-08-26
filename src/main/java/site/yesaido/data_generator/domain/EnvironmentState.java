package site.yesaido.data_generator.domain;

import site.yesaido.data_generator.exception.SensorDataGenerationException;

// 특정 생성 주기까지 계산된 재배 공용 환경값
public record EnvironmentState(
        double canonicalValue,
        long lastAdvancedCycleId
) {
    public EnvironmentState {
        if (!Double.isFinite(canonicalValue)) {
            throw new SensorDataGenerationException(
                    "canonicalValue는 유한한 숫자여야 합니다."
            );
        }

        if (lastAdvancedCycleId <= 0) {
            throw new SensorDataGenerationException(
                    "lastAdvancedCycleId는 0보다 커야 합니다."
            );
        }
    }
}

package site.yesaido.data_generator.domain;

import site.yesaido.data_generator.exception.SensorDataGenerationException;

// 특정 EUI 센서가 마지막 생성 주기에 관측한 표준 단위 상태
public record SensorObservationState(
        double canonicalBias,
        double canonicalDeviation,
        double canonicalValue,
        long lastProjectedCycleId
) {

    public SensorObservationState {
        validateFinite(canonicalBias, "canonicalBias");
        validateFinite(canonicalDeviation, "canonicalDeviation");
        validateFinite(canonicalValue, "canonicalValue");

        if (lastProjectedCycleId <= 0) {
            throw new SensorDataGenerationException("lastProjectedCycleId는 0보다 커야 합니다.");
        }
    }

    private static void validateFinite(double value, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new SensorDataGenerationException(fieldName + "는 유한한 숫자여야 합니다.");
        }
    }
}

package site.yesaido.data_generator.domain;

import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.math.BigDecimal;

// 공용 환경값에서 EUI별 센서 관측값을 만들 때 사용하는 불변 정책
public record SensorObservationPolicy(
        BigDecimal maximumBiasRatio,
        BigDecimal maximumDeviationRatio,
        BigDecimal deviationRetentionRatio
) {

    public SensorObservationPolicy {
        maximumBiasRatio = validateInclusiveRatio(maximumBiasRatio, "maximumBiasRatio");
        maximumDeviationRatio = validateInclusiveRatio(maximumDeviationRatio, "maximumDeviationRatio");
        deviationRetentionRatio = validateRetentionRatio(deviationRetentionRatio);
    }

    private static BigDecimal validateInclusiveRatio(BigDecimal ratio, String fieldName) {
        if (ratio == null) {
            throw new SensorDataGenerationException(fieldName + "는 null일 수 없습니다.");
        }

        if (ratio.compareTo(BigDecimal.ZERO) < 0 || ratio.compareTo(BigDecimal.ONE) > 0) {
            throw new SensorDataGenerationException(fieldName + "는 0 이상 1 이하여야 합니다.");
        }

        return ratio.stripTrailingZeros();
    }

    private static BigDecimal validateRetentionRatio(BigDecimal ratio) {
        if (ratio == null) {
            throw new SensorDataGenerationException("deviationRetentionRatio는 null일 수 없습니다.");
        }

        if (ratio.compareTo(BigDecimal.ZERO) < 0 || ratio.compareTo(BigDecimal.ONE) >= 0) {
            throw new SensorDataGenerationException("deviationRetentionRatio는 0 이상 1 미만이어야 합니다.");
        }

        return ratio.stripTrailingZeros();
    }
}

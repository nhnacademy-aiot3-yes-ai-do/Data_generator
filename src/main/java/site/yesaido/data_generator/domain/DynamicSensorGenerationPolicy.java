package site.yesaido.data_generator.domain;

import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.math.BigDecimal;

// 임계값 범위를 동적 센서 생성 범위와 변화량으로 변환할 때 사용하는 불변 정책
public record DynamicSensorGenerationPolicy(
        BigDecimal rangeExpansionRatio,
        BigDecimal maximumChangeRatio,
        int decimalPlaces
) {

    public DynamicSensorGenerationPolicy {
        if (rangeExpansionRatio == null) {
            throw new SensorDataGenerationException("rangeExpansionRatio는 null일 수 없습니다.");
        }
        if (maximumChangeRatio == null) {
            throw new SensorDataGenerationException("maximumChangeRatio는 null일 수 없습니다.");
        }

        if( rangeExpansionRatio.compareTo(BigDecimal.ZERO) < 0 || rangeExpansionRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new SensorDataGenerationException("rangeExpansionRatio는 0 이상 1 이하여야 합니다.");
        }
        if( maximumChangeRatio.compareTo(BigDecimal.ZERO) <= 0 || maximumChangeRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new SensorDataGenerationException("maximumChangeRatio는 0보다 크고 1 이하 여야 합니다.");
        }

        if( decimalPlaces < 0) {
            throw new SensorDataGenerationException("decimalPlaces는 0보다 작을 수 없습니다.");
        }

        rangeExpansionRatio = rangeExpansionRatio.stripTrailingZeros();
        maximumChangeRatio = maximumChangeRatio.stripTrailingZeros();
    }
}

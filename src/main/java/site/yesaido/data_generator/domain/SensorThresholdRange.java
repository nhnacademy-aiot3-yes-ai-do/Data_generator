package site.yesaido.data_generator.domain;

import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.math.BigDecimal;

// 센서 타입·단위에 설정된 최솟값과 최댓값을 표현하는 불변 임계값 범위
public record SensorThresholdRange(
        BigDecimal thresholdMin,
        BigDecimal thresholdMax
) {
    public SensorThresholdRange {
        if(thresholdMin == null) {
            throw new SensorDataGenerationException("thresholdMin은 null일 수 없습니다.");
        }
        if(thresholdMax == null) {
            throw new SensorDataGenerationException("thresholdMax은 null일 수 없습니다.");
        }
        if(thresholdMin.compareTo(thresholdMax) > 0) {
            throw new SensorDataGenerationException("thresholdMin은 thresholdMax보다 클 수 없습니다.");
        }

        thresholdMin = thresholdMin.stripTrailingZeros();
        thresholdMax = thresholdMax.stripTrailingZeros();
    }

    public BigDecimal rangeWidth() {
        return thresholdMax.subtract(thresholdMin);
    }

    public BigDecimal midpoint() {
        return thresholdMin
                .add(thresholdMax)
                .divide(BigDecimal.valueOf(2L));
    }
}

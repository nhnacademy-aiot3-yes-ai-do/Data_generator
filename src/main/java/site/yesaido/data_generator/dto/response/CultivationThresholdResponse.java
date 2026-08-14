package site.yesaido.data_generator.dto.response;


import site.yesaido.data_generator.domain.SensorThresholdKey;
import site.yesaido.data_generator.domain.SensorThresholdRange;
import site.yesaido.data_generator.exception.SensorSynchronizationException;

import java.math.BigDecimal;

// Cultivation Server가 반환하는 재배별 센서 채널 임계값을 표현하는 Feign 응답 DTO
public record CultivationThresholdResponse(
        long cultivationId,
        String sensorType,
        String unit,
        BigDecimal minValue,
        BigDecimal maxValue
) {

    public CultivationThresholdResponse {
        if (cultivationId <= 0) {
            throw new SensorSynchronizationException("cultivationId는 0보다 커야 합니다.");
        }

        sensorType = normalizeRequiredText(sensorType, "sensorType");
        unit = normalizeRequiredText(unit, "unit");

        if (minValue == null) {
            throw new SensorSynchronizationException("minValue는 null일 수 없습니다.");
        }

        if (maxValue == null) {
            throw new SensorSynchronizationException("maxValue는 null일 수 없습니다."
            );
        }

        if (minValue.compareTo(maxValue) > 0) {
            throw new SensorSynchronizationException("minValue는 maxValue보다 클 수 없습니다.");
        }
    }

    public SensorThresholdKey convertToSensorThresholdKey() {
        return new SensorThresholdKey(cultivationId, sensorType, unit);
    }

    public SensorThresholdRange convertToSensorThresholdRange() {
        return new SensorThresholdRange(minValue, maxValue);
    }

    private static String normalizeRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new SensorSynchronizationException(fieldName + "은 null이거나 빈 문자열 또는 공백 문자열일 수 없습니다.");
        }

        return value.strip();
    }
}
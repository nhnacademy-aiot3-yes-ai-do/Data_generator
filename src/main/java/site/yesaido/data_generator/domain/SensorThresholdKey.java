package site.yesaido.data_generator.domain;

import site.yesaido.data_generator.exception.SensorDataGenerationException;

// 재배별 센서 타입·단위 임계값을 식별하는 불변 키
public record SensorThresholdKey(
        long cultivationId,
        String sensorType,
        String unit
) {
    public SensorThresholdKey {
        if ( cultivationId <= 0) {
            throw new SensorDataGenerationException("cultivationId는 0보다 커야 합니다.");
        }

        sensorType = normalizeRequiredText(sensorType, "sensorType");
        unit = normalizeRequiredText(unit,"unit");
    }

    private static String normalizeRequiredText(String value, String fieldName) {
        if(value == null || value.isBlank()) {
            throw new SensorDataGenerationException(fieldName + "은 null이거나 빈 문자열 또는 공백 문자열일 수 없습니다.");
        }

        return value.strip();
    }
}

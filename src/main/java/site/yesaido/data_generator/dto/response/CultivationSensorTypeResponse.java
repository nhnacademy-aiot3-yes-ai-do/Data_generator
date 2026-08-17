package site.yesaido.data_generator.dto.response;

import site.yesaido.data_generator.domain.SensorTypeSpec;
import site.yesaido.data_generator.exception.SensorSynchronizationException;

// Cultivation Server가 반환하는 센서 타입과 단위 한 쌍을 표현하는 Feign 응답 DTO
public record CultivationSensorTypeResponse(
        String sensorType,
        String unit
) {
    public CultivationSensorTypeResponse {
        sensorType = normalizeRequiredText(sensorType, "sensorType");
        unit = normalizeRequiredText(unit, "unit");
    }

    public SensorTypeSpec convertToSensorTypeSpec() {
        return new SensorTypeSpec(sensorType, unit);
    }

    private static String normalizeRequiredText(String value, String fieldName) {
        if( value == null || value.isBlank()) {
            throw new SensorSynchronizationException(fieldName + "은 null이거나 빈 문자열 또는 공백 문자열일 수 없습니다.");
        }

        return value.strip();
    }
}

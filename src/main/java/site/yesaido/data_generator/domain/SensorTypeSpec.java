package site.yesaido.data_generator.domain;

import site.yesaido.data_generator.exception.SensorDataGenerationException;

// 센서 장치가 지원하는 센서 타입과 단위의 조합을 표현하는 값 객체
public record SensorTypeSpec(
        String sensorType,
        String unit
) {

    public SensorTypeSpec {
        sensorType = normalizeRequiredText(sensorType, "sensorType");
        unit = normalizeRequiredText(unit, "unit");
    }

    private static String normalizeRequiredText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new SensorDataGenerationException(
                    fieldName + "은 null이거나 빈 문자열 또는 공백 문자열일 수 없습니다."
            );
        }

        return value.strip();
    }
}

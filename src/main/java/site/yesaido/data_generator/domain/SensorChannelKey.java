package site.yesaido.data_generator.domain;

import site.yesaido.data_generator.exception.SensorDataGenerationException;

// deviceEui, sensorType, unit 조합으로 독립적인 센서 측정 채널을 식별하는 값 객체
public record SensorChannelKey(
        String deviceEui,
        String sensorType,
        String unit
) {

    public SensorChannelKey {
        deviceEui = normalizeRequiredText(deviceEui, "deviceEui");
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

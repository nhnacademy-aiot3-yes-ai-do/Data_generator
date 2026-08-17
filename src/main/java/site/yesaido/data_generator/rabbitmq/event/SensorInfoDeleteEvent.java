package site.yesaido.data_generator.rabbitmq.event;

import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.exception.SensorSynchronizationException;

import java.time.OffsetDateTime;

// Cultivation Server가 전달하는 센서 채널 한 개의 삭제 이벤트
public record SensorInfoDeleteEvent(
        Long cultivationId,
        String deviceEui,
        String sensorType,
        String unit,
        OffsetDateTime occurredAt
) {

    public SensorInfoDeleteEvent {
        if (cultivationId == null || cultivationId <= 0) {
            throw new SensorSynchronizationException("cultivationId는 null일 수 없고 0보다 커야 합니다.");
        }

        deviceEui = normalizeRequiredText(deviceEui, "deviceEui");
        sensorType = normalizeRequiredText(sensorType, "sensorType");
        unit = normalizeRequiredText(unit, "unit");

        if (occurredAt == null) {
            throw new SensorSynchronizationException("occurredAt은 null일 수 없습니다.");
        }
    }

    public SensorChannelKey convertToSensorChannelKey() {
        return new SensorChannelKey(deviceEui, sensorType, unit);
    }

    private static String normalizeRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new SensorSynchronizationException(fieldName + "은 null이거나 빈 문자열 또는 공백 문자열일 수 없습니다.");
        }

        return value.strip();
    }
}
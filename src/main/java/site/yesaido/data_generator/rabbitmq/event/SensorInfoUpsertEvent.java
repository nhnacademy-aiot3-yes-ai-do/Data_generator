package site.yesaido.data_generator.rabbitmq.event;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.domain.SensorTypeSpec;
import site.yesaido.data_generator.exception.SensorSynchronizationException;

import java.time.OffsetDateTime;
import java.util.Set;

// Cultivation Server가 전달하는 센서 채널 한 개의 등록·변경 이벤트
public record SensorInfoUpsertEvent(
        Long cultivationId,
        String location,
        String locationDetail,
        String deviceModel,
        String deviceName,
        String deviceEui,
        String sensorType,
        String unit,
        OffsetDateTime occurredAt
) {

    public SensorInfoUpsertEvent {
        if (cultivationId == null || cultivationId <= 0) {
            throw new SensorSynchronizationException("cultivationId는 null일 수 없고 0보다 커야 합니다.");
        }

        location = normalizeRequiredText(location, "location");
        locationDetail = normalizeRequiredText(locationDetail, "locationDetail");
        deviceModel = normalizeRequiredText(deviceModel, "deviceModel");
        deviceName = normalizeRequiredText(deviceName, "deviceName");
        deviceEui = normalizeRequiredText(deviceEui, "deviceEui");
        sensorType = normalizeRequiredText(sensorType, "sensorType");
        unit = normalizeRequiredText(unit, "unit");

        if (occurredAt == null) {
            throw new SensorSynchronizationException("occurredAt은 null일 수 없습니다.");
        }
    }

    public SensorCacheEntry convertToSensorCacheEntry() {
        return new SensorCacheEntry(
                cultivationId,
                deviceEui,
                deviceName,
                location,
                locationDetail,
                deviceModel,
                Set.of(new SensorTypeSpec(sensorType, unit))
        );
    }

    private static String normalizeRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new SensorSynchronizationException(fieldName + "은 null이거나 빈 문자열 또는 공백 문자열일 수 없습니다.");
        }

        return value.strip();
    }
}
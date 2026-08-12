package site.yesaido.data_generator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import site.yesaido.data_generator.exception.InvalidMqttPayloadException;
import site.yesaido.data_generator.exception.InvalidMqttTopicException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 측정값 + SensorCacheEntry
 *         ↓
 * MqttSensorPayload 생성
 *         ↓
 * JSON 직렬화
 *         ↓
 * byte[] 반환
 *         ↓
 * MQTT 메시지로 발행
 */

/**
 *
 * @param value
 * @param time
 * @param unit
 * @param deviceName
 * @param deviceEui
 */
// MQTT로 발행한 정상 센서측정값과 등록 단위를 표현
public record MqttSensorPayload (
    @JsonProperty("value")
    Number value,

    @JsonProperty("unit")
    String unit,
    
    @JsonProperty("time")
    OffsetDateTime time,

    
    @JsonProperty("device_name")
    String deviceName,
    
    @JsonProperty("device_eui")
    String deviceEui
    )
{

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

public MqttSensorPayload {
    if(value == null) {
        throw new InvalidMqttTopicException("value는 null일 수 없습니다.");
    }

    if (isNonFinite(value)) {
        throw new InvalidMqttPayloadException("value는 유한한 숫자여야 합니다.");
    }

    unit = normalizeRequiredText(unit, "unit");

    if (time == null) {
        throw new InvalidMqttPayloadException("time은 null일 수 없습니다.");
    }

    time = time.withOffsetSameInstant(SEOUL_OFFSET);

    deviceName = normalizeRequiredText(deviceName, "deviceName");
    deviceEui = normalizeRequiredText(deviceEui, "deviceEui");
}

    private static String normalizeRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidMqttPayloadException(fieldName + "은 null이거나 빈 문자열 또는 공백 문자열일 수 없습니다.");
        }

        return value.strip();
    }

    private static boolean isNonFinite(Number value) {
        return value instanceof Double doubleValue && !Double.isFinite(doubleValue)
                || value instanceof Float floatValue && !Float.isFinite(floatValue);
    }
}


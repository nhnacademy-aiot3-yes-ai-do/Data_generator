package site.yesaido.data_generator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import site.yesaido.data_generator.exception.InvalidMqttPayloadException;

import java.time.Instant;

public record MqttSensorPayload (
    @JsonProperty("value")
    Number value,
    
    @JsonProperty("time")
    Instant time,
    
    @JsonProperty("device_name")
    String deviceName,
    
    @JsonProperty("device_eui")
    String deviceEui
    ) {

public MqttSensorPayload {
    if (value == null) {
        throw new InvalidMqttPayloadException("value는 null일 수 없습니다.");
    }

    if (isNonFinite(value)) {
        throw new InvalidMqttPayloadException("value는 유한한 숫자여야 합니다.");
    }

    if (time == null) {
        throw new InvalidMqttPayloadException("time은 null일 수 없습니다.");
    }

    requireText(deviceName, "deviceName");
    requireText(deviceEui, "deviceEui");
}

private static boolean isNonFinite(Number value) {
    return value instanceof Double doubleValue && !Double.isFinite(doubleValue)
            || value instanceof Float floatValue && !Float.isFinite(floatValue);
}

private static void requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
        throw new InvalidMqttPayloadException(fieldName + "은 null이거나 공백일 수 없습니다.");
        }
    }
}
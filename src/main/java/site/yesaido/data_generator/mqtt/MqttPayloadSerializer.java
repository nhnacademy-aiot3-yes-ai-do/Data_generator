package site.yesaido.data_generator.mqtt;

import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.domain.SensorTypeSpec;
import site.yesaido.data_generator.dto.MqttSensorPayload;
import site.yesaido.data_generator.exception.InvalidMqttPayloadException;
import site.yesaido.data_generator.exception.MqttPayloadSerializationException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.cfg.DateTimeFeature;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

// 센서 측정값과 등록 단위를 서울 시간 기준 MQTT JSON으로 직렬화
public class MqttPayloadSerializer {
    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

    private final ObjectWriter mqttPayloadWriter;
    private final Clock clock;

    public MqttPayloadSerializer (ObjectMapper objectMapper, Clock clock) {
        if (objectMapper == null) {
            throw new MqttPayloadSerializationException("objectMapper는 null일 수 없습니다.");
        }
        if ( clock == null) {
            throw new MqttPayloadSerializationException("clock은 null일 수 없습니다.");
        }
        this.mqttPayloadWriter = objectMapper.writer().without(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.clock = clock;
    }

    public byte[] serializePayload(Number value, SensorTypeSpec sensorTypeSpec, SensorCacheEntry sensorCacheEntry) {
        if (sensorTypeSpec == null) {
            throw new InvalidMqttPayloadException("sensorTypeSpec은 null일 수 없습니다.");
        }
        if (sensorCacheEntry == null) {
            throw new InvalidMqttPayloadException("sensorCacheEntry는 null일 수 없습니다.");
        }
        if (!sensorCacheEntry.sensorTypes().contains(sensorTypeSpec)) {
            throw new InvalidMqttPayloadException(
                    "장치가 지원하지 않는 센서 채널입니다. deviceEui=" + sensorCacheEntry.deviceEui()
                            + ", sensorType=" + sensorTypeSpec.sensorType()
                            + ", unit=" + sensorTypeSpec.unit()
            );
        }

        OffsetDateTime measuredAt = OffsetDateTime.ofInstant(clock.instant(), SEOUL_OFFSET);
        MqttSensorPayload mqttSensorPayload = new MqttSensorPayload(value, sensorTypeSpec.unit(), measuredAt, sensorCacheEntry.deviceName(), sensorCacheEntry.deviceEui());

        try {
            return mqttPayloadWriter.writeValueAsBytes(mqttSensorPayload);
        } catch (JacksonException exception) {
            throw new MqttPayloadSerializationException(
                    "MQTT payload 직렬화에 실패했습니다. deviceEui=" + sensorCacheEntry.deviceEui()
                            + ", sensorType=" + sensorTypeSpec.sensorType()
                            + ", unit=" + sensorTypeSpec.unit(), exception
            );
        }


    }
}

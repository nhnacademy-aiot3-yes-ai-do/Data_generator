package site.yesaido.data_generator.mqtt;

import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.dto.MqttSensorPayload;
import site.yesaido.data_generator.exception.InvalidMqttPayloadException;
import site.yesaido.data_generator.exception.MqttPayloadSerializationException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.cfg.DateTimeFeature;

import java.time.Clock;

public class MqttPayloadSerializer {

    private final ObjectWriter mqttPayloadWriter;
    private final Clock clock;

    public MqttPayloadSerializer(ObjectMapper objectMapper, Clock clock){
        if(objectMapper == null){
            throw new MqttPayloadSerializationException("objectMapper는 null일 수 없습니다.");
        }
        if(clock == null){
            throw new MqttPayloadSerializationException("clock은 null일 수 없습니다.");
        }

        this.mqttPayloadWriter = objectMapper.writer()
                .without(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.clock = clock;
    }

    public byte[] serializePayload(Number value, SensorCacheEntry sensorCacheEntry){
        if(sensorCacheEntry == null){
            throw new InvalidMqttPayloadException("sensorCacheEntry는 null일 수 없습니다.");
        }

        MqttSensorPayload mqttSensorPayload = new MqttSensorPayload(value, clock.instant(), sensorCacheEntry.deviceName(), sensorCacheEntry.deviceEui());

        try{
            return mqttPayloadWriter.writeValueAsBytes(mqttSensorPayload);
        }catch (JacksonException exception){
            throw new MqttPayloadSerializationException("MQTT payload 직렬화에 실패했습니다. deviceEui=" + sensorCacheEntry.deviceEui(), exception);
        }
    }

}

package site.yesaido.data_generator.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.yesaido.data_generator.cache.SensorCache;
import site.yesaido.data_generator.domain.MeasurementType;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.exception.SensorDataGenerationException;
import site.yesaido.data_generator.generator.RandomWalkGenerator;
import site.yesaido.data_generator.mqtt.MqttPayloadSerializer;
import site.yesaido.data_generator.mqtt.MqttPublishable;
import site.yesaido.data_generator.mqtt.MqttTopicGenerator;

import java.util.List;
import java.util.concurrent.CompletionStage;

@Slf4j
@Service
@RequiredArgsConstructor
public class CultivationDataGenerationService {

    private final SensorCache sensorCache;
    private final MqttTopicGenerator mqttTopicGenerator;
    private final MqttPayloadSerializer mqttPayloadSerializer;
    private final MqttPublishable mqttPublishable;
    private final RandomWalkGenerator randomWalkGenerator;

    public void generateAndPublishSensorData(long cultivationId, List<SensorCacheEntry> sensorCacheEntries) {
        validateGenerationRequest(cultivationId,sensorCacheEntries);

        try{
            for(SensorCacheEntry sensorCacheEntry : sensorCacheEntries){
                generateAndPublishSensorMeasurements(cultivationId,sensorCacheEntry);
            }
        } finally {
            removeDeletedSensorStates(sensorCacheEntries);
        }

    }

    private void generateAndPublishSensorMeasurements(long cultivationId, SensorCacheEntry sensorCacheEntry) {
        for(MeasurementType measurementType : sensorCacheEntry.measurementTypes()){
            if(!isCurrentSensorEntry(sensorCacheEntry)) {
                return;
            }

            generateAndPublishMeasurement(cultivationId,sensorCacheEntry,measurementType);
        }
    }

    private void generateAndPublishMeasurement(long cultivationId, SensorCacheEntry sensorCacheEntry, MeasurementType measurementType) {
        try {
            Number value = randomWalkGenerator.generateNextValue(sensorCacheEntry.deviceEui(), measurementType);
            String topic = mqttTopicGenerator.generateTopic(sensorCacheEntry, measurementType);
            byte[] payload = mqttPayloadSerializer.serializePayload(value, sensorCacheEntry);

            CompletionStage<Void> publishResult = mqttPublishable.publishMessage(topic, payload);

            publishResult.whenComplete((ignoredResult, exception)-> {
                if(exception != null){
                    log.error("MQTT 비동기 발행 실패. cultivationId={}, deviceEui={}, measurementType={}",cultivationId,sensorCacheEntry.deviceEui(),measurementType, exception);
                }
            });
        }catch ( RuntimeException exception){
            log.error("센서 데이터 생성 또는 발행 요청 실패. cultivationId={}, deviceEui={}, measurementType={}",cultivationId,sensorCacheEntry.deviceEui(),measurementType, exception);
        }
    }


    private boolean isCurrentSensorEntry(SensorCacheEntry sensorCacheEntry){
        return sensorCache.findByDeviceEui(sensorCacheEntry.deviceEui())
                .filter(sensorCacheEntry::equals)
                .isPresent();
    }

    private void removeDeletedSensorStates(List<SensorCacheEntry> sensorCacheEntries){
        for( SensorCacheEntry sensorCacheEntry : sensorCacheEntries) {
            boolean sensorDeleted = sensorCache.findByDeviceEui(sensorCacheEntry.deviceEui()).isEmpty();

            if(sensorDeleted){
                randomWalkGenerator.removeStatesByDeviceEui(sensorCacheEntry.deviceEui());
            }
        }
    }

    private void validateGenerationRequest(long cultivationId, List<SensorCacheEntry> sensorCacheEntries) {
        if(cultivationId <= 0 ) {
            throw new SensorDataGenerationException("cultivationId는 0보다 커야 합니다.");
        }

        if(sensorCacheEntries == null){
            throw new SensorDataGenerationException("sensorCacheEntries는 null일 수 없습니다.");
        }

        for(SensorCacheEntry sensorCacheEntry : sensorCacheEntries){
            if(sensorCacheEntry == null ) {
                throw new SensorDataGenerationException("sensorCacheEntries null이 포함될 수 없습니다.");
            }

            if(sensorCacheEntry.cultivationId() != cultivationId){
                throw new SensorDataGenerationException("서로 다른 cultivation의 센서가 포함되어 있습니다. cultivationId=" + cultivationId);
            }
        }


    }
}

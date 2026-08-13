package site.yesaido.data_generator.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.yesaido.data_generator.cache.SensorCache;
import site.yesaido.data_generator.converter.SensorUnitConverter;
import site.yesaido.data_generator.domain.*;
import site.yesaido.data_generator.exception.SensorDataGenerationException;
import site.yesaido.data_generator.generator.RandomWalkGenerator;
import site.yesaido.data_generator.generator.SensorValueGenerator;
import site.yesaido.data_generator.generator.SensorValueGeneratorRegistry;
import site.yesaido.data_generator.mqtt.MqttPayloadSerializer;
import site.yesaido.data_generator.mqtt.MqttPublishable;
import site.yesaido.data_generator.mqtt.MqttTopicGenerator;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

// 재배별 센서 채널 값을 생성·변환하여 MQTT로 비동기 발행
@Slf4j
@Service
@RequiredArgsConstructor
public class CultivationDataGenerationService {

    private final SensorCache sensorCache;

    private final MqttTopicGenerator mqttTopicGenerator;
    private final MqttPayloadSerializer mqttPayloadSerializer;
    private final MqttPublishable mqttPublishable;

    private final RandomWalkGenerator randomWalkGenerator;
    private final VirtualActuatorService virtualActuatorService;
    private final SensorValueGeneratorRegistry sensorValueGeneratorRegistry;
    private final SensorUnitConverter sensorUnitConverter;

    public void generateAndPublishSensorData(long cultivationId, List<SensorCacheEntry> sensorCacheEntries) {
        validateGenerationRequest(cultivationId,sensorCacheEntries);

        try{
            Set<ActuatorType> activeActuatorTypes = virtualActuatorService.getActiveActuatorTypesSnapshot(cultivationId);

            for(SensorCacheEntry sensorCacheEntry : sensorCacheEntries){
                generateAndPublishSensorMeasurements(cultivationId,sensorCacheEntry,activeActuatorTypes);
            }
        } finally {
            removeDeletedSensorStates(sensorCacheEntries);
        }

    }

    private void generateAndPublishSensorMeasurements(long cultivationId, SensorCacheEntry sensorCacheEntry, Set<ActuatorType> activeActuatorTypes) {
        for(SensorTypeSpec sensorTypeSpec : sensorCacheEntry.sensorTypes()){
            if(!isCurrentSensorEntry(sensorCacheEntry)) {
                return;
            }

            generateAndPublishSensorChannel(cultivationId,sensorCacheEntry,sensorTypeSpec, activeActuatorTypes);
        }
    }

    private void generateAndPublishSensorChannel(long cultivationId, SensorCacheEntry sensorCacheEntry,
                                               SensorTypeSpec sensorTypeSpec, Set<ActuatorType> activeActuatorTypes) {
       try {
           SensorChannelKey sensorChannelKey = new SensorChannelKey(sensorCacheEntry.deviceEui(), sensorTypeSpec.sensorType(),  sensorTypeSpec.unit());
           Optional<SensorValueGenerator> optionalSensorValueGenerator = sensorValueGeneratorRegistry.findBySensorType(sensorTypeSpec.sensorType());

           if(optionalSensorValueGenerator.isEmpty()) {
               log.debug("등록된 센서값 생성기가 없어 MQTT 발행을 건너뜁니다. " + "cultivationId={}, deviceEui={}, sensorType={}, unit={}",
                       cultivationId, sensorCacheEntry.deviceEui(), sensorTypeSpec.sensorType(), sensorTypeSpec.unit());
               return;
           }

           double actuatorEffectAmount = calculateActuatorEffectAmount(activeActuatorTypes, sensorTypeSpec.sensorType());
           Number canonicalValue = optionalSensorValueGenerator.get().generateNextValue(sensorChannelKey,actuatorEffectAmount);
           Optional<Number> optionalConvertedValue = sensorUnitConverter.convertFromCanonical(sensorTypeSpec.sensorType(), sensorTypeSpec.unit(), canonicalValue);

           if (optionalConvertedValue.isEmpty()) {
               log.debug(
                       "지원하지 않는 센서 타입·단위 조합이므로 MQTT 발행을 건너뜁니다. "
                               + "cultivationId={}, deviceEui={}, sensorType={}, unit={}",
                       cultivationId,
                       sensorCacheEntry.deviceEui(),
                       sensorTypeSpec.sensorType(),
                       sensorTypeSpec.unit()
               );
               return;
           }

           Number convertedValue = optionalConvertedValue.get();

           String topic = mqttTopicGenerator.generateTopic(sensorCacheEntry,sensorTypeSpec);
           byte[] payload = mqttPayloadSerializer.serializePayload(convertedValue,sensorTypeSpec,sensorCacheEntry);
           CompletionStage<Void> publishResult = mqttPublishable.publishMessage(topic,payload);

           publishResult.whenComplete((ignoredResult, exception) -> {
                       if (exception != null) {
                           log.error(
                                   "MQTT 비동기 발행 실패. "
                                           + "cultivationId={}, deviceEui={}, sensorType={}, unit={}",
                                   cultivationId,
                                   sensorCacheEntry.deviceEui(),
                                   sensorTypeSpec.sensorType(),
                                   sensorTypeSpec.unit(),
                                   exception
                           );
                       }
                   }
           );
           } catch (RuntimeException exception) {
           log.error("센서 데이터 생성 또는 발행 요청 실패. cultivationId={}, deviceEui={}, sensorType={}, unit={}",
                   cultivationId, sensorCacheEntry.deviceEui(), sensorTypeSpec.sensorType(), sensorTypeSpec.unit(), exception);
       }
    }

    private static double calculateActuatorEffectAmount(Set<ActuatorType> activeActuatorTypes, String sensorType) {
        return activeActuatorTypes.stream()
                .filter(actuatorType -> actuatorType.getTargetSensorType().equals(sensorType))
                .mapToDouble(ActuatorType::getEffectAmount)
                .sum();
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

    private static void validateGenerationRequest(long cultivationId, List<SensorCacheEntry> sensorCacheEntries) {
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

package site.yesaido.data_generator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.yesaido.data_generator.cache.SensorCache;
import site.yesaido.data_generator.cache.SensorThresholdCache;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.domain.SensorThresholdKey;
import site.yesaido.data_generator.domain.SensorThresholdRange;
import site.yesaido.data_generator.domain.SensorTypeSpec;
import site.yesaido.data_generator.exception.SensorSynchronizationException;
import site.yesaido.data_generator.generator.SensorValueGenerationResolver;
import site.yesaido.data_generator.rabbitmq.event.SensorRange;
import site.yesaido.data_generator.rabbitmq.event.ThresholdInfoEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// RabbitMQ 임계값 변경 이벤트를 캐시와 cultivation 생성 상태에 반영합니다.
@Slf4j
@Service
@RequiredArgsConstructor
public class ThresholdInfoEventService {

    private final SensorThresholdCache sensorThresholdCache;
    private final SensorCache sensorCache;
    private final SensorValueGenerationResolver sensorValueGenerationResolver;
    private final VirtualActuatorService virtualActuatorService;

    public void processThresholdEvent(
            ThresholdInfoEvent thresholdInfoEvent
    ) {
        if (thresholdInfoEvent == null) {
            throw new SensorSynchronizationException("thresholdInfoEvent는 null일 수 없습니다.");
        }

        if (thresholdInfoEvent.sensorRangeList().isEmpty()) {
            stopCultivationGeneration(thresholdInfoEvent.cultivationId());

            log.info("빈 임계값 이벤트를 반영하여 cultivation 생성을 중단했습니다. cultivationId={}, occurredAt={}",
                    thresholdInfoEvent.cultivationId(), thresholdInfoEvent.occurredAt());
            return;
        }

        Map<SensorThresholdKey, SensorThresholdRange> thresholdEntries = convertToThresholdEntries(thresholdInfoEvent);

        sensorThresholdCache.upsertAll(thresholdEntries);

        log.info(
                "임계값 Upsert 이벤트를 반영했습니다. cultivationId={}, thresholdCount={}, occurredAt={}",
                thresholdInfoEvent.cultivationId(), thresholdEntries.size(), thresholdInfoEvent.occurredAt());
    }

    private void stopCultivationGeneration(long cultivationId) {
        List<SensorCacheEntry> cultivationSensorEntries = sensorCache.getSnapshot().stream()
                        .filter(sensorCacheEntry -> sensorCacheEntry.cultivationId() == cultivationId)
                        .toList();

        removeSensorGenerationStates(cultivationSensorEntries);

        sensorCache.removeByCultivationId(cultivationId);
        sensorThresholdCache.removeByCultivationId(cultivationId);
        virtualActuatorService.removeCultivationState(cultivationId);
    }

    private void removeSensorGenerationStates(List<SensorCacheEntry> sensorCacheEntries) {
        for (SensorCacheEntry sensorCacheEntry : sensorCacheEntries) {
            for (SensorTypeSpec sensorTypeSpec : sensorCacheEntry.sensorTypes()) {
                SensorChannelKey sensorChannelKey = new SensorChannelKey(
                                sensorCacheEntry.deviceEui(),
                                sensorTypeSpec.sensorType(),
                                sensorTypeSpec.unit()
                        );

                sensorValueGenerationResolver.removeState(sensorChannelKey);
            }
        }
    }

    private static Map<SensorThresholdKey, SensorThresholdRange> convertToThresholdEntries(
            ThresholdInfoEvent thresholdInfoEvent) {
        Map<SensorThresholdKey, SensorThresholdRange> thresholdEntries = new HashMap<>();

        for (SensorRange sensorRange : thresholdInfoEvent.sensorRangeList()) {
            SensorThresholdKey thresholdKey = sensorRange.convertToSensorThresholdKey(thresholdInfoEvent.cultivationId());
            SensorThresholdRange thresholdRange = sensorRange.convertToSensorThresholdRange();
            SensorThresholdRange previousThresholdRange = thresholdEntries.putIfAbsent(thresholdKey, thresholdRange);

            if (previousThresholdRange != null) {
                throw new SensorSynchronizationException("threshold.crud 이벤트에 중복 임계값 키가 존재합니다. cultivationId=%d, sensorType=%s, unit=%s"
                                .formatted(thresholdKey.cultivationId(), thresholdKey.sensorType(), thresholdKey.unit())
                                .strip()
                );
            }
        }

        return Map.copyOf(thresholdEntries);
    }

}

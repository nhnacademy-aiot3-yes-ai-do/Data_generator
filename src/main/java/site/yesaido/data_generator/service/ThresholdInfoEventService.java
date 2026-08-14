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

    private static final int SINGLE_THRESHOLD_UPDATE_COUNT = 1;
    private static final int MINIMUM_THRESHOLD_REPLACEMENT_COUNT = 4;

    public void processThresholdEvent(ThresholdInfoEvent thresholdInfoEvent) {
        if (thresholdInfoEvent == null) {
            throw new SensorSynchronizationException("thresholdInfoEvent는 null일 수 없습니다.");
        }

        int sensorRangeCount = thresholdInfoEvent.sensorRangeList().size();

        if (sensorRangeCount == 0) {
            stopCultivationGeneration(thresholdInfoEvent.cultivationId());

            log.info("빈 임계값 이벤트를 반영하여 cultivation 생성을 중단했습니다. cultivationId={}, occurredAt={}",
                    thresholdInfoEvent.cultivationId(), thresholdInfoEvent.occurredAt());
            return;
        }

        validateSensorRangeCount(thresholdInfoEvent.cultivationId(), sensorRangeCount);

        Map<SensorThresholdKey, SensorThresholdRange> thresholdEntries =
                convertToThresholdEntries(thresholdInfoEvent);

        if (sensorRangeCount == SINGLE_THRESHOLD_UPDATE_COUNT) {
            sensorThresholdCache.upsertAll(thresholdEntries);

            log.info("단건 임계값 수정 이벤트를 반영했습니다. cultivationId={}, thresholdCount={}, occurredAt={}",
                    thresholdInfoEvent.cultivationId(), thresholdEntries.size(), thresholdInfoEvent.occurredAt());
            return;
        }

        sensorThresholdCache.replaceByCultivationId(thresholdInfoEvent.cultivationId(), thresholdEntries);

        log.info("cultivation 임계값 등록 이벤트를 전체 교체 방식으로 반영했습니다. cultivationId={}, thresholdCount={}, occurredAt={}",
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

    private static void validateSensorRangeCount(long cultivationId, int sensorRangeCount) {
        if (sensorRangeCount == SINGLE_THRESHOLD_UPDATE_COUNT || sensorRangeCount >= MINIMUM_THRESHOLD_REPLACEMENT_COUNT) {
            return;
        }

        throw new SensorSynchronizationException(
                "threshold.crud 이벤트의 sensorRangeList 크기는 0, 1 또는 4 이상이어야 합니다. cultivationId=%d, sensorRangeCount=%d"
                        .formatted(cultivationId, sensorRangeCount)
                        .strip()
        );
    }
}

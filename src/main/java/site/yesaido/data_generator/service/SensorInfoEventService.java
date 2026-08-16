package site.yesaido.data_generator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.yesaido.data_generator.cache.SensorCache;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.exception.SensorSynchronizationException;
import site.yesaido.data_generator.generator.SensorValueGenerationResolver;
import site.yesaido.data_generator.rabbitmq.event.SensorInfoDeleteEvent;
import site.yesaido.data_generator.rabbitmq.event.SensorInfoUpsertEvent;

// RabbitMQ 센서 등록·변경·삭제 이벤트를 센서 캐시와 생성 상태에 반영합니다.
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorInfoEventService {

    private final SensorCache sensorCache;
    private final SensorValueGenerationResolver sensorValueGenerationResolver;

    public void processUpsertEvent(
            SensorInfoUpsertEvent sensorInfoUpsertEvent) {
        if (sensorInfoUpsertEvent == null) {
            throw new SensorSynchronizationException(
                    "sensorInfoUpsertEvent는 null일 수 없습니다."
            );
        }

        SensorCacheEntry sensorCacheEntry = sensorInfoUpsertEvent.convertToSensorCacheEntry();
        sensorCache.upsert(sensorCacheEntry);

        log.info("센서 채널 Upsert 이벤트를 반영했습니다. cultivationId={}, deviceEui={}, sensorType={}, unit={}, occurredAt={}",
                sensorInfoUpsertEvent.cultivationId(), sensorInfoUpsertEvent.deviceEui(), sensorInfoUpsertEvent.sensorType(),
                sensorInfoUpsertEvent.unit(), sensorInfoUpsertEvent.occurredAt()
        );
    }

    public void processDeleteEvent(SensorInfoDeleteEvent sensorInfoDeleteEvent) {
        if (sensorInfoDeleteEvent == null) {
            throw new SensorSynchronizationException("sensorInfoDeleteEvent는 null일 수 없습니다.");
        }

        validateCultivationOwnership(sensorInfoDeleteEvent);

        SensorChannelKey sensorChannelKey = sensorInfoDeleteEvent.convertToSensorChannelKey();

        sensorCache.removeChannel(sensorChannelKey);

        sensorValueGenerationResolver.removeState(sensorChannelKey);

        log.info("센서 채널 Delete 이벤트를 반영했습니다. cultivationId={}, deviceEui={}, sensorType={}, unit={}, occurredAt={}",
                sensorInfoDeleteEvent.cultivationId(), sensorInfoDeleteEvent.deviceEui(), sensorInfoDeleteEvent.sensorType(),
                sensorInfoDeleteEvent.unit(), sensorInfoDeleteEvent.occurredAt()
        );
    }

    private void validateCultivationOwnership(SensorInfoDeleteEvent sensorInfoDeleteEvent) {
        sensorCache.findByDeviceEui(sensorInfoDeleteEvent.deviceEui())
                .filter(sensorCacheEntry -> sensorCacheEntry.cultivationId() != sensorInfoDeleteEvent.cultivationId())
                .ifPresent(sensorCacheEntry -> {throw new SensorSynchronizationException(
                                    "삭제 이벤트의 cultivationId가 현재 센서 소속과 다릅니다. deviceEui=%s, currentCultivationId=%d, eventCultivationId=%d"
                                            .formatted(sensorInfoDeleteEvent.deviceEui(),
                                                    sensorCacheEntry.cultivationId(),
                                                    sensorInfoDeleteEvent.cultivationId()).strip()
                            );
                        }
                );
    }
}

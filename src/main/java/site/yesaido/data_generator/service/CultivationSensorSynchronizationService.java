package site.yesaido.data_generator.service;



import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.yesaido.data_generator.cache.SensorCache;
import site.yesaido.data_generator.cache.SensorThresholdCache;
import site.yesaido.data_generator.client.CultivationSensorReadable;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.domain.SensorThresholdKey;
import site.yesaido.data_generator.domain.SensorThresholdRange;
import site.yesaido.data_generator.dto.response.CultivationSensorResponse;
import site.yesaido.data_generator.dto.response.CultivationSnapshotResponse;
import site.yesaido.data_generator.dto.response.CultivationThresholdResponse;
import site.yesaido.data_generator.exception.SensorSynchronizationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CultivationSensorSynchronizationService {

    private final CultivationSensorReadable cultivationSensorReadable;
    private final SensorCache sensorCache;
    private final SensorThresholdCache sensorThresholdCache;

    public void synchronizeAllSensors() {
        try {
            CultivationSnapshotResponse cultivationSnapshotResponse = cultivationSensorReadable.getCultivationSnapshot();

            if (cultivationSnapshotResponse == null) {
                throw new SensorSynchronizationException("Cultivation Service의 snapshot 응답은 null일 수 없습니다.");
            }

            List<SensorCacheEntry> sensorCacheEntries =
                    cultivationSnapshotResponse.sensors()
                            .stream()
                            .map(CultivationSensorSynchronizationService::convertToSensorCacheEntry)
                            .toList();

            validateUniqueDeviceEuis(sensorCacheEntries);

            Map<SensorThresholdKey, SensorThresholdRange> thresholdEntries =
                    convertToThresholdEntries(cultivationSnapshotResponse.thresholds());

            sensorThresholdCache.replaceAll(thresholdEntries);
            sensorCache.replaceAll(sensorCacheEntries);

            log.info(
                    """
                    Cultivation Service snapshot으로 센서와 임계값 캐시를 \
                    초기화했습니다. snapshotAt={}, sensorCount={}, thresholdCount={}
                    """,
                    cultivationSnapshotResponse.snapshotAt(),
                    sensorCacheEntries.size(),
                    thresholdEntries.size()
            );
        } catch (SensorSynchronizationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SensorSynchronizationException(
                    "Cultivation Service snapshot 동기화에 실패했습니다.",
                    exception
            );
        }
    }

    private static SensorCacheEntry convertToSensorCacheEntry(
            CultivationSensorResponse cultivationSensorResponse
    ) {
        if (cultivationSensorResponse == null) {
            throw new SensorSynchronizationException(
                    "snapshot의 sensors에 null 응답이 포함될 수 없습니다."
            );
        }

        return cultivationSensorResponse.convertToSensorCacheEntry();
    }

    private static Map<SensorThresholdKey, SensorThresholdRange> convertToThresholdEntries(
            List<CultivationThresholdResponse> cultivationThresholdResponses) {
        Map<SensorThresholdKey, SensorThresholdRange> thresholdEntries =
                new HashMap<>();

        for (CultivationThresholdResponse cultivationThresholdResponse : cultivationThresholdResponses) {
            if (cultivationThresholdResponse == null) {
                throw new SensorSynchronizationException(
                        "snapshot의 thresholds에 null 응답이 포함될 수 없습니다."
                );
            }

            SensorThresholdKey thresholdKey = cultivationThresholdResponse.convertToSensorThresholdKey();
            SensorThresholdRange thresholdRange = cultivationThresholdResponse.convertToSensorThresholdRange();

            SensorThresholdRange previousThresholdRange = thresholdEntries.putIfAbsent(thresholdKey, thresholdRange);

            if (previousThresholdRange != null) {
                throw new SensorSynchronizationException("""
                        snapshot에 중복 임계값 키가 존재합니다. \
                        cultivationId=%d, sensorType=%s, unit=%s
                        """
                        .formatted(thresholdKey.cultivationId(), thresholdKey.sensorType(), thresholdKey.unit()).strip()
                );
            }
        }

        return Map.copyOf(thresholdEntries);
    }

    private static void validateUniqueDeviceEuis(List<SensorCacheEntry> sensorCacheEntries) {
        Map<String, Long> cultivationIdByDeviceEui = new HashMap<>();

        for (SensorCacheEntry sensorCacheEntry : sensorCacheEntries) {
            Long existingCultivationId = cultivationIdByDeviceEui.putIfAbsent(
                            sensorCacheEntry.deviceEui(),
                            sensorCacheEntry.cultivationId()
                    );

            if (existingCultivationId != null) {
                throw new SensorSynchronizationException("""
                        snapshot에 중복 deviceEui가 존재합니다. \
                        deviceEui=%s, cultivationIds=%d,%d
                        """
                        .formatted(sensorCacheEntry.deviceEui(), existingCultivationId, sensorCacheEntry.cultivationId()).strip()
                );
            }
        }
    }
}

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
import site.yesaido.data_generator.domain.SensorTypeSpec;
import site.yesaido.data_generator.dto.response.DataGeneratorSensorResponse;
import site.yesaido.data_generator.dto.response.DataGeneratorSnapshotResponse;
import site.yesaido.data_generator.dto.response.DataGeneratorThresholdResponse;
import site.yesaido.data_generator.exception.SensorSynchronizationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CultivationSensorSynchronizationService { // 초기 센서/임계값 동기화 서비스

    private final CultivationSensorReadable cultivationSensorReadable;
    private final SensorCache sensorCache;
    private final SensorThresholdCache sensorThresholdCache;

    public void synchronizeAllSensors() {
        try {
            DataGeneratorSnapshotResponse snapshot = cultivationSensorReadable.getSnapshot();

            if (snapshot == null) {
                throw new SensorSynchronizationException("Cultivation Service의 snapshot 응답은 null일 수 없습니다.");
            }

            List<SensorCacheEntry> sensorCacheEntries = toSensorCacheEntries(snapshot.sensors());
            sensorCache.replaceAll(sensorCacheEntries);

            Map<SensorThresholdKey, SensorThresholdRange> thresholdEntries = toThresholdEntries(snapshot.thresholds());
            sensorThresholdCache.replaceAll(thresholdEntries);

            log.info("Cultivation Service의 snapshot으로 캐시를 초기화 했습니다. sensorCount={}, thresholdCount={}",
                    sensorCacheEntries.size(), thresholdEntries.size());
        } catch (SensorSynchronizationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SensorSynchronizationException("Cultivation Service snapshot 동기화에 실패했습니다.", exception);
        }
    }

    private List<SensorCacheEntry> toSensorCacheEntries(List<DataGeneratorSensorResponse> sensors) {
        if (sensors == null) {
            throw new SensorSynchronizationException("snapshot의 sensors는 null일 수 없습니다.");
        }

        return sensors.stream()
                .map(this::convertToSensorCacheEntry)
                .toList();
    }

    private SensorCacheEntry convertToSensorCacheEntry(DataGeneratorSensorResponse sensor) {
        if (sensor == null) {
            throw new SensorSynchronizationException("snapshot의 sensors에 null 응답이 포함되어 있을 수 없습니다.");
        }

        Set<SensorTypeSpec> sensorTypeSpecs = sensor.sensorTypes().stream()
                .map(type -> new SensorTypeSpec(type.sensorType(), type.unit()))
                .collect(Collectors.toUnmodifiableSet());

        return new SensorCacheEntry(
                sensor.cultivationId(),
                sensor.deviceEui(),
                sensor.deviceName(),
                sensor.location(),
                sensor.locationDetail(),
                sensor.deviceModel(),
                sensorTypeSpecs
        );
    }

    private Map<SensorThresholdKey, SensorThresholdRange> toThresholdEntries(List<DataGeneratorThresholdResponse> thresholds) {
        if (thresholds == null) {
            throw new SensorSynchronizationException("snapshot의 thresholds는 null일 수 없습니다.");
        }

        Map<SensorThresholdKey, SensorThresholdRange> thresholdEntries = new HashMap<>();

        for (DataGeneratorThresholdResponse threshold : thresholds) {
            if (threshold == null) {
                throw new SensorSynchronizationException("snapshot의 thresholds에 null 응답이 포함되어 있을 수 없습니다.");
            }

            SensorThresholdKey key = new SensorThresholdKey(threshold.cultivationId(), threshold.sensorType(), threshold.unit());
            SensorThresholdRange range = new SensorThresholdRange(threshold.minValue(), threshold.maxValue());

            SensorThresholdRange previous = thresholdEntries.putIfAbsent(key, range);
            if (previous != null) {
                throw new SensorSynchronizationException("snapshot의 thresholds에 중복된 임계값 키가 포함되어 있습니다: " + key);
            }
        }

        return thresholdEntries;
    }
}
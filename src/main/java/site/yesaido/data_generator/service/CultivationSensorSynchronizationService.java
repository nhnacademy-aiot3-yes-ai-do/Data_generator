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
import site.yesaido.data_generator.dto.response.CultivationSensorTypeResponse;
import site.yesaido.data_generator.dto.response.DataGeneratorSensorResponse;
import site.yesaido.data_generator.dto.response.DataGeneratorSnapshotResponse;
import site.yesaido.data_generator.dto.response.DataGeneratorThresholdResponse;
import site.yesaido.data_generator.exception.SensorSynchronizationException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CultivationSensorSynchronizationService {

    private final CultivationSensorReadable cultivationSensorReadable;
    private final SensorCache sensorCache;
    private final SensorThresholdCache sensorThresholdCache;

    public void synchronizeAllSensors() {
        try {
            DataGeneratorSnapshotResponse snapshot = cultivationSensorReadable.getSnapshot();

            validateSnapshot(snapshot);

            List<SensorCacheEntry> sensorCacheEntries = toSensorCacheEntries(snapshot.sensors());

            validateUniqueDeviceEuis(sensorCacheEntries);

            Map<SensorThresholdKey, SensorThresholdRange> thresholdEntries = toThresholdEntries(snapshot.thresholds());

            sensorThresholdCache.replaceAll(thresholdEntries);
            sensorCache.replaceAll(sensorCacheEntries);

            log.info("Cultivation Service의 snapshot으로 캐시를 초기화했습니다. snapshotAt={}, sensorCount={}, thresholdCount={}",
                    snapshot.snapshotAt(), sensorCacheEntries.size(), thresholdEntries.size()
            );
        } catch (SensorSynchronizationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SensorSynchronizationException(
                    "Cultivation Service snapshot 동기화에 실패했습니다.", exception);
        }
    }

    private static void validateSnapshot(DataGeneratorSnapshotResponse snapshot) {
        if (snapshot == null) {
            throw new SensorSynchronizationException("Cultivation Service의 snapshot 응답은 null일 수 없습니다.");
        }

        if (snapshot.snapshotAt() == null) {
            throw new SensorSynchronizationException("snapshotAt은 null일 수 없습니다.");
        }
    }

    private List<SensorCacheEntry> toSensorCacheEntries(
            List<DataGeneratorSensorResponse> sensors
    ) {
        if (sensors == null) {
            throw new SensorSynchronizationException("snapshot의 sensors는 null일 수 없습니다.");
        }

        return sensors.stream()
                .map(this::convertToSensorCacheEntry)
                .toList();
    }

    private SensorCacheEntry convertToSensorCacheEntry(DataGeneratorSensorResponse sensor) {
        if (sensor == null) {
            throw new SensorSynchronizationException("snapshot의 sensors에 null 응답이 포함될 수 없습니다.");
        }

        if (sensor.sensorTypes() == null || sensor.sensorTypes().isEmpty()) {
            throw new SensorSynchronizationException("snapshot 센서의 sensorTypes는 null이거나 비어 있을 수 없습니다. "
                            + "deviceEui=" + sensor.deviceEui());
        }

        Set<SensorTypeSpec> sensorTypeSpecs = new HashSet<>();

        for (CultivationSensorTypeResponse sensorType : sensor.sensorTypes()) {
            if (sensorType == null) {
                throw new SensorSynchronizationException("snapshot 센서의 sensorTypes에 null이 포함될 수 없습니다. "
                                + "deviceEui=" + sensor.deviceEui());
            }

            SensorTypeSpec sensorTypeSpec = new SensorTypeSpec(sensorType.sensorType(), sensorType.unit());

            if (!sensorTypeSpecs.add(sensorTypeSpec)) {
                throw new SensorSynchronizationException(
                        "snapshot 센서에 중복 채널이 포함되어 있습니다. " + "deviceEui=" + sensor.deviceEui()
                                + ", sensorType=" + sensorTypeSpec.sensorType()
                                + ", unit=" + sensorTypeSpec.unit());
            }
        }

        return new SensorCacheEntry(
                sensor.cultivationId(),
                sensor.deviceEui(),
                sensor.deviceName(),
                sensor.location(),
                sensor.locationDetail(),
                sensor.deviceModel(),
                Set.copyOf(sensorTypeSpecs)
        );
    }

    private static void validateUniqueDeviceEuis(List<SensorCacheEntry> sensorCacheEntries) {
        Set<String> deviceEuis = new HashSet<>();

        for (SensorCacheEntry sensorCacheEntry : sensorCacheEntries) {
            if (!deviceEuis.add(sensorCacheEntry.deviceEui())) {
                throw new SensorSynchronizationException("snapshot에 중복된 deviceEui가 포함되어 있습니다. "
                        + "deviceEui=" + sensorCacheEntry.deviceEui()
                );
            }
        }
    }

    private Map<SensorThresholdKey, SensorThresholdRange> toThresholdEntries(
            List<DataGeneratorThresholdResponse> thresholds) {
        if (thresholds == null) {
            throw new SensorSynchronizationException("snapshot의 thresholds는 null일 수 없습니다.");
        }

        Map<SensorThresholdKey, SensorThresholdRange> thresholdEntries = new HashMap<>();

        for (DataGeneratorThresholdResponse threshold : thresholds) {
            if (threshold == null) {
                throw new SensorSynchronizationException("snapshot의 thresholds에 null 응답이 포함될 수 없습니다.");
            }

            SensorThresholdKey thresholdKey = new SensorThresholdKey(
                    threshold.cultivationId(), threshold.sensorType(), threshold.unit());

            SensorThresholdRange thresholdRange = new SensorThresholdRange(
                    threshold.minValue(), threshold.maxValue());

            SensorThresholdRange previousThresholdRange = thresholdEntries.putIfAbsent(
                    thresholdKey, thresholdRange);

            if (previousThresholdRange != null) {
                throw new SensorSynchronizationException("snapshot에 중복된 임계값 키가 포함되어 있습니다: " + thresholdKey);
            }
        }

        return Map.copyOf(thresholdEntries);
    }
}

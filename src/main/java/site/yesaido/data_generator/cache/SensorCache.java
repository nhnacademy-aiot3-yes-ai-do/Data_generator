package site.yesaido.data_generator.cache;

import org.springframework.stereotype.Service;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.domain.SensorTypeSpec;
import site.yesaido.data_generator.exception.SensorCacheException;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/***
 * 1. 현재 불변 Map을 받음
 * 2. 데이터 확인
 * 3. 현재 Map을 수정 가능한 HashMap으로 복사
 * 4. 복사한 Map에 센서 추가 또는 변경
 * 5. 다시 불변 Map으로 만듦
 * 6. AtomicReference가 현재 Map을 교체
 */
/*
 * 센서 캐시는 불변 Map을 AtomicReference로 교체하여
 * 읽는 쪽에서 항상 완성된 센서 정보를 조회할 수 있게 합니다.
 */
@Service
public class SensorCache {

    private final AtomicReference<Map<String, SensorCacheEntry>> sensorEntriesReference = new AtomicReference<>(Map.of());

    private final AtomicBoolean initialSynchronizationCompleted = new AtomicBoolean(false);

    // 장치 메타데이터는 갱신하고 기존 채널과 신규 채널은 합집합으로 병합
    public void upsert(SensorCacheEntry sensorCacheEntry) {
        if (sensorCacheEntry == null) {
            throw new SensorCacheException("sensorCacheEntry는 null일 수 없습니다.");
        }

        sensorEntriesReference.updateAndGet(currentSensorEntries -> {
            SensorCacheEntry currentSensorCacheEntry = currentSensorEntries.get(sensorCacheEntry.deviceEui());

            if (currentSensorCacheEntry == null) {
                Map<String, SensorCacheEntry> updatedSensorEntries = new HashMap<>(currentSensorEntries);
                updatedSensorEntries.put(sensorCacheEntry.deviceEui(), sensorCacheEntry);
                return Map.copyOf(updatedSensorEntries);
            }
            validateSameCultivation(currentSensorCacheEntry, sensorCacheEntry);

            SensorCacheEntry mergedSensorCacheEntry = mergeSensorCacheEntries(currentSensorCacheEntry, sensorCacheEntry);

            if (mergedSensorCacheEntry.equals(currentSensorCacheEntry)) {
                return currentSensorEntries;
            }

            Map<String, SensorCacheEntry> updatedSensorEntries = new HashMap<>(currentSensorEntries);

            updatedSensorEntries.put(mergedSensorCacheEntry.deviceEui(), mergedSensorCacheEntry);

            return Map.copyOf(updatedSensorEntries);
        });
    }

    // 정확한 deviceEui, sensorType, unit 채널 하나만 삭제
    public void removeChannel(SensorChannelKey sensorChannelKey) {
        if (sensorChannelKey == null) {
            throw new SensorCacheException("sensorChannelKey는 null일 수 없습니다.");
        }

        String normalizedDeviceEui = normalizeDeviceEui(sensorChannelKey.deviceEui());

        sensorEntriesReference.updateAndGet(currentSensorEntries -> {
            SensorCacheEntry currentSensorCacheEntry = currentSensorEntries.get(normalizedDeviceEui);

            if (currentSensorCacheEntry == null) {
                return currentSensorEntries;
            }

            Set<SensorTypeSpec> remainingSensorTypes = new HashSet<>(currentSensorCacheEntry.sensorTypes());

            boolean channelRemoved = remainingSensorTypes.removeIf(
                    sensorTypeSpec ->
                            sensorTypeSpec.sensorType().equals(sensorChannelKey.sensorType())
                                    && sensorTypeSpec.unit().equals(sensorChannelKey.unit())
            );

            if (!channelRemoved) {
                return currentSensorEntries;
            }

            Map<String, SensorCacheEntry> updatedSensorEntries = new HashMap<>(currentSensorEntries);

            if (remainingSensorTypes.isEmpty()) {
                updatedSensorEntries.remove(normalizedDeviceEui);
            } else {
                SensorCacheEntry updatedSensorCacheEntry = copyWithSensorTypes(currentSensorCacheEntry, remainingSensorTypes);

                updatedSensorEntries.put(normalizedDeviceEui, updatedSensorCacheEntry);
            }

            return Map.copyOf(updatedSensorEntries);
        });
    }

    // 장치와 장치에 속한 모든 채널을 함께 삭제
    public void removeByDeviceEui(String deviceEui) {
        String normalizedDeviceEui = normalizeDeviceEui(deviceEui);

        sensorEntriesReference.updateAndGet(currentSensorEntries -> {
            if (!currentSensorEntries.containsKey(normalizedDeviceEui)) {
                return currentSensorEntries;
            }

            Map<String, SensorCacheEntry> updatedSensorEntries = new HashMap<>(currentSensorEntries);

            updatedSensorEntries.remove(normalizedDeviceEui);

            return Map.copyOf(updatedSensorEntries);
        });
    }

    // 재배 종료 시 해당 cultivation의 모든 센서 장치를 한 번에 제거합니다.
    public List<SensorCacheEntry> removeByCultivationId(long cultivationId) {
        validateCultivationId(cultivationId);

        Map<String, SensorCacheEntry> previousSensorEntries =
                sensorEntriesReference.getAndUpdate(currentSensorEntries -> {
                            Map<String, SensorCacheEntry> updatedSensorEntries = new HashMap<>(currentSensorEntries);

                            boolean sensorRemoved = updatedSensorEntries.entrySet()
                                    .removeIf(sensorEntry -> sensorEntry
                                            .getValue().cultivationId() == cultivationId);

                            if (!sensorRemoved) {
                                return currentSensorEntries;
                            }

                            return Map.copyOf(updatedSensorEntries);
                        }
                );

        return previousSensorEntries.values().stream()
                .filter(sensorCacheEntry -> sensorCacheEntry.cultivationId() == cultivationId)
                .toList();
    }

    // Feign으로 조회한 전체 센서 목록을 한 번에 교체
    public void replaceAll(Collection<SensorCacheEntry> sensorCacheEntries) {
        if (sensorCacheEntries == null) {
            throw new SensorCacheException("sensorCacheEntries는 null일 수 없습니다.");
        }

        Map<String, SensorCacheEntry> replacementSensorEntries = new HashMap<>();

        for (SensorCacheEntry sensorCacheEntry : sensorCacheEntries) {
            if (sensorCacheEntry == null) {
                throw new SensorCacheException("sensorCacheEntries에 null이 포함될 수 없습니다.");
            }

            SensorCacheEntry previousSensorCacheEntry = replacementSensorEntries.putIfAbsent(sensorCacheEntry.deviceEui(), sensorCacheEntry);

            if (previousSensorCacheEntry != null) {
                throw new SensorCacheException("중복된 deviceEui입니다: " + sensorCacheEntry.deviceEui());
            }
        }

        sensorEntriesReference.set(Map.copyOf(replacementSensorEntries));

        initialSynchronizationCompleted.set(true);
    }

    public boolean isInitialSynchronizationCompleted() {
        return initialSynchronizationCompleted.get();
    }

    public Optional<SensorCacheEntry> findByDeviceEui(String deviceEui) {
        String normalizedDeviceEui = normalizeDeviceEui(deviceEui);

        return Optional.ofNullable(sensorEntriesReference.get().get(normalizedDeviceEui));
    }

    public List<SensorCacheEntry> getSnapshot() {
        return List.copyOf(sensorEntriesReference.get().values());
    }

    public int getSensorCount() {
        return sensorEntriesReference.get().size();
    }

    private static SensorCacheEntry mergeSensorCacheEntries(
            SensorCacheEntry currentSensorCacheEntry,
            SensorCacheEntry newSensorCacheEntry
    ) {
        Set<SensorTypeSpec> mergedSensorTypes = new HashSet<>(currentSensorCacheEntry.sensorTypes());

        mergedSensorTypes.addAll(newSensorCacheEntry.sensorTypes());

        // 공통 장치 정보는 가장 최근 Upsert로 전달된 값을 사용
        return new SensorCacheEntry(
                newSensorCacheEntry.cultivationId(),
                newSensorCacheEntry.deviceEui(),
                newSensorCacheEntry.deviceName(),
                newSensorCacheEntry.location(),
                newSensorCacheEntry.locationDetail(),
                newSensorCacheEntry.deviceModel(),
                mergedSensorTypes
        );
    }

    private static SensorCacheEntry copyWithSensorTypes(
            SensorCacheEntry sensorCacheEntry,
            Set<SensorTypeSpec> sensorTypes
    ) {
        return new SensorCacheEntry(
                sensorCacheEntry.cultivationId(),
                sensorCacheEntry.deviceEui(),
                sensorCacheEntry.deviceName(),
                sensorCacheEntry.location(),
                sensorCacheEntry.locationDetail(),
                sensorCacheEntry.deviceModel(),
                sensorTypes
        );
    }

    private static void validateCultivationId(long cultivationId) {
        if (cultivationId <= 0) {
            throw new SensorCacheException("cultivationId는 0보다 커야 합니다.");
        }
    }

    private static void validateSameCultivation(
            SensorCacheEntry currentSensorCacheEntry,
            SensorCacheEntry newSensorCacheEntry
    ) {
        if (currentSensorCacheEntry.cultivationId()
                != newSensorCacheEntry.cultivationId()) {
            throw new SensorCacheException(
                    "같은 deviceEui를 다른 cultivation으로 변경할 수 없습니다. " + "deviceEui="
                            + newSensorCacheEntry.deviceEui() + ", currentCultivationId="
                            + currentSensorCacheEntry.cultivationId() + ", requestedCultivationId="
                            + newSensorCacheEntry.cultivationId()
            );
        }
    }

    private static String normalizeDeviceEui(String deviceEui) {
        if (deviceEui == null || deviceEui.isBlank()) {
            throw new SensorCacheException("deviceEui는 null이거나 공백일 수 없습니다.");
        }

        return deviceEui.strip();
    }
}
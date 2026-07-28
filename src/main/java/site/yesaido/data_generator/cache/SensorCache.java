package site.yesaido.data_generator.cache;

import org.springframework.stereotype.Service;
import site.yesaido.data_generator.domain.SensorCacheEntry;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/***
 * 1. 현재 불변 Map을 받음
 * 2. 데이터 확인
 * 3. 현재 Map을 수정 가능한 HashMap으로 복사
 * 4. 복사한 Map에 센서 추가 또는 변경
 * 5. 다시 불변 Map으로 만듦
 * 6. AtomicReference가 현재 Map을 교체
 */
@Service
public class SensorCache {
    //AtomicReference 로 감싸줌으로써 thread-safe
    private final AtomicReference<Map<String, SensorCacheEntry>> sensorEntriesReference = new AtomicReference<>(Map.of());

    public void upsert(SensorCacheEntry sensorCacheEntry) {
        if (sensorCacheEntry == null) {
            throw new IllegalArgumentException("sensorCacheEntry는 null일 수 없습니다.");
        }
        // updateAndGet()은 AtomicReference의 현재 값을 기반으로 새 값을 계산하고, 그 값을 원자적으로 교체한 뒤 새 값을 반환하는 메서드.
        sensorEntriesReference.updateAndGet(currentSensorEntries -> {
            SensorCacheEntry currentSensorCacheEntry =
                    currentSensorEntries.get(sensorCacheEntry.deviceEui());

            if (sensorCacheEntry.equals(currentSensorCacheEntry)) {
                return currentSensorEntries;
            }

            Map<String, SensorCacheEntry> updatedSensorEntries = new HashMap<>(currentSensorEntries);

            updatedSensorEntries.put(sensorCacheEntry.deviceEui(), sensorCacheEntry);

            return Map.copyOf(updatedSensorEntries);
        });
    }

    public void removeByDeviceEui(String deviceEui) {
        validateDeviceEui(deviceEui);

        sensorEntriesReference.updateAndGet(currentSensorEntries -> {
            if (!currentSensorEntries.containsKey(deviceEui)) {
                return currentSensorEntries;
            }

            Map<String, SensorCacheEntry> updatedSensorEntries = new HashMap<>(currentSensorEntries);

            updatedSensorEntries.remove(deviceEui);

            return Map.copyOf(updatedSensorEntries);
        });
    }

    public void replaceAll(Collection<SensorCacheEntry> sensorCacheEntries) {
        if (sensorCacheEntries == null) {
            throw new IllegalArgumentException("sensorCacheEntries는 null일 수 없습니다.");
        }

        Map<String, SensorCacheEntry> replacementSensorEntries = new HashMap<>();

        for (SensorCacheEntry sensorCacheEntry : sensorCacheEntries) {
            if (sensorCacheEntry == null) {
                throw new IllegalArgumentException("sensorCacheEntries에 null이 포함될 수 없습니다.");
            }

            SensorCacheEntry previousSensorCacheEntry = replacementSensorEntries.put(sensorCacheEntry.deviceEui(), sensorCacheEntry);

            if (previousSensorCacheEntry != null) {
                throw new IllegalArgumentException("중복된 deviceEui입니다: " + sensorCacheEntry.deviceEui());
            }
        }

        sensorEntriesReference.set(Map.copyOf(replacementSensorEntries));
    }

    public Optional<SensorCacheEntry> findByDeviceEui(String deviceEui) {
        validateDeviceEui(deviceEui);

        return Optional.ofNullable(sensorEntriesReference.get().get(deviceEui));
    }

    public List<SensorCacheEntry> getSnapshot() {
        return List.copyOf(sensorEntriesReference.get().values());
    }

    public int getSensorCount() {
        return sensorEntriesReference.get().size();
    }

    private static void validateDeviceEui(String deviceEui) {
        if (deviceEui == null || deviceEui.isBlank()) {
            throw new IllegalArgumentException("deviceEui는 null이거나 공백일 수 없습니다.");
        }
    }
}
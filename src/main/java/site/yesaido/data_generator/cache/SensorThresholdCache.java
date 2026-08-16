package site.yesaido.data_generator.cache;

import org.springframework.stereotype.Service;
import site.yesaido.data_generator.domain.SensorThresholdKey;
import site.yesaido.data_generator.domain.SensorThresholdRange;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 변경할 때 현재 불변 Map을 HashMap으로 복사하고,
 * 복사본 수정 후 Map.copyOf()로 다시 불변화합니다.
 * 완성된 Map을 AtomicReference로 한 번에 교체하므로
 * 읽는 스레드는 중간 수정 상태를 보지 않습니다.
 */
@Service
public class SensorThresholdCache {
    private final AtomicReference<Map<SensorThresholdKey, SensorThresholdRange>> thresholdEntriesReference = new AtomicReference<>(Map.of());
    private final AtomicBoolean initialSynchronizationCompleted = new AtomicBoolean(false);

    public void upsert(SensorThresholdKey thresholdKey, SensorThresholdRange thresholdRange) {
        validateThresholdKey(thresholdKey);
        validateThresholdRange(thresholdRange);

        thresholdEntriesReference.updateAndGet(currentThresholdEntries -> {
            SensorThresholdRange currentThresholdRange = currentThresholdEntries.get(thresholdKey);

            if(thresholdRange.equals(currentThresholdRange)) {
                return currentThresholdEntries;
            }

            Map<SensorThresholdKey, SensorThresholdRange> updatedThresholdEntries = new HashMap<>(currentThresholdEntries);

            updatedThresholdEntries.put(thresholdKey, thresholdRange);

            return Map.copyOf(updatedThresholdEntries);
            });
    }

    public void upsertAll(Map<SensorThresholdKey, SensorThresholdRange> thresholdEntries) {
        Map<SensorThresholdKey, SensorThresholdRange> validatedThresholdEntries = copyAndValidateThresholdEntries(thresholdEntries);
        if(validatedThresholdEntries.isEmpty()) {
            return;
        }

        thresholdEntriesReference.updateAndGet(currentThresholdEntries -> {
            Map<SensorThresholdKey, SensorThresholdRange> updatedThresholdEntries = new HashMap<>(currentThresholdEntries);

            updatedThresholdEntries.putAll(validatedThresholdEntries);

            if (updatedThresholdEntries.equals(currentThresholdEntries)){
                return currentThresholdEntries;
            }

            return Map.copyOf(updatedThresholdEntries);
        });
    }

    // 한 cultivation의 기존 임계값을 새로운 임계값 목록으로 원자적으로 교체합니다.
    public void replaceByCultivationId(long cultivationId, Map<SensorThresholdKey, SensorThresholdRange> thresholdEntries) {
        validateCultivationId(cultivationId);

        Map<SensorThresholdKey, SensorThresholdRange> replacementThresholdEntries = copyAndValidateThresholdEntries(thresholdEntries);

        if (replacementThresholdEntries.isEmpty()) {
            throw new SensorDataGenerationException("교체할 thresholdEntries는 비어 있을 수 없습니다.");
        }

        validateThresholdCultivationIds(cultivationId, replacementThresholdEntries);

        thresholdEntriesReference.updateAndGet(currentThresholdEntries -> {
            Map<SensorThresholdKey, SensorThresholdRange> updatedThresholdEntries = new HashMap<>(currentThresholdEntries);

            updatedThresholdEntries.keySet()
                    .removeIf(thresholdKey -> thresholdKey.cultivationId() == cultivationId);

            updatedThresholdEntries.putAll(replacementThresholdEntries);

            if (updatedThresholdEntries.equals(currentThresholdEntries)) {
                return currentThresholdEntries;
            }

            return Map.copyOf(updatedThresholdEntries);
        });
    }

    public void remove(SensorThresholdKey thresholdKey) {
        validateThresholdKey(thresholdKey);

        thresholdEntriesReference.updateAndGet(currentThresholdEntries -> {
            if(!currentThresholdEntries.containsKey(thresholdKey)){
                return currentThresholdEntries;
            }

            Map<SensorThresholdKey, SensorThresholdRange> updatedThresholdEntries = new HashMap<>(currentThresholdEntries);

            updatedThresholdEntries.remove(thresholdKey);

            return Map.copyOf(updatedThresholdEntries);
        });
    }

    // 빈 threshold.crud 이벤트가 지정한 재배의 임계값을 모두 삭제 합니다.
    public void removeByCultivationId(long cultivationId) {
        validateCultivationId(cultivationId);

        thresholdEntriesReference.updateAndGet(currentThresholdEntries -> {
            boolean thresholdExists = currentThresholdEntries.keySet().stream()
                    .anyMatch(thresholdKey -> thresholdKey.cultivationId() == cultivationId);

        if(!thresholdExists) {
            return currentThresholdEntries;
        }
        Map<SensorThresholdKey, SensorThresholdRange> updateThresholdEntries = new HashMap<>(currentThresholdEntries);

        updateThresholdEntries.keySet().removeIf(thresholdKey ->thresholdKey.cultivationId() == cultivationId);

        return Map.copyOf(updateThresholdEntries);

        });

    }

    public void replaceAll(Map<SensorThresholdKey,SensorThresholdRange> thresholdEntries) {


        Map<SensorThresholdKey, SensorThresholdRange> replacementThresholdEntries = copyAndValidateThresholdEntries(thresholdEntries);

        thresholdEntriesReference.set(replacementThresholdEntries);
        initialSynchronizationCompleted.set(true);
    }



    public Optional<SensorThresholdRange> find(SensorThresholdKey thresholdKey) {
        validateThresholdKey(thresholdKey);

        return Optional.ofNullable(thresholdEntriesReference.get().get(thresholdKey));
    }

    public Map<SensorThresholdKey, SensorThresholdRange> getSnapshot() {
        return thresholdEntriesReference.get();
    }

    public boolean isInitialSynchronizationCompleted(){
        return initialSynchronizationCompleted.get();
    }


    public int getThresholdCount() {
        return thresholdEntriesReference.get().size();
    }

    private static Map<SensorThresholdKey, SensorThresholdRange> copyAndValidateThresholdEntries(
            Map<SensorThresholdKey, SensorThresholdRange> thresholdEntries) {
        if(thresholdEntries == null) {
            throw new SensorDataGenerationException("thresholdEntries는 null일 수 없습니다.");
        }
        Map<SensorThresholdKey, SensorThresholdRange> validateThresholdEntries = new HashMap<>();
        for(Map.Entry<SensorThresholdKey, SensorThresholdRange> thresholdEntry : thresholdEntries.entrySet()) {
            SensorThresholdKey sensorThresholdKey = thresholdEntry.getKey();
            SensorThresholdRange sensorThresholdRange = thresholdEntry.getValue();

            validateThresholdKey(sensorThresholdKey);
            validateThresholdRange(sensorThresholdRange);

            validateThresholdEntries.put(sensorThresholdKey, sensorThresholdRange);
        }
        return Map.copyOf(validateThresholdEntries);
    }

    private static void validateCultivationId(long cultivationId) {
        if(cultivationId <= 0) {
            throw new SensorDataGenerationException("cultivationId는 0보다 커야 합니다.");
        }
    }

    private static void validateThresholdCultivationIds(long cultivationId, Map<SensorThresholdKey, SensorThresholdRange> thresholdEntries) {
        for (SensorThresholdKey thresholdKey : thresholdEntries.keySet()) {
            if (thresholdKey.cultivationId() != cultivationId) {
                throw new SensorDataGenerationException(
                        "교체할 임계값 키의 cultivationId가 요청값과 일치하지 않습니다. requestedCultivationId=%d, thresholdCultivationId=%d"
                                .formatted(cultivationId, thresholdKey.cultivationId())
                                .strip()
                );
            }
        }
    }

    private static void validateThresholdKey(SensorThresholdKey sensorThresholdKey) {
        if( sensorThresholdKey == null) {
            throw new SensorDataGenerationException("thresholdKey는 null일 수 없습니다.");
        }
    }

    private static void validateThresholdRange(SensorThresholdRange sensorThresholdRange) {
        if(sensorThresholdRange == null) {
            throw new SensorDataGenerationException("thresholdRange는 null일 수 없습니다.");
        }
    }
}

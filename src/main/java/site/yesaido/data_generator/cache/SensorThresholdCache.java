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

    public void replaceAll(Map<SensorThresholdKey,SensorThresholdRange> thresholdEntries) {
        if(thresholdEntries == null) {
            throw new SensorDataGenerationException("thresholdEntries는 null일 수 없습니다.");
        }

        Map<SensorThresholdKey, SensorThresholdRange> replacementThresholdEntries = new HashMap<>();

        for (Map.Entry<SensorThresholdKey,SensorThresholdRange> thresholdEntry : thresholdEntries.entrySet()) {
            SensorThresholdKey thresholdKey = thresholdEntry.getKey();
            SensorThresholdRange thresholdRange = thresholdEntry.getValue();

            validateThresholdKey(thresholdKey);
            validateThresholdRange(thresholdRange);

            replacementThresholdEntries.put(thresholdKey,thresholdRange);
        }

        Map<SensorThresholdKey,SensorThresholdRange> immutableThresholdEntries = Map.copyOf(replacementThresholdEntries);
        thresholdEntriesReference.set(immutableThresholdEntries);
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

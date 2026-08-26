package site.yesaido.data_generator.scheduler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.yesaido.data_generator.cache.SensorCache;
import site.yesaido.data_generator.cache.SensorThresholdCache;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.generator.GenerationCycleSequence;
import site.yesaido.data_generator.service.CultivationTaskCoordinator;

@Slf4j
@Component
@RequiredArgsConstructor
public class SensorDataGenerationScheduler {

    private final SensorCache sensorCache;
    private final SensorThresholdCache sensorThresholdCache;
    private final CultivationTaskCoordinator cultivationTaskCoordinator;
    private final GenerationCycleSequence generationCycleSequence;

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.SECONDS)
    public void scheduleSensorDataGeneration() {
        if (!isInitialSynchronizationCompleted()) {
            return;
        }

        List<SensorCacheEntry> sensorCacheEntries = sensorCache.getSnapshot();

        if (sensorCacheEntries.isEmpty()) {
            return;
        }

        Map<Long, List<SensorCacheEntry>> sensorCacheEntriesByCultivationId =
                sensorCacheEntries.stream()
                        .collect(Collectors.groupingBy(
                                SensorCacheEntry::cultivationId,
                                Collectors.toUnmodifiableList()
                        ));

        long cycleId = generationCycleSequence.nextCycleId();

        for (Map.Entry<Long, List<SensorCacheEntry>> cultivationEntry : sensorCacheEntriesByCultivationId.entrySet()) {
            submitGenerationTask(cultivationEntry.getKey(), cultivationEntry.getValue(), cycleId);
        }
    }

    private boolean isInitialSynchronizationCompleted() {
        return sensorCache.isInitialSynchronizationCompleted() && sensorThresholdCache.isInitialSynchronizationCompleted();
    }

    private void submitGenerationTask(long cultivationId, List<SensorCacheEntry> sensorCacheEntries, long cycleId) {
        try {
            cultivationTaskCoordinator.submitGenerationTask(cultivationId, sensorCacheEntries, cycleId);
        } catch (RuntimeException exception) {
            log.error("cultivation 데이터 생성 작업 제출 실패. cultivationId={}, cycleId={}", cultivationId, cycleId, exception);
        }
    }
}

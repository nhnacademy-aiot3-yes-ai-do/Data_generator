package site.yesaido.data_generator.scheduler;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.data_generator.cache.SensorCache;
import site.yesaido.data_generator.cache.SensorThresholdCache;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.domain.SensorTypeSpec;
import site.yesaido.data_generator.generator.GenerationCycleSequence;
import site.yesaido.data_generator.service.CultivationTaskCoordinator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensorDataGenerationSchedulerTest {

    @Mock
    private SensorCache sensorCache;

    @Mock
    private SensorThresholdCache sensorThresholdCache;

    @Mock
    private CultivationTaskCoordinator cultivationTaskCoordinator;

    @Mock
    private GenerationCycleSequence generationCycleSequence;

    private SensorDataGenerationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SensorDataGenerationScheduler(
                sensorCache,
                sensorThresholdCache,
                cultivationTaskCoordinator,
                generationCycleSequence
        );
    }

    @Test
    @DisplayName("센서 캐시 초기 동기화가 완료되지 않으면 주기를 발급하지 않는다")
    void skipWhenSensorCacheSynchronizationIsIncomplete() {
        when(sensorCache.isInitialSynchronizationCompleted())
                .thenReturn(false);

        scheduler.scheduleSensorDataGeneration();

        verify(sensorCache).isInitialSynchronizationCompleted();
        verify(sensorThresholdCache, never())
                .isInitialSynchronizationCompleted();
        verify(sensorCache, never()).getSnapshot();
        verifyNoInteractions(
                cultivationTaskCoordinator,
                generationCycleSequence
        );
    }

    @Test
    @DisplayName("임계값 캐시 초기 동기화가 완료되지 않으면 주기를 발급하지 않는다")
    void skipWhenThresholdCacheSynchronizationIsIncomplete() {
        when(sensorCache.isInitialSynchronizationCompleted())
                .thenReturn(true);
        when(sensorThresholdCache.isInitialSynchronizationCompleted())
                .thenReturn(false);

        scheduler.scheduleSensorDataGeneration();

        verify(sensorCache, never()).getSnapshot();
        verifyNoInteractions(
                cultivationTaskCoordinator,
                generationCycleSequence
        );
    }

    @Test
    @DisplayName("동기화된 센서 snapshot이 비어 있으면 주기를 발급하지 않는다")
    void skipWhenSensorSnapshotIsEmpty() {
        completeInitialSynchronization();
        when(sensorCache.getSnapshot()).thenReturn(List.of());

        scheduler.scheduleSensorDataGeneration();

        verify(generationCycleSequence, never()).nextCycleId();
        verifyNoInteractions(cultivationTaskCoordinator);
    }

    @Test
    @DisplayName("한 tick의 모든 cultivation에 같은 주기를 전달하고 다음 tick에는 새 주기를 전달한다")
    void shareOneCycleWithinTickAndAdvanceOnNextTick() {
        SensorCacheEntry firstCultivationFirstSensor =
                cacheEntry(1L, "device-A");
        SensorCacheEntry secondCultivationSensor =
                cacheEntry(2L, "device-B");
        SensorCacheEntry firstCultivationSecondSensor =
                cacheEntry(1L, "device-C");

        completeInitialSynchronization();
        when(sensorCache.getSnapshot()).thenReturn(List.of(
                firstCultivationFirstSensor,
                secondCultivationSensor,
                firstCultivationSecondSensor
        ));
        when(generationCycleSequence.nextCycleId())
                .thenReturn(41L, 42L);

        scheduler.scheduleSensorDataGeneration();
        scheduler.scheduleSensorDataGeneration();

        verify(generationCycleSequence, times(2)).nextCycleId();

        List<SensorCacheEntry> firstCultivationEntries = List.of(
                firstCultivationFirstSensor,
                firstCultivationSecondSensor
        );
        List<SensorCacheEntry> secondCultivationEntries =
                List.of(secondCultivationSensor);

        verify(cultivationTaskCoordinator)
                .submitGenerationTask(
                        1L,
                        firstCultivationEntries,
                        41L
                );
        verify(cultivationTaskCoordinator)
                .submitGenerationTask(
                        2L,
                        secondCultivationEntries,
                        41L
                );
        verify(cultivationTaskCoordinator)
                .submitGenerationTask(
                        1L,
                        firstCultivationEntries,
                        42L
                );
        verify(cultivationTaskCoordinator)
                .submitGenerationTask(
                        2L,
                        secondCultivationEntries,
                        42L
                );
    }

    @Test
    @DisplayName("한 cultivation 제출 실패가 같은 tick의 다른 cultivation 제출을 막지 않는다")
    void isolateCultivationSubmissionFailure() {
        SensorCacheEntry firstEntry =
                cacheEntry(1L, "device-A");
        SensorCacheEntry secondEntry =
                cacheEntry(2L, "device-B");

        completeInitialSynchronization();
        when(sensorCache.getSnapshot()).thenReturn(List.of(
                firstEntry,
                secondEntry
        ));
        when(generationCycleSequence.nextCycleId())
                .thenReturn(51L);
        when(cultivationTaskCoordinator.submitGenerationTask(
                anyLong(),
                anyList(),
                eq(51L)
        ))
                .thenThrow(new IllegalStateException("의도적인 제출 실패"))
                .thenReturn(true);

        assertThatCode(scheduler::scheduleSensorDataGeneration)
                .doesNotThrowAnyException();

        verify(generationCycleSequence).nextCycleId();
        verify(cultivationTaskCoordinator)
                .submitGenerationTask(
                        1L,
                        List.of(firstEntry),
                        51L
                );
        verify(cultivationTaskCoordinator)
                .submitGenerationTask(
                        2L,
                        List.of(secondEntry),
                        51L
                );
    }

    private void completeInitialSynchronization() {
        when(sensorCache.isInitialSynchronizationCompleted())
                .thenReturn(true);
        when(sensorThresholdCache.isInitialSynchronizationCompleted())
                .thenReturn(true);
    }

    private static SensorCacheEntry cacheEntry(
            long cultivationId,
            String deviceEui
    ) {
        return new SensorCacheEntry(
                cultivationId,
                deviceEui,
                "device-name",
                "location",
                "location-detail",
                "model",
                Set.of(new SensorTypeSpec("TEMPERATURE", "°C"))
        );
    }
}

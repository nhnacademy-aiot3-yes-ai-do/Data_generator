package site.yesaido.data_generator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import site.yesaido.data_generator.config.GeneratorExecutorConfiguration;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Service
public class CultivationTaskCoordinator {

    private final CultivationDataGenerationService cultivationDataGenerationService;
    private final TaskExecutor cultivationTaskExecutor;

    /*
     * 같은 cultivation의 생성 작업이 동시에 실행되지 않게 합니다.
     * cycleId는 예약 키에 포함하지 않습니다.
     */
    private final Set<Long> processingCultivationIds = ConcurrentHashMap.newKeySet();

    public CultivationTaskCoordinator(CultivationDataGenerationService cultivationDataGenerationService,
            @Qualifier(GeneratorExecutorConfiguration.CULTIVATION_TASK_EXECUTOR_NAME) TaskExecutor cultivationTaskExecutor
    ) {
        this.cultivationDataGenerationService = cultivationDataGenerationService;
        this.cultivationTaskExecutor = cultivationTaskExecutor;
    }


    public boolean submitGenerationTask(long cultivationId, List<SensorCacheEntry> sensorCacheEntries, long cycleId) {
        validateGenerationTask(cultivationId, sensorCacheEntries);
        validateCycleId(cycleId);

        List<SensorCacheEntry> sensorCacheEntriesSnapshot = List.copyOf(sensorCacheEntries);

        /*
         * primitive cycleId와 불변 센서 snapshot을 작업 lambda에
         * 그대로 캡처합니다. 작업 실행 시 새 cycle을 만들거나
         * 현재 sequence 값을 다시 읽으면 안 됩니다.
         */
        return reserveAndSubmit(cultivationId, () -> cultivationDataGenerationService
                        .generateAndPublishSensorData(cultivationId, sensorCacheEntriesSnapshot, cycleId));
    }

    private boolean reserveAndSubmit(long cultivationId, Runnable generationTask) {
        boolean taskReserved = processingCultivationIds.add(cultivationId);

        if (!taskReserved) {
            log.debug("cultivation 데이터 생성 작업이 이미 대기 또는 실행 중이므로 현재 주기를 건너뜁니다. cultivationId={}", cultivationId);
            return false;
        }

        try {
            cultivationTaskExecutor.execute(
                    () -> executeGenerationTask(cultivationId, generationTask)
            );
            return true;
        } catch (RejectedExecutionException exception) {
            processingCultivationIds.remove(cultivationId);

            log.warn("cultivation 데이터 생성 작업이 거절되어 현재 주기를 건너뜁니다. cultivationId={}, reason={}", cultivationId, exception.getMessage());
            return false;
        } catch (RuntimeException exception) {
            processingCultivationIds.remove(cultivationId);
            throw exception;
        }
    }

    private void executeGenerationTask(long cultivationId, Runnable generationTask) {
        try {
            generationTask.run();
        } catch (RuntimeException exception) {
            log.error("cultivation 데이터 생성 작업 중 오류가 발생했습니다. cultivationId={}", cultivationId, exception);
        } finally {
            processingCultivationIds.remove(cultivationId);
        }
    }

    private static void validateGenerationTask(long cultivationId, List<SensorCacheEntry> sensorCacheEntries) {
        CultivationDataGenerationService.validateCultivation(cultivationId, sensorCacheEntries);
    }

    private static void validateCycleId(long cycleId) {
        if (cycleId <= 0) {
            throw new SensorDataGenerationException("cycleId는 0보다 커야 합니다.");
        }
    }
}

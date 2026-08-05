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
    private final Set<Long> processingCultivationIds = ConcurrentHashMap.newKeySet();

    public CultivationTaskCoordinator(
            CultivationDataGenerationService cultivationDataGenerationService,
            @Qualifier(GeneratorExecutorConfiguration.CULTIVATION_TASK_EXECUTOR_NAME) TaskExecutor cultivationTaskExecutor) {
        this.cultivationDataGenerationService = cultivationDataGenerationService;
        this.cultivationTaskExecutor = cultivationTaskExecutor;
    }
    
    public boolean submitGenerationTask(long cultivationId, List<SensorCacheEntry> sensorCacheEntries) {
        validateGenerationTask(cultivationId, sensorCacheEntries);
        
        List<SensorCacheEntry> sensorCacheEntriesSnapshot = List.copyOf(sensorCacheEntries);
        
        boolean taskReserved = processingCultivationIds.add(cultivationId);
        
        if(!taskReserved) {
            log.debug("cultivation 데이터 생성 작업이 이미 대기 또는 실행 중이므로 현재 주기를 건너뜁니다. cultivationId={}",cultivationId);
            return false;
        }
        
        try {
            cultivationTaskExecutor.execute(() -> executeGenerationTask(cultivationId, sensorCacheEntriesSnapshot));
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

    private void executeGenerationTask(long cultivationId, List<SensorCacheEntry> sensorCacheEntries) {
        try {
            cultivationDataGenerationService.generateAndPublishSensorData(cultivationId, sensorCacheEntries);
        } catch (RuntimeException exception) {
            log.error("cultivation 데이터 생성 작업 중 오류가 발생했습니다. cultivationId={}", cultivationId, exception);
        } finally {
            processingCultivationIds.remove(cultivationId);
        }
    }


    private void validateGenerationTask(long cultivationId, List<SensorCacheEntry> sensorCacheEntries) {
        if ( cultivationId <= 0 ) {
            throw new SensorDataGenerationException("cultivationId는 0보다 커야 합니다.");
        }
        
        if(sensorCacheEntries == null) {
            throw new SensorDataGenerationException("sensorCacheEntries는 null일 수 없습니다.");
        }
        
        for(SensorCacheEntry sensorCacheEntry : sensorCacheEntries) {
            if(sensorCacheEntry == null) {
                throw new SensorDataGenerationException("sensorCacheEntries에 null이 포함될 수 없습니다.");
            }
            
            if(sensorCacheEntry.cultivationId() != cultivationId){
                throw new SensorDataGenerationException("서로 다른 cultivation의 센서가 포함되어 있습니다. cultivationId=" + cultivationId);
            }
        }
        
    }
}
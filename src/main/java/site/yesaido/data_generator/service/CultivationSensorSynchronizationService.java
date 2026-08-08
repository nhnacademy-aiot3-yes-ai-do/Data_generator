package site.yesaido.data_generator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.yesaido.data_generator.cache.SensorCache;
import site.yesaido.data_generator.client.CultivationSensorReadable;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.dto.response.CultivationSensorResponse;
import site.yesaido.data_generator.exception.SensorSynchronizationException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CultivationSensorSynchronizationService { // 초기 센서 목록 동기화 서비스

    private final CultivationSensorReadable cultivationSensorReadable;
    private final SensorCache sensorCache;

    public void synchronizeAllSensors() {
        try {
            List<CultivationSensorResponse> cultivationSensorResponses = cultivationSensorReadable.getCultivationSensors();

            if (cultivationSensorResponses == null) {
                throw new SensorSynchronizationException("Cultivation Service의 센서 목록 응답은 null일 수 없습니다.");
            }
            List<SensorCacheEntry> sensorCacheEntries = cultivationSensorResponses.stream()
                    .map(CultivationSensorSynchronizationService::convertToSensorCacheEntry)
                    .toList();

            sensorCache.replaceAll(sensorCacheEntries);

            log.info("Cultivation Service의 센서 목록으로 캐시를 초기화 했습니다. sensorCount={}", sensorCacheEntries.size());
        } catch (SensorSynchronizationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SensorSynchronizationException("Cultivation Service 센서 목록 동기화에 실패했습니다.", exception);
        }
    }

    private static SensorCacheEntry convertToSensorCacheEntry(CultivationSensorResponse cultivationSensorResponse) {
        if (cultivationSensorResponse == null) {
            throw new SensorSynchronizationException("Cultivation Service의 센서목록에 null 응답이 포함되어 있을 수 없습니다.");
        }
        return cultivationSensorResponse.convertToSensorCacheEntry();
    }
}

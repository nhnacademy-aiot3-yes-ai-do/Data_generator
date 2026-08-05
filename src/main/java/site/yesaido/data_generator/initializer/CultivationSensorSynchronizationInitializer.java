package site.yesaido.data_generator.initializer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import site.yesaido.data_generator.config.SensorSynchronizationProperties;
import site.yesaido.data_generator.exception.SensorSynchronizationException;
import site.yesaido.data_generator.service.CultivationSensorSynchronizationService;

@Slf4j
@Component
@Profile("!local") //역으로 local설정에선 동작하지 않음
@RequiredArgsConstructor
public class CultivationSensorSynchronizationInitializer implements ApplicationRunner {

    private final CultivationSensorSynchronizationService synchronizationService;
    private final SensorSynchronizationProperties sensorSynchronizationProperties;

    @Override
    public void run(ApplicationArguments applicationArguments) {
        int attempt = 1;
        long currentBackoffMilliseconds = sensorSynchronizationProperties.getInitialBackoffMilliseconds();

        while (attempt <= sensorSynchronizationProperties.getMaxAttempts()) {
            try {
                synchronizationService.synchronizeAllSensors(); // 센서 목록 동기화 시도

                log.info("초기 센서 동기화를 완료했습니다. attempt={}", attempt);
                return ;
            } catch (SensorSynchronizationException exception) {
                if (attempt >= sensorSynchronizationProperties.getMaxAttempts()) {
                    throw new SensorSynchronizationException("초기 센서 동기화가 최대 시도 횟수를 초과했습니다. maxAttempts=" + sensorSynchronizationProperties.getMaxAttempts(), exception);
                }

                log.warn("초기 센서 동기화에 실패해 재시도합니다. " +"attempt={}, nextAttempt={}, backoffMilliseconds={}", attempt, attempt+1, currentBackoffMilliseconds, exception);

                waitBeforeRetry(currentBackoffMilliseconds);
                currentBackoffMilliseconds = calculateNextBackoff(currentBackoffMilliseconds);
                attempt++;
            }
        }
    }

    private static void waitBeforeRetry(long backoffMilliseconds) {
        try {
            Thread.sleep(backoffMilliseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SensorSynchronizationException("초기 센서 동기화 재시도 대기 중 스레드가 중단됐습니다.", exception);
        }
    }


    private long calculateNextBackoff(long currentBackoffMilliseconds) {
        double calculatedBackoffMilliseconds = currentBackoffMilliseconds * sensorSynchronizationProperties.getBackoffMultiplier();
        if (calculatedBackoffMilliseconds >= sensorSynchronizationProperties.getMaximumBackoffMilliseconds()) {
            return sensorSynchronizationProperties.getMaximumBackoffMilliseconds();
        }

        return Math.round(calculatedBackoffMilliseconds);
    }

}

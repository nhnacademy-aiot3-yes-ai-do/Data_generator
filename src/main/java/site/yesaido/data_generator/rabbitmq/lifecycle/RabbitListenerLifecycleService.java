package site.yesaido.data_generator.rabbitmq.lifecycle;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.stereotype.Service;
import site.yesaido.data_generator.cache.SensorCache;
import site.yesaido.data_generator.cache.SensorThresholdCache;
import site.yesaido.data_generator.exception.SensorSynchronizationException;

import static site.yesaido.data_generator.rabbitmq.RabbitMqConstants.SENSOR_INFO_LISTENER_ID;
import static site.yesaido.data_generator.rabbitmq.RabbitMqConstants.THRESHOLD_INFO_LISTENER_ID;

// 초기 snapshot 완료 후 센서·임계값 RabbitMQ Listener를 명시적으로 시작합니다.
@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitListenerLifecycleService {

    private final RabbitListenerEndpointRegistry listenerEndpointRegistry;
    private final SensorCache sensorCache;
    private final SensorThresholdCache sensorThresholdCache;

    public void startListenersAfterInitialSynchronization() {
        validateInitialSynchronizationCompleted();

        MessageListenerContainer sensorInfoListenerContainer = getRequiredListenerContainer(SENSOR_INFO_LISTENER_ID);
        MessageListenerContainer thresholdInfoListenerContainer = getRequiredListenerContainer(THRESHOLD_INFO_LISTENER_ID);

        startListenerIfNecessary(sensorInfoListenerContainer, SENSOR_INFO_LISTENER_ID);
        startListenerIfNecessary(thresholdInfoListenerContainer, THRESHOLD_INFO_LISTENER_ID);

        log.info("snapshot 초기화 완료 후 RabbitMQ Listener를 시작했습니다. listenerIds={},{}",
                SENSOR_INFO_LISTENER_ID, THRESHOLD_INFO_LISTENER_ID);
    }

    private void validateInitialSynchronizationCompleted() {
        if (!sensorCache.isInitialSynchronizationCompleted() || !sensorThresholdCache.isInitialSynchronizationCompleted()) {
            throw new SensorSynchronizationException("센서와 임계값 snapshot 초기화가 모두 완료되기 전에는 RabbitMQ Listener를 시작할 수 없습니다.".strip()
            );
        }
    }

    private MessageListenerContainer getRequiredListenerContainer(String listenerId) {
        MessageListenerContainer listenerContainer = listenerEndpointRegistry.getListenerContainer(listenerId);

        if (listenerContainer == null) {
            throw new SensorSynchronizationException("RabbitMQ Listener Container를 찾을 수 없습니다. listenerId=" + listenerId
            );
        }

        return listenerContainer;
    }

    private static void startListenerIfNecessary(MessageListenerContainer listenerContainer, String listenerId) {
        if (listenerContainer.isRunning()) {
            return;
        }

        try {
            listenerContainer.start();
        } catch (RuntimeException exception) {
            throw new SensorSynchronizationException("RabbitMQ Listener 시작에 실패했습니다. listenerId=" + listenerId, exception);
        }
    }
}

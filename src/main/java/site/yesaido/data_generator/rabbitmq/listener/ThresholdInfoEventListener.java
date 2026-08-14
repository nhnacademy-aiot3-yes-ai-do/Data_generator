package site.yesaido.data_generator.rabbitmq.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import site.yesaido.data_generator.rabbitmq.event.ThresholdInfoEvent;
import site.yesaido.data_generator.service.ThresholdInfoEventService;

import static site.yesaido.data_generator.rabbitmq.RabbitMqConstants.THRESHOLD_INFO_LISTENER_ID;
import static site.yesaido.data_generator.rabbitmq.RabbitMqConstants.THRESHOLD_INFO_QUEUE;

// 임계값 큐의 threshold.crud 이벤트를 임계값 이벤트 서비스로 전달합니다.
@Component
@RequiredArgsConstructor
public class ThresholdInfoEventListener {

    private final ThresholdInfoEventService thresholdInfoEventService;

    @RabbitListener(
            id = THRESHOLD_INFO_LISTENER_ID,
            queues = THRESHOLD_INFO_QUEUE,
            containerFactory = "rabbitListenerContainerFactory",
            autoStartup = "false",
            concurrency = "1"
    )
    public void handleThresholdInfoEvent(ThresholdInfoEvent thresholdInfoEvent) {
        thresholdInfoEventService.processThresholdEvent(
                thresholdInfoEvent
        );
    }
}

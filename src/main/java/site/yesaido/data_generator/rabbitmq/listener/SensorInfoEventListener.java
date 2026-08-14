package site.yesaido.data_generator.rabbitmq.listener;


import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import site.yesaido.data_generator.rabbitmq.event.SensorInfoDeleteEvent;
import site.yesaido.data_generator.rabbitmq.event.SensorInfoUpsertEvent;
import site.yesaido.data_generator.service.SensorInfoEventService;

import static site.yesaido.data_generator.rabbitmq.RabbitMqConstants.SENSOR_INFO_LISTENER_ID;
import static site.yesaido.data_generator.rabbitmq.RabbitMqConstants.SENSOR_INFO_QUEUE;

// 센서 큐의 Upsert와 Delete 이벤트를 타입에 따라 센서 이벤트 서비스로 전달합니다.
@Component
@RequiredArgsConstructor
@RabbitListener(
        id = SENSOR_INFO_LISTENER_ID,
        queues = SENSOR_INFO_QUEUE,
        containerFactory = "rabbitListenerContainerFactory",
        autoStartup = "false",
        concurrency = "1"
)
public class SensorInfoEventListener {

    private final SensorInfoEventService sensorInfoEventService;

    @RabbitHandler
    public void handleSensorInfoUpsertEvent(SensorInfoUpsertEvent sensorInfoUpsertEvent) {
        sensorInfoEventService.processUpsertEvent(sensorInfoUpsertEvent);
    }

    @RabbitHandler
    public void handleSensorInfoDeleteEvent(SensorInfoDeleteEvent sensorInfoDeleteEvent) {
        sensorInfoEventService.processDeleteEvent(sensorInfoDeleteEvent);
    }
}

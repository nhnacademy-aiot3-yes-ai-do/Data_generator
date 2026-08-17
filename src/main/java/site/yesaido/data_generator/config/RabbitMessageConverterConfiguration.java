package site.yesaido.data_generator.config;

import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import site.yesaido.data_generator.rabbitmq.event.SensorInfoDeleteEvent;
import site.yesaido.data_generator.rabbitmq.event.SensorInfoUpsertEvent;
import site.yesaido.data_generator.rabbitmq.event.ThresholdInfoEvent;

import java.util.HashMap;
import java.util.Map;

import static site.yesaido.data_generator.rabbitmq.RabbitMqConstants.SENSOR_DELETE_TYPE_ID;
import static site.yesaido.data_generator.rabbitmq.RabbitMqConstants.SENSOR_UPSERT_TYPE_ID;
import static site.yesaido.data_generator.rabbitmq.RabbitMqConstants.THRESHOLD_CRUD_TYPE_ID;

// RabbitMQ의 JSON 메시지와 로컬 이벤트 record 사이의 변환 규칙을 설정합니다.
@Configuration(proxyBeanMethods = false)
public class RabbitMessageConverterConfiguration {

    @Bean
    public DefaultClassMapper createRabbitClassMapper() {
        DefaultClassMapper classMapper = new DefaultClassMapper();

        Map<String, Class<?>> idClassMapping = new HashMap<>();

        idClassMapping.put(SENSOR_UPSERT_TYPE_ID, SensorInfoUpsertEvent.class);
        idClassMapping.put(SENSOR_DELETE_TYPE_ID, SensorInfoDeleteEvent.class);
        idClassMapping.put(THRESHOLD_CRUD_TYPE_ID, ThresholdInfoEvent.class);

        classMapper.setIdClassMapping(idClassMapping);

        return classMapper;
    }

    @Bean
    public MessageConverter createRabbitMessageConverter(DefaultClassMapper classMapper) {
        JacksonJsonMessageConverter messageConverter = new JacksonJsonMessageConverter();

        messageConverter.setClassMapper(classMapper);

        return messageConverter;
    }
}

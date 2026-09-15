package site.yesaido.data_generator.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import site.yesaido.common.rabbitmq.DeadLetterQueues;
import site.yesaido.common.rabbitmq.DeadLetterTopologyConfiguration;
import site.yesaido.common.rabbitmq.RabbitDeadLetterProperties;

import static site.yesaido.data_generator.rabbitmq.RabbitMqConstants.*;

// Data Generator가 소비할 RabbitMQ Exchange, Queue, Binding을 선언합니다.
@Configuration(proxyBeanMethods = false)
@Import(DeadLetterTopologyConfiguration.class)
public class RabbitMqTopologyConfiguration {

    @Bean
    public TopicExchange createSensorExchange() {
        return new TopicExchange(SENSOR_EXCHANGE);
    }

    @Bean
    public Queue createSensorInfoQueue(RabbitDeadLetterProperties dlProps) {
        return DeadLetterQueues.durableWithDeadLetter(SENSOR_INFO_QUEUE, dlProps).build();
    }

    @Bean
    public Binding createSensorInfoBinding(
            @Qualifier("createSensorInfoQueue")
            Queue sensorInfoQueue,

            @Qualifier("createSensorExchange")
            TopicExchange sensorExchange
    ) {
        return BindingBuilder
                .bind(sensorInfoQueue)
                .to(sensorExchange)
                .with(SENSOR_INFO_BINDING_KEY_PATTERN);
    }

    @Bean
    public Queue createThresholdInfoQueue(RabbitDeadLetterProperties dlProps) {
        return DeadLetterQueues.durableWithDeadLetter(THRESHOLD_INFO_QUEUE, dlProps).build();
    }

    @Bean
    public Binding createThresholdInfoBinding(
            @Qualifier("createThresholdInfoQueue")
            Queue thresholdInfoQueue,

            @Qualifier("createSensorExchange")
            TopicExchange sensorExchange
    ) {
        return BindingBuilder
                .bind(thresholdInfoQueue)
                .to(sensorExchange)
                .with(THRESHOLD_INFO_BINDING_KEY_PATTERN);
    }
}
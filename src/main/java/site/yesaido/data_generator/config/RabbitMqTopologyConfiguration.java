package site.yesaido.data_generator.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static site.yesaido.data_generator.rabbitmq.RabbitMqConstants.DEAD_LETTER_EXCHANGE;
import static site.yesaido.data_generator.rabbitmq.RabbitMqConstants.DEAD_LETTER_EXCHANGE_ARGUMENT;
import static site.yesaido.data_generator.rabbitmq.RabbitMqConstants.DEAD_LETTER_QUEUE;
import static site.yesaido.data_generator.rabbitmq.RabbitMqConstants.SENSOR_EXCHANGE;
import static site.yesaido.data_generator.rabbitmq.RabbitMqConstants.SENSOR_INFO_BINDING_KEY_PATTERN;
import static site.yesaido.data_generator.rabbitmq.RabbitMqConstants.SENSOR_INFO_QUEUE;
import static site.yesaido.data_generator.rabbitmq.RabbitMqConstants.THRESHOLD_INFO_BINDING_KEY_PATTERN;
import static site.yesaido.data_generator.rabbitmq.RabbitMqConstants.THRESHOLD_INFO_QUEUE;

// Data Generator가 소비할 RabbitMQ Exchange, Queue, Binding을 선언합니다.
@Configuration(proxyBeanMethods = false)
public class RabbitMqTopologyConfiguration {

    @Bean
    public FanoutExchange createDeadLetterExchange() {
        return new FanoutExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue createDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding createDeadLetterBinding(
            @Qualifier("createDeadLetterQueue")
            Queue deadLetterQueue,

            @Qualifier("createDeadLetterExchange")
            FanoutExchange deadLetterExchange
    ) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange);
    }

    @Bean
    public TopicExchange createSensorExchange() {
        return new TopicExchange(SENSOR_EXCHANGE);
    }

    @Bean
    public Queue createSensorInfoQueue() {
        return QueueBuilder
                .durable(SENSOR_INFO_QUEUE)
                .withArgument(DEAD_LETTER_EXCHANGE_ARGUMENT, DEAD_LETTER_EXCHANGE)
                .build();
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
    public Queue createThresholdInfoQueue() {
        return QueueBuilder
                .durable(THRESHOLD_INFO_QUEUE)
                .withArgument(DEAD_LETTER_EXCHANGE_ARGUMENT, DEAD_LETTER_EXCHANGE)
                .build();
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

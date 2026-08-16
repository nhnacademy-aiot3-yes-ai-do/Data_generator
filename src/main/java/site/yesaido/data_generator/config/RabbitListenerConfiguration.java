package site.yesaido.data_generator.config;

import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// RabbitMQ Listener의 단일 처리, 지연 시작, 재시도와 DLQ 전송 조건을 설정합니다.
@Configuration(proxyBeanMethods = false)
public class RabbitListenerConfiguration {

    private static final int LISTENER_CONCURRENCY = 1;
    private static final int MAXIMUM_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MILLISECONDS = 1_000L;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final long MAXIMUM_BACKOFF_MILLISECONDS = 10_000L;

    @Bean(name = "rabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory createRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer factoryConfigurer,
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter
    ) {
        SimpleRabbitListenerContainerFactory containerFactory = new SimpleRabbitListenerContainerFactory();

        factoryConfigurer.configure(containerFactory, connectionFactory);

        containerFactory.setMessageConverter(messageConverter);
        containerFactory.setConcurrentConsumers(LISTENER_CONCURRENCY);
        containerFactory.setMaxConcurrentConsumers(LISTENER_CONCURRENCY);
        containerFactory.setAutoStartup(false);
        containerFactory.setDefaultRequeueRejected(false);

        containerFactory.setAdviceChain(RetryInterceptorBuilder.stateless()
                        .maxRetries(MAXIMUM_RETRIES)
                        .backOffOptions(INITIAL_BACKOFF_MILLISECONDS, BACKOFF_MULTIPLIER, MAXIMUM_BACKOFF_MILLISECONDS)
                        .recoverer(new RejectAndDontRequeueRecoverer())
                        .build()
        );

        return containerFactory;
    }
}

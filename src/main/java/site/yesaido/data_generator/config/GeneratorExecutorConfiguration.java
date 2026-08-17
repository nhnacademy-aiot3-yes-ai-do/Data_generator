package site.yesaido.data_generator.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class GeneratorExecutorConfiguration {

    public static final String CULTIVATION_TASK_EXECUTOR_NAME = "cultivationTaskExecutor";
    private static final String THREAD_NAME_PREFIX = "cultivation-worker-";

    private final GeneratorExecutionProperties generatorExecutionProperties;

    @Bean(name = CULTIVATION_TASK_EXECUTOR_NAME)
    public ThreadPoolTaskExecutor createCultivationTaskExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();

        int workerPoolSize = generatorExecutionProperties.getWorkerPoolSize();

        taskExecutor.setCorePoolSize(workerPoolSize);
        taskExecutor.setMaxPoolSize(workerPoolSize);
        taskExecutor.setQueueCapacity(generatorExecutionProperties.getQueueCapacity());
        taskExecutor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        taskExecutor.setAwaitTerminationSeconds(generatorExecutionProperties.getAwaitTerminationSeconds());
        taskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

        return taskExecutor;
    }
}

package site.yesaido.data_generator.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "generator.execution")
public class GeneratorExecutionProperties {

    @Min(1)
    private int workerPoolSize = 4;

    @Min(0)
    private int queueCapacity = 200;

    @Min(1)
    private int awaitTerminationSeconds = 10;


}

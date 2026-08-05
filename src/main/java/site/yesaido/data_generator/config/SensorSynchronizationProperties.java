package site.yesaido.data_generator.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
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
@ConfigurationProperties(prefix = "sensor.synchronization")
public class SensorSynchronizationProperties {

    @Min(1)
    private int maxAttempts = 5;

    @Min(1)
    private long initialBackoffMilliseconds = 1_000;

    @DecimalMin("1.0")
    private double backoffMultiplier = 2.0;

    @Min(1)
    private long maximumBackoffMilliseconds = 30_000;

    @AssertTrue(message = "maximumBackoffMilliseconds는 initialBackoffMilliseconds보다 작을 수 없습니다.")
    public boolean isBackoffRangeValid() {
        return maximumBackoffMilliseconds >= initialBackoffMilliseconds;
    }
}

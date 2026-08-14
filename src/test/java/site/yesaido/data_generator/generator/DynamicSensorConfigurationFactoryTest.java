package site.yesaido.data_generator.generator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.data_generator.domain.DynamicSensorGenerationPolicy;
import site.yesaido.data_generator.domain.MeasurementConfiguration;
import site.yesaido.data_generator.domain.SensorThresholdRange;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class DynamicSensorConfigurationFactoryTest {

    private final DynamicSensorConfigurationFactory factory =
            new DynamicSensorConfigurationFactory();

    @Test
    @DisplayName("임계값과 정책으로 Random Walk 설정을 생성한다")
    void createRandomWalkConfigurationFromThresholdAndPolicy(){
        SensorThresholdRange thresholdRange = new SensorThresholdRange(
                new BigDecimal("10"), new BigDecimal("20")
        );

        DynamicSensorGenerationPolicy generationPolicy =
                new DynamicSensorGenerationPolicy(
                        new BigDecimal("0.20"), new BigDecimal("0.02"),
                        1
                );

        MeasurementConfiguration measurementConfiguration =
                factory.create(thresholdRange,generationPolicy);

        assertThat(measurementConfiguration.initialValue()).isEqualTo(15.0);
        assertThat(measurementConfiguration.minimumValue()).isEqualTo(8.0);
        assertThat(measurementConfiguration.maximumValue()).isEqualTo(22.0);
        assertThat(measurementConfiguration.maximumChange()).isEqualTo(0.2);
        assertThat(measurementConfiguration.decimalPlaces()).isEqualTo(1);

    }

    @Test
    @DisplayName("최솟값과 최댓값이 같으면 고정값 생성 설정을 만든다")
    void createFixedValueConfigurationWhenThresholdsAreEqual() {
        SensorThresholdRange thresholdRange =
                new SensorThresholdRange(new BigDecimal("15"), new BigDecimal("15"));

        DynamicSensorGenerationPolicy generationPolicy =
                new DynamicSensorGenerationPolicy(new BigDecimal("0.10"), new BigDecimal("0.02"),
                        2
                );

        MeasurementConfiguration measurementConfiguration =
                factory.create(thresholdRange, generationPolicy);

        assertThat(measurementConfiguration.initialValue()).isEqualTo(15.0);
        assertThat(measurementConfiguration.minimumValue()).isEqualTo(15.0);
        assertThat(measurementConfiguration.maximumValue()).isEqualTo(15.0);
        assertThat(measurementConfiguration.maximumChange()).isEqualTo(0.0);
        assertThat(measurementConfiguration.decimalPlaces()).isEqualTo(2);
    }

    @Test
    @DisplayName("임계값 범위가 null이면 예외가 발생한다")
    void throwExceptionWhenThresholdRangeIsNull() {
        DynamicSensorGenerationPolicy generationPolicy =
                new DynamicSensorGenerationPolicy(new BigDecimal("0.10"), new BigDecimal("0.02"),
                        2
                );

        assertThatThrownBy(() -> factory.create(null, generationPolicy))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("thresholdRange는 null일 수 없습니다.");
    }

    @Test
    @DisplayName("동적 생성 정책이 null이면 예외가 발생한다")
    void throwExceptionWhenGenerationPolicyIsNull() {
        SensorThresholdRange thresholdRange =
                new SensorThresholdRange(new BigDecimal("10"), new BigDecimal("20"));

        assertThatThrownBy(() -> factory.create(thresholdRange, null))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("generationPolicy는 null일 수 없습니다.");
    }

    @Test
    @DisplayName("double의 최대 표현 범위를 초과하면 예외가 발생한다")
    void throwExceptionWhenThresholdExceedsDoubleRange() {
        BigDecimal valueExceedingDoubleMaximum =
                BigDecimal.valueOf(Double.MAX_VALUE)
                        .multiply(BigDecimal.TEN);

        SensorThresholdRange thresholdRange =
                new SensorThresholdRange(valueExceedingDoubleMaximum,
                        valueExceedingDoubleMaximum.multiply(BigDecimal.valueOf(2L)));

        DynamicSensorGenerationPolicy generationPolicy =
                new DynamicSensorGenerationPolicy(new BigDecimal("0.10"), new BigDecimal("0.02"),
                        2
                );

        assertThatThrownBy(() -> factory.create(thresholdRange, generationPolicy))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("유한한 double 값으로 변환할 수 없습니다.");
    }

    @Test
    @DisplayName("double의 최소 표현 범위보다 작은 임계값이면 예외가 발생한다")
    void throwExceptionWhenThresholdIsSmallerThanMinimumDoubleValue() {
        BigDecimal smallerThanMinimumDouble =
                BigDecimal.valueOf(Double.MIN_VALUE)
                        .divide(BigDecimal.TEN);

        SensorThresholdRange thresholdRange =
                new SensorThresholdRange(smallerThanMinimumDouble,
                        smallerThanMinimumDouble.multiply(BigDecimal.valueOf(2L))
                );

        DynamicSensorGenerationPolicy generationPolicy =
                new DynamicSensorGenerationPolicy(new BigDecimal("0.10"), new BigDecimal("0.02"),
                        2
                );

        assertThatThrownBy(() -> factory.create(thresholdRange, generationPolicy))
                .isInstanceOf(SensorDataGenerationException.class);
    }
}

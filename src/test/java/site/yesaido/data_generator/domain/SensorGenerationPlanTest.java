package site.yesaido.data_generator.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

class SensorGenerationPlanTest {

    private final EnvironmentStateKey environmentStateKey =
            new EnvironmentStateKey(
                    1L,
                    "TEMPERATURE",
                    "°C"
            );

    private final MeasurementConfiguration measurementConfiguration =
            new MeasurementConfiguration(
                    16.0,
                    10.0,
                    30.0,
                    0.3,
                    1
            );

    @Test
    void preserveResolvedGenerationInformation() {
        SensorGenerationPlan plan = new SensorGenerationPlan(environmentStateKey, measurementConfiguration);

        assertThat(plan.environmentStateKey()).isSameAs(environmentStateKey);
        assertThat(plan.measurementConfiguration()).isSameAs(measurementConfiguration);
    }

    @Test
    void rejectNullEnvironmentStateKey() {
        assertThatThrownBy(() -> new SensorGenerationPlan(null, measurementConfiguration))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("environmentStateKey");
    }

    @Test
    void rejectNullMeasurementConfiguration() {
        assertThatThrownBy(() -> new SensorGenerationPlan(environmentStateKey, null))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("measurementConfiguration");
    }
}
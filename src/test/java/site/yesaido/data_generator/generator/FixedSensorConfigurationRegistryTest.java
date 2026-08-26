package site.yesaido.data_generator.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import site.yesaido.data_generator.domain.MeasurementConfiguration;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

class FixedSensorConfigurationRegistryTest {

    private final FixedSensorConfigurationRegistry registry = new FixedSensorConfigurationRegistry();

    @ParameterizedTest
    @MethodSource("fixedSensorConfigurations")
    void findFixedSensorConfiguration(String sensorType, MeasurementConfiguration expectedConfiguration) {
        assertThat(registry.findBySensorType(sensorType)).contains(expectedConfiguration);
    }

    @Test
    void stripSensorTypeBeforeLookup() {
        assertThat(registry.findBySensorType("  TEMPERATURE  "))
                .contains(new MeasurementConfiguration(16.0, 10.0, 30.0, 0.3, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SOIL_MOISTURE", "temperature"})
    void returnEmptyForUnknownOrDifferentCaseSensorType(String sensorType) {
        assertThat(registry.findBySensorType(sensorType)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void rejectMissingSensorType(String sensorType) {
        assertThatThrownBy(() -> registry.findBySensorType(sensorType))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("sensorType");
    }

    private static Stream<Arguments> fixedSensorConfigurations() {
        return Stream.of(
                Arguments.of("TEMPERATURE",
                        new MeasurementConfiguration(16.0, 10.0, 30.0, 0.3, 1)),
                Arguments.of("HUMIDITY",
                        new MeasurementConfiguration(80.0, 40.0, 120.0, 1.0, 1)),
                Arguments.of("CO2",
                        new MeasurementConfiguration(1500.0, 500.0, 4000.0, 30.0, 0)),
                Arguments.of("LIGHT",
                        new MeasurementConfiguration(100.0, 0.0, 1000.0, 20.0, 0))
        );
    }
}

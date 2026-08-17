package site.yesaido.data_generator.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StandardSensorUnitConverterTest {

    private final StandardSensorUnitConverter converter = new StandardSensorUnitConverter();

    @ParameterizedTest(name = "{0}/{1} 표준값 {2} -> {3}")
    @MethodSource("supportedConversions")
    @DisplayName("지원 센서의 표준값을 등록 단위로 변환한다")
    void convertSupportedCanonicalValue(
            String sensorType,
            String unit,
            Number canonicalValue,
            double expectedValue
    ) {
        Optional<Number> convertedValue = converter.convertFromCanonical(
                sensorType, unit, canonicalValue);

        assertThat(convertedValue)
                .hasValueSatisfying(value -> assertThat(value.doubleValue())
                        .isEqualTo(expectedValue));
    }

    private static Stream<Arguments> supportedConversions() {
        return Stream.of(
                Arguments.of("TEMPERATURE", "°C", 20.0, 20.0),
                Arguments.of("TEMPERATURE", "°F", 20.0, 68.0),
                Arguments.of("TEMPERATURE", "°F", 20.03, 68.1),
                Arguments.of("HUMIDITY", "%RH", 75.5, 75.5),
                Arguments.of("CO2", "ppm", 1_500L, 1_500.0),
                Arguments.of("LIGHT", "lux", 350, 350.0)
        );
    }

    @ParameterizedTest(name = "{0}/{1}")
    @MethodSource("unsupportedConversions")
    @DisplayName("지원하지 않는 센서 타입 또는 단위에는 빈 결과를 반환한다")
    void returnEmptyForUnsupportedConversion(String sensorType, String unit) {
        assertThat(converter.convertFromCanonical(sensorType, unit, 10.0))
                .isEmpty();
    }

    private static Stream<Arguments> unsupportedConversions() {
        return Stream.of(
                Arguments.of("TEMPERATURE", "K"),
                Arguments.of("HUMIDITY", "%"),
                Arguments.of("CO2", "ppb"),
                Arguments.of("LIGHT", "lumen"),
                Arguments.of("SOIL_MOISTURE", "%")
        );
    }

    @Test
    @DisplayName("센서 타입과 단위의 앞뒤 공백을 제거한 뒤 변환한다")
    void normalizeSensorTypeAndUnit() {
        Optional<Number> convertedValue = converter.convertFromCanonical(
                "  TEMPERATURE  ", "  °F  ", 10.0);

        assertThat(convertedValue)
                .hasValueSatisfying(value -> assertThat(value.doubleValue())
                        .isEqualTo(50.0));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("센서 타입이 null 또는 공백이면 예외가 발생한다")
    void rejectMissingSensorType(String sensorType) {
        assertThatThrownBy(() -> converter.convertFromCanonical(
                sensorType, "°C", 10.0))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("sensorType");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("단위가 null 또는 공백이면 예외가 발생한다")
    void rejectMissingUnit(String unit) {
        assertThatThrownBy(() -> converter.convertFromCanonical(
                "TEMPERATURE", unit, 10.0))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("unit");
    }

    @ParameterizedTest
    @MethodSource("invalidCanonicalValues")
    @DisplayName("표준값이 null 또는 유한하지 않은 숫자이면 예외가 발생한다")
    void rejectInvalidCanonicalValue(Number canonicalValue) {
        assertThatThrownBy(() -> converter.convertFromCanonical(
                "TEMPERATURE", "°C", canonicalValue))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("canonicalValue");
    }

    private static Stream<Number> invalidCanonicalValues() {
        return Stream.of(
                null,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Float.NaN
        );
    }
}

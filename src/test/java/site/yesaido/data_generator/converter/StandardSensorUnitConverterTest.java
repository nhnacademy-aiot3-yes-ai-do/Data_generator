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
import static org.assertj.core.api.AssertionsForClassTypes.within;

class StandardSensorUnitConverterTest {

    private final StandardSensorUnitConverter converter = new StandardSensorUnitConverter();

    @ParameterizedTest(name = "{0}/{1} 표준값 {2} -> {3}")
    @MethodSource("supportedConversions")
    @DisplayName("지원 센서의 표준값을 등록 단위로 변환한다")
    void convertSupportedCanonicalValue(String sensorType, String unit, Number canonicalValue, double expectedValue) {
        Optional<Number> convertedValue = converter.convertFromCanonical(sensorType, unit, canonicalValue);
        assertThat(convertedValue).hasValueSatisfying(value -> assertThat(value.doubleValue())
                .isEqualTo(expectedValue));
    }

    private static Stream<Arguments> supportedConversions() {
        return Stream.of(
                Arguments.of("TEMPERATURE", "℃", 20.0, 20.0),
                Arguments.of("TEMPERATURE", "°F", 20.0, 68.0),
                Arguments.of("TEMPERATURE", "°F", 20.03, 68.1),
                Arguments.of("HUMIDITY", "%", 75.5, 75.5),
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
                Arguments.of("TEMPERATURE", "\u00B0C"),
                Arguments.of("TEMPERATURE", "K"),
                Arguments.of("HUMIDITY", "%RH"),
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
                sensorType, "℃", 10.0))
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
                "TEMPERATURE", "℃", canonicalValue))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("canonicalValue");
    }

    @ParameterizedTest(name = "{0}의 표준 단위는 {1}")
    @MethodSource("canonicalUnits")
    @DisplayName("표준 센서 타입의 내부 표준 단위를 반환한다")
    void findCanonicalUnit(String sensorType, String expectedUnit) {
        assertThat(converter.findCanonicalUnit(sensorType))
                .contains(expectedUnit);
    }

    private static Stream<Arguments> canonicalUnits() {
        return Stream.of(
                Arguments.of("TEMPERATURE", "℃"),
                Arguments.of("HUMIDITY", "%"),
                Arguments.of("CO2", "ppm"),
                Arguments.of("LIGHT", "lux")
        );
    }

    @Test
    @DisplayName("동적 센서 타입은 정의된 표준 단위가 없다")
    void returnEmptyCanonicalUnitForDynamicSensorType() {
        assertThat(converter.findCanonicalUnit("SOIL_MOISTURE"))
                .isEmpty();
    }

    @Test
    @DisplayName("표준 단위를 찾을 때 센서 타입의 앞뒤 공백을 제거한다")
    void normalizeSensorTypeWhenFindingCanonicalUnit() {
        assertThat(converter.findCanonicalUnit("  TEMPERATURE  "))
                .contains("℃");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("표준 단위 조회 시 센서 타입은 필수이다")
    void rejectMissingSensorTypeWhenFindingCanonicalUnit(String sensorType) {
        assertThatThrownBy(() -> converter.findCanonicalUnit(sensorType))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("sensorType");
    }

    @ParameterizedTest(name = "{0}/{1} {2} -> 표준값 {3}")
    @MethodSource("supportedToCanonicalConversions")
    @DisplayName("등록 단위 값을 내부 표준 단위로 변환한다")
    void convertSupportedValueToCanonical(String sensorType, String unit, Number sourceValue, double expectedValue) {
        Optional<Number> convertedValue = converter.convertToCanonical(sensorType, unit, sourceValue);

        assertThat(convertedValue).hasValueSatisfying(value ->
                        assertThat(value.doubleValue()).isCloseTo(expectedValue, within(0.000_000_001)));
    }

    private static Stream<Arguments> supportedToCanonicalConversions() {
        return Stream.of(
                Arguments.of("TEMPERATURE", "℃", 20.0, 20.0),
                Arguments.of("TEMPERATURE", "°F", 32.0, 0.0),
                Arguments.of("TEMPERATURE", "°F", 68.0, 20.0),
                Arguments.of("TEMPERATURE", "°F", 68.18, 20.1),
                Arguments.of("TEMPERATURE", "°F", -40.0, -40.0),
                Arguments.of("HUMIDITY", "%", 75.5, 75.5),
                Arguments.of("CO2", "ppm", 1_500L, 1_500.0),
                Arguments.of("LIGHT", "lux", 350, 350.0)
        );
    }

    @ParameterizedTest(name = "{0}/{1}")
    @MethodSource("unsupportedConversions")
    @DisplayName("지원하지 않는 조합은 표준 단위로 변환하지 않는다")
    void returnEmptyForUnsupportedToCanonicalConversion(String sensorType, String unit) {
        assertThat(converter.convertToCanonical(sensorType, unit, 10.0)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidCanonicalValues")
    @DisplayName("입력값이 null 또는 유한하지 않으면 표준 단위 변환을 거절한다")
    void rejectInvalidSourceValue(Number sourceValue) {
        assertThatThrownBy(() -> converter.convertToCanonical("TEMPERATURE", "℃", sourceValue))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("sourceValue");
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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("표준 단위 변환 시 입력 단위는 필수이다")
    void rejectMissingUnitWhenConvertingToCanonical(String unit) {
        assertThatThrownBy(() -> converter.convertToCanonical("TEMPERATURE", unit, 10.0))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("unit");
    }
}

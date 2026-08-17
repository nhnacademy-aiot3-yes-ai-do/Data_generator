package site.yesaido.data_generator.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationConfigurationValueObjectTest {

    @Test
    @DisplayName("측정 설정은 범위 경계와 지원 최대 정밀도를 허용한다")
    void allowMeasurementConfigurationBoundaries() {
        assertThat(MeasurementConfiguration.MAX_DECIMAL_PLACES).isEqualTo(15);

        MeasurementConfiguration configuration = new MeasurementConfiguration(
                0.0, 0.0, 1.0, 0.0,
                15);

        assertThat(configuration.initialValue()).isZero();
        assertThat(configuration.minimumValue()).isZero();
        assertThat(configuration.maximumValue()).isEqualTo(1.0);
        assertThat(configuration.maximumChange()).isZero();
        assertThat(configuration.decimalPlaces()).isEqualTo(15);
    }

    @ParameterizedTest(name = "{5}")
    @MethodSource("invalidMeasurementConfigurations")
    @DisplayName("잘못된 측정 설정을 거절한다")
    void rejectInvalidMeasurementConfiguration(
            double initialValue,
            double minimumValue,
            double maximumValue,
            double maximumChange,
            int decimalPlaces,
            String expectedMessage
    ) {
        assertThatThrownBy(() -> new MeasurementConfiguration(
                initialValue, minimumValue, maximumValue, maximumChange, decimalPlaces))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static Stream<Arguments> invalidMeasurementConfigurations() {
        return Stream.of(
                Arguments.of(Double.NaN, 0.0, 1.0, 0.1, 1, "initialValue"),
                Arguments.of(0.5, Double.NEGATIVE_INFINITY, 1.0, 0.1, 1,
                        "minimumValue"),
                Arguments.of(0.5, 0.0, Double.POSITIVE_INFINITY, 0.1, 1,
                        "maximumValue"),
                Arguments.of(0.5, 0.0, 1.0, Double.NaN, 1, "maximumChange"),
                Arguments.of(0.5, 2.0, 1.0, 0.1, 1,
                        "minimumValue"),
                Arguments.of(-0.1, 0.0, 1.0, 0.1, 1,
                        "initialValue"),
                Arguments.of(1.1, 0.0, 1.0, 0.1, 1,
                        "initialValue"),
                Arguments.of(0.5, 0.0, 1.0, -0.1, 1,
                        "maximumChange"),
                Arguments.of(0.5, 0.0, 1.0, 0.1, -1,
                        "decimalPlaces"),
                Arguments.of(0.5, 0.0, 1.0, 0.1, 16,
                        "decimalPlaces")
        );
    }

    @Test
    @DisplayName("동적 센서 정책 비율의 불필요한 소수점 0을 제거한다")
    void normalizeDynamicSensorGenerationPolicyRatios() {
        DynamicSensorGenerationPolicy policy = new DynamicSensorGenerationPolicy(
                new BigDecimal("0.2000"), new BigDecimal("1.000"), 2);

        assertThat(policy.rangeExpansionRatio()).isEqualTo(new BigDecimal("0.2"));
        assertThat(policy.maximumChangeRatio()).isEqualTo(BigDecimal.ONE);
        assertThat(policy.decimalPlaces()).isEqualTo(2);
    }

    @Test
    @DisplayName("동적 센서 정책은 허용 비율 경계를 포함한다")
    void allowDynamicSensorGenerationPolicyBoundaries() {
        DynamicSensorGenerationPolicy lowerExpansionBoundary =
                new DynamicSensorGenerationPolicy(
                        BigDecimal.ZERO, new BigDecimal("0.0001"), 0);

        DynamicSensorGenerationPolicy upperBoundaries =
                new DynamicSensorGenerationPolicy(
                        BigDecimal.ONE, BigDecimal.ONE,
                        15);

        assertThat(lowerExpansionBoundary.rangeExpansionRatio()).isEqualByComparingTo("0");
        assertThat(upperBoundaries.rangeExpansionRatio()).isEqualByComparingTo("1");
        assertThat(upperBoundaries.maximumChangeRatio()).isEqualByComparingTo("1");
        assertThat(upperBoundaries.decimalPlaces()).isEqualTo(15);
    }

    @ParameterizedTest(name = "{3}")
    @MethodSource("invalidDynamicSensorPolicies")
    @DisplayName("잘못된 동적 센서 생성 정책을 거절한다")
    void rejectInvalidDynamicSensorGenerationPolicy(
            BigDecimal rangeExpansionRatio,
            BigDecimal maximumChangeRatio,
            int decimalPlaces,
            String expectedMessage
    ) {
        assertThatThrownBy(() -> new DynamicSensorGenerationPolicy(
                rangeExpansionRatio, maximumChangeRatio, decimalPlaces))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static Stream<Arguments> invalidDynamicSensorPolicies() {
        return Stream.of(
                Arguments.of(null, new BigDecimal("0.1"), 1,
                        "rangeExpansionRatio"),
                Arguments.of(new BigDecimal("0.1"), null, 1,
                        "maximumChangeRatio"),
                Arguments.of(new BigDecimal("-0.1"), new BigDecimal("0.1"), 1,
                        "rangeExpansionRatio"),
                Arguments.of(new BigDecimal("1.1"), new BigDecimal("0.1"), 1,
                        "rangeExpansionRatio"),
                Arguments.of(new BigDecimal("0.1"), BigDecimal.ZERO, 1,
                        "maximumChangeRatio"),
                Arguments.of(new BigDecimal("0.1"), new BigDecimal("1.1"), 1,
                        "maximumChangeRatio"),
                Arguments.of(new BigDecimal("0.1"), new BigDecimal("0.1"), -1,
                        "decimalPlaces"),
                Arguments.of(new BigDecimal("0.1"), new BigDecimal("0.1"), 16,
                        "decimalPlaces")
        );
    }
}

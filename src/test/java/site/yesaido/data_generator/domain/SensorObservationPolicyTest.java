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

class SensorObservationPolicyTest {

    @Test
    @DisplayName("센서 관측 정책 비율의 불필요한 소수점 0을 제거한다")
    void normalizeRatios() {
        SensorObservationPolicy policy = new SensorObservationPolicy(
                new BigDecimal("0.0100"), new BigDecimal("0.00500"), new BigDecimal("0.800"));

        assertThat(policy.maximumBiasRatio()).isEqualTo(new BigDecimal("0.01"));
        assertThat(policy.maximumDeviationRatio()).isEqualTo(new BigDecimal("0.005"));
        assertThat(policy.deviationRetentionRatio()).isEqualTo(new BigDecimal("0.8"));
    }

    @Test
    @DisplayName("센서 관측 정책의 허용 경계를 포함한다")
    void allowRatioBoundaries() {
        SensorObservationPolicy lowerBoundaries = new SensorObservationPolicy(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        SensorObservationPolicy upperBoundaries = new SensorObservationPolicy(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("0.9999"));

        assertThat(lowerBoundaries.maximumBiasRatio()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(lowerBoundaries.maximumDeviationRatio()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(lowerBoundaries.deviationRetentionRatio()).isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(upperBoundaries.maximumBiasRatio()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(upperBoundaries.maximumDeviationRatio()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(upperBoundaries.deviationRetentionRatio()).isEqualByComparingTo("0.9999");
    }

    @ParameterizedTest(name = "{3}")
    @MethodSource("invalidPolicies")
    @DisplayName("잘못된 센서 관측 정책을 거절한다")
    void rejectInvalidPolicies(BigDecimal maximumBiasRatio, BigDecimal maximumDeviationRatio, BigDecimal deviationRetentionRatio, String expectedFieldName) {
        assertThatThrownBy(() -> new SensorObservationPolicy(maximumBiasRatio, maximumDeviationRatio, deviationRetentionRatio))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining(expectedFieldName);
    }

    private static Stream<Arguments> invalidPolicies() {
        BigDecimal validBias = new BigDecimal("0.01");
        BigDecimal validDeviation = new BigDecimal("0.005");
        BigDecimal validRetention = new BigDecimal("0.8");

        return Stream.of(
                Arguments.of(null, validDeviation, validRetention, "maximumBiasRatio"),
                Arguments.of(new BigDecimal("-0.01"), validDeviation, validRetention, "maximumBiasRatio"),
                Arguments.of(new BigDecimal("1.01"), validDeviation, validRetention, "maximumBiasRatio"),
                Arguments.of(validBias, null, validRetention, "maximumDeviationRatio"),
                Arguments.of(validBias, new BigDecimal("-0.01"), validRetention, "maximumDeviationRatio"),
                Arguments.of(validBias, new BigDecimal("1.01"), validRetention, "maximumDeviationRatio"),
                Arguments.of(validBias, validDeviation, null, "deviationRetentionRatio"),
                Arguments.of(validBias, validDeviation, new BigDecimal("-0.01"), "deviationRetentionRatio"),
                Arguments.of(validBias, validDeviation, BigDecimal.ONE, "deviationRetentionRatio"),
                Arguments.of(validBias, validDeviation, new BigDecimal("1.01"), "deviationRetentionRatio")
        );
    }
}

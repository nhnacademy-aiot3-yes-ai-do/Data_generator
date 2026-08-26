package site.yesaido.data_generator.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensorObservationDomainValueObjectTest {

    @Test
    @DisplayName("센서 관측 키의 EUI를 정규화한다")
    void normalizeObservationDeviceEui() {
        EnvironmentStateKey environmentStateKey = new EnvironmentStateKey(1L, "TEMPERATURE", "°C");

        SensorObservationKey key = new SensorObservationKey(environmentStateKey, "  sensor-eui-001  ");

        assertThat(key.environmentStateKey()).isEqualTo(environmentStateKey);
        assertThat(key.deviceEui()).isEqualTo("sensor-eui-001");

        assertThat(key).isEqualTo(new SensorObservationKey(new EnvironmentStateKey(
                1L, "TEMPERATURE", "°C"), "sensor-eui-001"));
    }

    @Test
    @DisplayName("환경 키나 EUI가 다르면 서로 다른 센서 관측 키이다")
    void distinguishObservationKeys() {
        EnvironmentStateKey environmentStateKey = new EnvironmentStateKey(1L, "TEMPERATURE", "°C");
        SensorObservationKey base = new SensorObservationKey(environmentStateKey, "sensor-eui-001");

        assertThat(base)
                .isNotEqualTo(new SensorObservationKey(environmentStateKey, "sensor-eui-002"))
                .isNotEqualTo(new SensorObservationKey(new EnvironmentStateKey(
                        2L, "TEMPERATURE", "°C"), "sensor-eui-001"));
    }

    @Test
    @DisplayName("센서 관측 키는 환경 상태 키가 필요하다")
    void rejectNullEnvironmentStateKey() {
        assertThatThrownBy(() -> new SensorObservationKey(null, "sensor-eui-001"))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("environmentStateKey");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("센서 관측 키는 EUI가 필요하다")
    void rejectMissingDeviceEui(String deviceEui) {
        EnvironmentStateKey environmentStateKey = new EnvironmentStateKey(1L, "TEMPERATURE", "°C");

        assertThatThrownBy(() -> new SensorObservationKey(environmentStateKey, deviceEui))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("deviceEui");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-10.0, 0.0, 20.5})
    @DisplayName("센서 관측 상태는 유한한 음수, 0, 양수를 허용한다")
    void allowFiniteObservationValues(double value) {
        SensorObservationState state = new SensorObservationState(
                value,
                value,
                value,
                1L
                );

        assertThat(state.canonicalBias()).isEqualTo(value);
        assertThat(state.canonicalDeviation()).isEqualTo(value);
        assertThat(state.canonicalValue()).isEqualTo(value);
        assertThat(state.lastProjectedCycleId()).isEqualTo(1L);

        assertThat(state).isEqualTo(new SensorObservationState(
                value,
                value,
                value,
                1L
                )
        );
    }

    @ParameterizedTest
    @MethodSource("nonFiniteValues")
    @DisplayName("센서 관측 상태는 유한하지 않은 고정 편향을 거절한다")
    void rejectNonFiniteCanonicalBias(double value) {
        assertThatThrownBy(() -> new SensorObservationState(
                value,
                0.0,
                20.0,
                1L
        ))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("canonicalBias");
    }

    @ParameterizedTest
    @MethodSource("nonFiniteValues")
    @DisplayName("센서 관측 상태는 유한하지 않은 동적 편차를 거절한다")
    void rejectNonFiniteCanonicalDeviation(double value) {
        assertThatThrownBy(() -> new SensorObservationState(
                0.0,
                value,
                20.0,
                1L
        ))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("canonicalDeviation");
    }

    @ParameterizedTest
    @MethodSource("nonFiniteValues")
    @DisplayName("센서 관측 상태는 유한하지 않은 최종값을 거절한다")
    void rejectNonFiniteCanonicalValue(double value) {
        assertThatThrownBy(() -> new SensorObservationState(
                0.0,
                0.0,
                value,
                1L
        ))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("canonicalValue");
    }

    private static Stream<Double> nonFiniteValues() {
        return Stream.of(
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    @DisplayName("마지막 관측 주기 ID는 양수여야 한다")
    void rejectNonPositiveLastProjectedCycleId(long cycleId) {
        assertThatThrownBy(() -> new SensorObservationState(
                0.0,
                0.0,
                20.0,
                cycleId
        ))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("lastProjectedCycleId");
    }
}

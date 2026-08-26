package site.yesaido.data_generator.domain;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvironmentDomainValueObjectTest {

    @Test
    @DisplayName("환경 상태 키의 문자열을 정규화한다")
    void normalizeEnvironmentStateKey() {
        EnvironmentStateKey key = new EnvironmentStateKey(1L, "  TEMPERATURE  ", "  °C  ");
        assertThat(key).isEqualTo(new EnvironmentStateKey(1L, "TEMPERATURE", "°C"));
    }

    @Test
    @DisplayName("환경 상태 키의 구성 요소가 다르면 서로 다른 키이다")
    void distinguishEnvironmentStateKeys() {
        EnvironmentStateKey base = new EnvironmentStateKey(1L, "TEMPERATURE", "°C");
        assertThat(base).isNotEqualTo(new EnvironmentStateKey(2L, "TEMPERATURE", "°C"));
        assertThat(base).isNotEqualTo(new EnvironmentStateKey(1L, "HUMIDITY", "°C"));
        assertThat(base).isNotEqualTo(new EnvironmentStateKey(1L, "TEMPERATURE", "%"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    @DisplayName("환경 상태 키의 재배 ID는 양수여야 한다")
    void rejectNonPositiveCultivationId(long cultivationId) {
        assertThatThrownBy(() -> new EnvironmentStateKey(cultivationId, "TEMPERATURE", "°C"))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("cultivationId");
    }

    @ParameterizedTest(name = "{0}={1}")
    @MethodSource("missingEnvironmentKeyTexts")
    @DisplayName("환경 상태 키의 센서 타입과 표준 단위는 필수이다")
    void rejectMissingEnvironmentKeyText(String fieldName, String invalidValue) {
        ThrowingCallable creation = switch (fieldName) {
            case "sensorType" -> () -> new EnvironmentStateKey(1L, invalidValue, "°C");
            case "canonicalUnit" -> () -> new EnvironmentStateKey(1L, "TEMPERATURE", invalidValue);
            default -> throw new AssertionError("unexpected fieldName=" + fieldName);
        };

        assertThatThrownBy(creation)
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining(fieldName);
    }

    private static Stream<Arguments> missingEnvironmentKeyTexts() {
        return Stream.of("sensorType", "canonicalUnit")
                .flatMap(fieldName -> Stream.of(
                        Arguments.of(fieldName, null),
                        Arguments.of(fieldName, "   ")
                ));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-10.0, 0.0, 20.5})
    @DisplayName("환경 상태는 유한한 음수, 0, 양수를 허용한다")
    void allowFiniteEnvironmentValues(double value) {
        EnvironmentState state = new EnvironmentState(value, 1L);

        assertThat(state.canonicalValue()).isEqualTo(value);
        assertThat(state.lastAdvancedCycleId()).isEqualTo(1L);
    }

    @ParameterizedTest
    @MethodSource("nonFiniteValues")
    @DisplayName("환경 상태는 유한하지 않은 값을 거절한다")
    void rejectNonFiniteEnvironmentValue(double value) {
        assertThatThrownBy(() -> new EnvironmentState(value, 1L))
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
    @DisplayName("마지막 생성 주기 ID는 양수여야 한다")
    void rejectNonPositiveLastAdvancedCycleId(long cycleId) {
        assertThatThrownBy(() -> new EnvironmentState(20.0, cycleId))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("lastAdvancedCycleId");
    }
}

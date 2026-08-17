package site.yesaido.data_generator.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import site.yesaido.data_generator.exception.ActuatorStateException;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActuatorDomainTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-08-17T06:00:00Z");

    @Test
    @DisplayName("유효한 액추에이터 상태 항목을 생성한다")
    void createValidActuatorStateEntry() {
        ActuatorStateEntry entry = new ActuatorStateEntry(ActuatorState.ON, REQUESTED_AT);

        assertThat(entry.actualState()).isEqualTo(ActuatorState.ON);
        assertThat(entry.lastRequestedAt()).isEqualTo(REQUESTED_AT);
    }

    @Test
    @DisplayName("액추에이터 상태 항목의 필수 값이 null이면 예외가 발생한다")
    void rejectInvalidActuatorStateEntry() {
        assertThatThrownBy(() -> new ActuatorStateEntry(null, REQUESTED_AT))
                .isInstanceOf(ActuatorStateException.class)
                .hasMessageContaining("actualState");

        assertThatThrownBy(() -> new ActuatorStateEntry(ActuatorState.OFF, null))
                .isInstanceOf(ActuatorStateException.class)
                .hasMessageContaining("lastRequestedAt");
    }

    @Test
    @DisplayName("유효한 재배와 액추에이터 타입으로 상태 키를 생성한다")
    void createValidActuatorStateKey() {
        ActuatorStateKey key = new ActuatorStateKey(1L, ActuatorType.HEATER);

        assertThat(key.cultivationId()).isEqualTo(1L);
        assertThat(key.actuatorType()).isEqualTo(ActuatorType.HEATER);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    @DisplayName("재배 ID가 양수가 아니면 상태 키를 생성할 수 없다")
    void rejectNonPositiveCultivationId(long cultivationId) {
        assertThatThrownBy(() -> new ActuatorStateKey(cultivationId, ActuatorType.HEATER))
                .isInstanceOf(ActuatorStateException.class)
                .hasMessageContaining("cultivationId");
    }

    @Test
    @DisplayName("액추에이터 타입이 null이면 상태 키를 생성할 수 없다")
    void rejectNullActuatorType() {
        assertThatThrownBy(() -> new ActuatorStateKey(1L, null))
                .isInstanceOf(ActuatorStateException.class)
                .hasMessageContaining("actuatorType");
    }

    @ParameterizedTest(name = "{0} <-> {1}")
    @MethodSource("oppositeActuatorPairs")
    @DisplayName("모든 액추에이터는 반대 타입과 동일한 센서에 반대 효과를 준다")
    void exposeOppositeActuatorContract(
            ActuatorType actuatorType,
            ActuatorType oppositeType,
            String targetSensorType,
            double effectAmount
    ) {
        assertThat(actuatorType.getOppositeType()).isEqualTo(oppositeType);
        assertThat(actuatorType.getTargetSensorType()).isEqualTo(targetSensorType);
        assertThat(actuatorType.getEffectAmount()).isEqualTo(effectAmount);

        assertThat(oppositeType.getOppositeType()).isEqualTo(actuatorType);
        assertThat(oppositeType.getTargetSensorType()).isEqualTo(targetSensorType);
        assertThat(oppositeType.getEffectAmount()).isEqualTo(-effectAmount);
    }

    private static Stream<Arguments> oppositeActuatorPairs() {
        return Stream.of(
                Arguments.of(ActuatorType.HEATER, ActuatorType.COOLER,
                        "TEMPERATURE", 0.5),
                Arguments.of(ActuatorType.COOLER, ActuatorType.HEATER,
                        "TEMPERATURE", -0.5),
                Arguments.of(ActuatorType.HUMIDIFIER, ActuatorType.DEHUMIDIFIER,
                        "HUMIDITY", 2.0),
                Arguments.of(ActuatorType.DEHUMIDIFIER, ActuatorType.HUMIDIFIER,
                        "HUMIDITY", -2.0),
                Arguments.of(ActuatorType.CO2_SUPPLIER, ActuatorType.VENTILATION_FAN,
                        "CO2", 60.0),
                Arguments.of(ActuatorType.VENTILATION_FAN, ActuatorType.CO2_SUPPLIER,
                        "CO2", -60.0),
                Arguments.of(ActuatorType.LED, ActuatorType.LIGHT_REDUCER,
                        "LIGHT", 50.0),
                Arguments.of(ActuatorType.LIGHT_REDUCER, ActuatorType.LED,
                        "LIGHT", -50.0)
        );
    }
}

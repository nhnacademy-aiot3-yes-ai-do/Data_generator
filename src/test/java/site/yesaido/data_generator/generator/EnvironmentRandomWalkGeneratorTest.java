package site.yesaido.data_generator.generator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import site.yesaido.data_generator.domain.EnvironmentState;
import site.yesaido.data_generator.domain.EnvironmentStateKey;
import site.yesaido.data_generator.domain.MeasurementConfiguration;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import java.util.random.RandomGenerator;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EnvironmentRandomWalkGeneratorTest {

    private static final EnvironmentStateKey TEMPERATURE_KEY =
            new EnvironmentStateKey(
                    1L,
                    "TEMPERATURE",
                    "°C"
            );

    private RandomGenerator randomGenerator;
    private EnvironmentRandomWalkGenerator generator;

    @BeforeEach
    void setUp() {
        randomGenerator = mock(RandomGenerator.class);
        generator = new EnvironmentRandomWalkGenerator(randomGenerator);
    }

    @Test
    @DisplayName("난수 생성기가 null이면 환경 생성기를 만들 수 없다")
    void rejectNullRandomGenerator() {
        assertThatThrownBy(() -> new EnvironmentRandomWalkGenerator(null))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("randomGenerator");
    }

    @Test
    @DisplayName("최초 생성 주기는 초기값과 액추에이터 효과로 시작한다")
    void initializeFirstCycle() {
        EnvironmentState state = generator.advance(TEMPERATURE_KEY, createMovableConfiguration(), 2.0, 1L);

        assertThat(state.canonicalValue()).isEqualTo(52.0);
        assertThat(state.lastAdvancedCycleId()).isEqualTo(1L);

        verifyNoInteractions(randomGenerator);
    }

    @Test
    @DisplayName("새 생성 주기에는 랜덤 변화량과 액추에이터 효과를 적용한다")
    void advanceNewCycle() {
        generator.advance(TEMPERATURE_KEY, createMovableConfiguration(), 0.0, 1L);

        when(randomGenerator.nextDouble(-1.0, 1.0)).thenReturn(0.5);

        EnvironmentState state = generator.advance(
                TEMPERATURE_KEY, createMovableConfiguration(), 2.0, 2L);

        assertThat(state.canonicalValue()).isEqualTo(52.5);
        assertThat(state.lastAdvancedCycleId()).isEqualTo(2L);

        verify(randomGenerator).nextDouble(-1.0, 1.0);
    }

    @Test
    @DisplayName("같은 생성 주기는 환경값과 액추에이터 효과를 다시 적용하지 않는다")
    void reuseStateForSameCycle() {
        generator.advance(TEMPERATURE_KEY, createMovableConfiguration(), 0.0, 1L);

        when(randomGenerator.nextDouble(-1.0, 1.0)).thenReturn(0.5);

        EnvironmentState firstResult = generator.advance(
                TEMPERATURE_KEY, createMovableConfiguration(), 2.0, 2L);

        EnvironmentState repeatedResult = generator.advance(
                TEMPERATURE_KEY, createMovableConfiguration(), -50.0, 2L);

        assertThat(repeatedResult).isEqualTo(firstResult);
        assertThat(repeatedResult.canonicalValue())
                .isEqualTo(52.5);

        verify(randomGenerator)
                .nextDouble(-1.0, 1.0);
    }

    @Test
    @DisplayName("더 오래된 생성 주기는 거절하고 최신 상태를 유지한다")
    void rejectStaleCycle() {
        MeasurementConfiguration configuration = createMovableConfiguration();

        generator.advance(TEMPERATURE_KEY, configuration, 0.0, 1L);

        when(randomGenerator.nextDouble(-1.0, 1.0)).thenReturn(0.5);

        EnvironmentState latestState = generator.advance(
                TEMPERATURE_KEY, configuration, 0.0, 3L);

        assertThatThrownBy(() -> generator.advance(
                TEMPERATURE_KEY, configuration, 0.0, 2L))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("lastAdvancedCycleId");

        EnvironmentState retainedState = generator.advance(
                TEMPERATURE_KEY, configuration, 0.0, 3L);

        assertThat(retainedState).isEqualTo(latestState);

        verify(randomGenerator).nextDouble(-1.0, 1.0);
    }

    @Test
    @DisplayName("서로 다른 환경 키의 상태는 독립적으로 관리한다")
    void isolateStateByEnvironmentKey() {
        EnvironmentStateKey humidityKey =
                new EnvironmentStateKey(
                        1L,
                        "HUMIDITY",
                        "%"
                );

        generator.advance(TEMPERATURE_KEY, createMovableConfiguration(), 2.0, 1L);
        generator.advance(humidityKey, createMovableConfiguration(), -3.0, 1L);

        when(randomGenerator.nextDouble(-1.0, 1.0)).thenReturn(0.5, -0.5);

        EnvironmentState temperatureState = generator.advance(
                TEMPERATURE_KEY, createMovableConfiguration(), 0.0, 2L);

        EnvironmentState humidityState = generator.advance(
                humidityKey, createMovableConfiguration(), 0.0, 2L);

        assertThat(temperatureState.canonicalValue()).isEqualTo(52.5);
        assertThat(humidityState.canonicalValue()).isEqualTo(46.5);

        verify(randomGenerator, times(2)).nextDouble(-1.0, 1.0);
    }

    @Test
    @DisplayName("정확한 환경 상태를 삭제하면 초기값부터 다시 시작한다")
    void removeExactEnvironmentState() {
        MeasurementConfiguration configuration =
                createFixedConfiguration();

        generator.advance(TEMPERATURE_KEY, configuration, 2.0, 1L);
        generator.advance(TEMPERATURE_KEY, configuration, 2.0, 2L);

        generator.removeState(TEMPERATURE_KEY);

        EnvironmentState resetState = generator.advance(
                TEMPERATURE_KEY, configuration, 0.0, 3L);

        assertThat(resetState.canonicalValue()).isEqualTo(50.0);

        verifyNoInteractions(randomGenerator);
    }

    @Test
    @DisplayName("재배 환경 상태 삭제는 다른 재배의 상태를 유지한다")
    void removeOnlyRequestedCultivationStates() {
        EnvironmentStateKey cultivationOneHumidity =
                new EnvironmentStateKey(
                        1L,
                        "HUMIDITY",
                        "%"
                );

        EnvironmentStateKey cultivationTwoTemperature =
                new EnvironmentStateKey(
                        2L,
                        "TEMPERATURE",
                        "°C"
                );

        MeasurementConfiguration configuration =
                createFixedConfiguration();

        generator.advance(TEMPERATURE_KEY, configuration, 2.0, 1L);
        generator.advance(cultivationOneHumidity, configuration, -3.0, 1L);
        generator.advance(cultivationTwoTemperature, configuration, 4.0, 1L);
        generator.removeStatesByCultivationId(1L);

        EnvironmentState resetTemperature = generator.advance(
                TEMPERATURE_KEY, configuration, 0.0, 2L);

        EnvironmentState resetHumidity = generator.advance(
                cultivationOneHumidity, configuration, 0.0, 2L);

        EnvironmentState retainedCultivationTwo =
                generator.advance(cultivationTwoTemperature, configuration, 0.0, 2L);

        assertThat(resetTemperature.canonicalValue()).isEqualTo(50.0);
        assertThat(resetHumidity.canonicalValue()).isEqualTo(50.0);
        assertThat(retainedCultivationTwo.canonicalValue()).isEqualTo(54.0);

        verifyNoInteractions(randomGenerator);
    }

    @Test
    @DisplayName("필수 생성 인자가 null이면 환경값 생성을 거절한다")
    void rejectNullGenerationArguments() {
        MeasurementConfiguration configuration =
                createMovableConfiguration();

        assertThatThrownBy(() -> generator.advance(
                null, configuration, 0.0, 1L))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("environmentStateKey");

        assertThatThrownBy(() -> generator.advance(
                TEMPERATURE_KEY, null, 0.0, 1L))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("configuration");

        assertThatThrownBy(() -> generator.removeState(null))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("environmentStateKey");
    }

    @ParameterizedTest
    @MethodSource("nonFiniteActuatorEffects")
    @DisplayName("유한하지 않은 액추에이터 효과를 거절한다")
    void rejectNonFiniteActuatorEffect(double actuatorEffect) {
        MeasurementConfiguration configuration = createMovableConfiguration();

        assertThatThrownBy(() -> generator.advance(
                TEMPERATURE_KEY, configuration, actuatorEffect, 1L))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("actuatorEffectAmount");
    }

    private static Stream<Double> nonFiniteActuatorEffects() {
        return Stream.of(
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    @DisplayName("생성 주기 ID는 양수여야 한다")
    void rejectNonPositiveCycleId(long cycleId) {
        MeasurementConfiguration configuration = createMovableConfiguration();

        assertThatThrownBy(() -> generator.advance(
                TEMPERATURE_KEY, configuration, 0.0, cycleId))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("cycleId");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    @DisplayName("삭제할 재배 ID는 양수여야 한다")
    void rejectNonPositiveCultivationId(long cultivationId) {
        assertThatThrownBy(
                () -> generator.removeStatesByCultivationId(cultivationId))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("cultivationId");
    }

    @Test
    @DisplayName("동일 환경 키와 주기의 동시 요청은 한 번만 환경값을 변경한다")
    void advanceSameEnvironmentAndCycleOnlyOnceConcurrently()
            throws Exception {
        generator.advance(TEMPERATURE_KEY, createMovableConfiguration(), 0.0, 1L);

        CountDownLatch randomCallStarted = new CountDownLatch(1);
        CountDownLatch allowRandomCallToFinish = new CountDownLatch(1);

        when(randomGenerator.nextDouble(-1.0, 1.0)).thenAnswer(invocation -> {
                    randomCallStarted.countDown();

                    if (!allowRandomCallToFinish.await(5, SECONDS)) {
                        throw new AssertionError("난수 호출 대기 시간이 초과되었습니다.");
                    }

                    return 0.5;
                });

        EnvironmentState firstResult;
        EnvironmentState secondResult;

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<EnvironmentState> firstFuture = executor.submit(() -> generator.advance(
                    TEMPERATURE_KEY, createMovableConfiguration(), 0.0, 2L));

            boolean randomCallObserved = randomCallStarted.await(5, SECONDS);

            Future<EnvironmentState> secondFuture = executor.submit(() -> generator.advance(
                    TEMPERATURE_KEY, createMovableConfiguration(), 0.0, 2L));

            allowRandomCallToFinish.countDown();

            assertThat(randomCallObserved).isTrue();

            firstResult = firstFuture.get(5, SECONDS);
            secondResult = secondFuture.get(5, SECONDS);
        }

        assertThat(firstResult).isEqualTo(secondResult);
        assertThat(firstResult.canonicalValue()).isEqualTo(50.5);

        verify(randomGenerator).nextDouble(-1.0, 1.0);
    }

    private static MeasurementConfiguration
    createMovableConfiguration() {
        return new MeasurementConfiguration(
                50.0,
                0.0,
                100.0,
                1.0,
                2
        );
    }

    private static MeasurementConfiguration
    createFixedConfiguration() {
        return new MeasurementConfiguration(
                50.0,
                0.0,
                100.0,
                0.0,
                2
        );
    }
}

package site.yesaido.data_generator.generator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import site.yesaido.data_generator.domain.EnvironmentState;
import site.yesaido.data_generator.domain.EnvironmentStateKey;
import site.yesaido.data_generator.domain.MeasurementConfiguration;
import site.yesaido.data_generator.domain.SensorObservationKey;
import site.yesaido.data_generator.domain.SensorObservationPolicy;
import site.yesaido.data_generator.domain.SensorObservationState;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.random.RandomGenerator;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SensorObservationProjectorTest {

    private static final SensorObservationKey SENSOR_A_KEY =
            createObservationKey(
                    1L,
                    "sensor-eui-a"
            );

    private static final SensorObservationKey SENSOR_B_KEY =
            createObservationKey(
                    1L,
                    "sensor-eui-b"
            );

    private RandomGenerator randomGenerator;
    private SensorObservationProjector projector;

    @BeforeEach
    void setUp() {
        randomGenerator = mock(RandomGenerator.class);

        projector = new SensorObservationProjector(
                randomGenerator,
                createPolicy(
                        "0.1",
                        "0.2",
                        "0.5"
                )
        );
    }

    @Test
    @DisplayName("최초 주기에는 편향과 편차를 만들고 다음 주기에는 편향을 유지한다")
    void projectInitialAndNextCycle() {
        when(randomGenerator.nextDouble(-1.0, 1.0))
                .thenReturn(
                        0.5,
                        -0.5,
                        0.25
                );

        MeasurementConfiguration configuration =
                createStandardConfiguration();

        SensorObservationState firstState =
                projector.project(
                        SENSOR_A_KEY,
                        new EnvironmentState(50.0, 1L),
                        configuration
                );

        SensorObservationState secondState =
                projector.project(
                        SENSOR_A_KEY,
                        new EnvironmentState(51.0, 2L),
                        configuration
                );

        /*
         * range span = 100
         *
         * bias maximum = 100 × 0.1 = 10
         * initial bias = 0.5 × 10 = 5
         *
         * deviation maximum = 100 × 0.2 = 20
         * initial target = -0.5 × 20 = -10
         * initial deviation = 0 × 0.5 + -10 × 0.5 = -5
         *
         * first value = 50 + 5 - 5 = 50
         */
        assertThat(firstState.canonicalBias())
                .isEqualTo(5.0);
        assertThat(firstState.canonicalDeviation())
                .isEqualTo(-5.0);
        assertThat(firstState.canonicalValue())
                .isEqualTo(50.0);
        assertThat(firstState.lastProjectedCycleId())
                .isEqualTo(1L);

        /*
         * second target = 0.25 × 20 = 5
         * second deviation = -5 × 0.5 + 5 × 0.5 = 0
         * second value = 51 + 5 + 0 = 56
         */
        assertThat(secondState.canonicalBias())
                .isEqualTo(5.0);
        assertThat(secondState.canonicalDeviation())
                .isEqualTo(0.0);
        assertThat(secondState.canonicalValue())
                .isEqualTo(56.0);
        assertThat(secondState.lastProjectedCycleId())
                .isEqualTo(2L);

        verify(randomGenerator, times(3))
                .nextDouble(-1.0, 1.0);
    }

    @Test
    @DisplayName("같은 주기는 기존 상태를 반환하고 오래된 주기는 거절한다")
    void reuseSameCycleAndRejectStaleCycle() {
        when(randomGenerator.nextDouble(-1.0, 1.0))
                .thenReturn(
                        0.5,
                        -0.5
                );

        MeasurementConfiguration configuration =
                createStandardConfiguration();

        SensorObservationState originalState =
                projector.project(
                        SENSOR_A_KEY,
                        new EnvironmentState(50.0, 2L),
                        configuration
                );

        SensorObservationState repeatedState =
                projector.project(
                        SENSOR_A_KEY,
                        new EnvironmentState(90.0, 2L),
                        configuration
                );

        assertThat(repeatedState)
                .isSameAs(originalState);

        EnvironmentState staleEnvironmentState =
                new EnvironmentState(40.0, 1L);

        assertThatThrownBy(() -> projector.project(
                SENSOR_A_KEY,
                staleEnvironmentState,
                configuration
        ))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("lastProjectedCycleId");

        SensorObservationState retainedState =
                projector.project(
                        SENSOR_A_KEY,
                        new EnvironmentState(50.0, 2L),
                        configuration
                );

        assertThat(retainedState)
                .isSameAs(originalState);

        verify(randomGenerator, times(2))
                .nextDouble(-1.0, 1.0);
    }

    @Test
    @DisplayName("측정 가능 범위 폭이 0이면 난수를 호출하지 않는다")
    void skipRandomGenerationForZeroRangeSpan() {
        MeasurementConfiguration fixedConfiguration =
                new MeasurementConfiguration(
                        42.0,
                        42.0,
                        42.0,
                        0.0,
                        2
                );

        SensorObservationState state =
                projector.project(
                        SENSOR_A_KEY,
                        new EnvironmentState(42.0, 1L),
                        fixedConfiguration
                );

        assertThat(state.canonicalBias())
                .isZero();
        assertThat(state.canonicalDeviation())
                .isZero();
        assertThat(state.canonicalValue())
                .isEqualTo(42.0);

        verifyNoInteractions(randomGenerator);
    }

    @Test
    @DisplayName("BigDecimal 유지율이 double 1로 반올림되면 생성기를 만들 수 없다")
    void rejectRetentionRatioRoundedToOne() {
        SensorObservationPolicy roundedPolicy =
                createPolicy(
                        "0.1",
                        "0.2",
                        "0.999999999999999999999999999999999999"
                );

        assertThatThrownBy(() -> new SensorObservationProjector(
                randomGenerator,
                roundedPolicy
        ))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("deviationRetentionRatio");
    }

    @Test
    @DisplayName("필수 생성자 인자가 null이면 생성기를 만들 수 없다")
    void rejectNullConstructorArguments() {
        SensorObservationPolicy policy =
                createPolicy(
                        "0.1",
                        "0.2",
                        "0.5"
                );

        assertThatThrownBy(() -> new SensorObservationProjector(
                null,
                policy
        ))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("randomGenerator");

        assertThatThrownBy(() -> new SensorObservationProjector(
                randomGenerator,
                null
        ))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("policy");
    }

    @Test
    @DisplayName("측정 가능 범위 폭 계산이 overflow되면 상태를 만들지 않는다")
    void rejectOverflowingRangeSpan() {
        MeasurementConfiguration overflowingConfiguration =
                new MeasurementConfiguration(
                        0.0,
                        -Double.MAX_VALUE,
                        Double.MAX_VALUE,
                        0.0,
                        2
                );

        EnvironmentState environmentState =
                new EnvironmentState(0.0, 1L);

        assertThatThrownBy(() -> projector.project(
                SENSOR_A_KEY,
                environmentState,
                overflowingConfiguration
        ))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("범위 폭");

        verifyNoInteractions(randomGenerator);

        when(randomGenerator.nextDouble(-1.0, 1.0))
                .thenReturn(
                        0.5,
                        -0.5
                );

        SensorObservationState validState =
                projector.project(
                        SENSOR_A_KEY,
                        new EnvironmentState(50.0, 1L),
                        createStandardConfiguration()
                );

        assertThat(validState.lastProjectedCycleId())
                .isEqualTo(1L);

        verify(randomGenerator, times(2))
                .nextDouble(-1.0, 1.0);
    }

    @Test
    @DisplayName("범위가 축소되면 이전 동적 편차를 새로운 범위로 제한한다")
    void clampPreviousDeviationAfterRangeShrink() {
        projector = new SensorObservationProjector(
                randomGenerator,
                createPolicy(
                        "0.0",
                        "0.5",
                        "0.5"
                )
        );

        when(randomGenerator.nextDouble(-1.0, 1.0))
                .thenReturn(
                        0.8,
                        0.0
                );

        MeasurementConfiguration largeConfiguration =
                new MeasurementConfiguration(
                        50.0,
                        0.0,
                        100.0,
                        0.0,
                        2
                );

        SensorObservationState largeRangeState =
                projector.project(
                        SENSOR_A_KEY,
                        new EnvironmentState(10.0, 1L),
                        largeConfiguration
                );

        /*
         * maximum deviation = 100 × 0.5 = 50
         * target = 0.8 × 50 = 40
         * deviation = 0 × 0.5 + 40 × 0.5 = 20
         */
        assertThat(largeRangeState.canonicalDeviation())
                .isEqualTo(20.0);

        MeasurementConfiguration narrowConfiguration =
                new MeasurementConfiguration(
                        10.0,
                        0.0,
                        20.0,
                        0.0,
                        3
                );

        SensorObservationState narrowRangeState =
                projector.project(
                        SENSOR_A_KEY,
                        new EnvironmentState(10.0, 2L),
                        narrowConfiguration
                );

        /*
         * new maximum deviation = 20 × 0.5 = 10
         * previous deviation 20을 10으로 먼저 제한
         * target = 0
         * next deviation = 10 × 0.5 + 0 × 0.5 = 5
         */
        assertThat(narrowRangeState.canonicalDeviation())
                .isCloseTo(
                        5.0,
                        within(0.000_000_001)
                );
        assertThat(narrowRangeState.canonicalValue())
                .isEqualTo(15.0);

        verify(randomGenerator, times(2))
                .nextDouble(-1.0, 1.0);
    }

    @Test
    @DisplayName("정확한 EUI 관측 상태만 삭제한다")
    void removeExactObservationState() {
        projector = createZeroOffsetProjector();

        SensorObservationState sensorBState =
                projector.project(
                        SENSOR_B_KEY,
                        new EnvironmentState(50.0, 2L),
                        createStandardConfiguration()
                );

        projector.project(
                SENSOR_A_KEY,
                new EnvironmentState(50.0, 2L),
                createStandardConfiguration()
        );

        projector.removeState(SENSOR_A_KEY);

        SensorObservationState restartedSensorA =
                projector.project(
                        SENSOR_A_KEY,
                        new EnvironmentState(60.0, 2L),
                        createStandardConfiguration()
                );

        SensorObservationState retainedSensorB =
                projector.project(
                        SENSOR_B_KEY,
                        new EnvironmentState(60.0, 2L),
                        createStandardConfiguration()
                );

        assertThat(restartedSensorA.canonicalValue())
                .isEqualTo(60.0);
        assertThat(retainedSensorB)
                .isSameAs(sensorBState);
        assertThat(retainedSensorB.canonicalValue())
                .isEqualTo(50.0);

        verifyNoInteractions(randomGenerator);
    }

    @Test
    @DisplayName("재배 관측 상태 삭제는 다른 재배의 상태를 유지한다")
    void removeOnlyRequestedCultivationStates() {
        projector = createZeroOffsetProjector();

        SensorObservationKey cultivationOneKey =
                createObservationKey(
                        1L,
                        "sensor-eui-a"
                );

        SensorObservationKey cultivationTwoKey =
                createObservationKey(
                        2L,
                        "sensor-eui-a"
                );

        projector.project(
                cultivationOneKey,
                new EnvironmentState(50.0, 2L),
                createStandardConfiguration()
        );

        SensorObservationState cultivationTwoState =
                projector.project(
                        cultivationTwoKey,
                        new EnvironmentState(50.0, 2L),
                        createStandardConfiguration()
                );

        projector.removeStatesByCultivationId(1L);

        SensorObservationState restartedCultivationOne =
                projector.project(
                        cultivationOneKey,
                        new EnvironmentState(60.0, 2L),
                        createStandardConfiguration()
                );

        SensorObservationState retainedCultivationTwo =
                projector.project(
                        cultivationTwoKey,
                        new EnvironmentState(60.0, 2L),
                        createStandardConfiguration()
                );

        assertThat(restartedCultivationOne.canonicalValue())
                .isEqualTo(60.0);
        assertThat(retainedCultivationTwo)
                .isSameAs(cultivationTwoState);
        assertThat(retainedCultivationTwo.canonicalValue())
                .isEqualTo(50.0);

        verifyNoInteractions(randomGenerator);
    }

    @Test
    @DisplayName("최종 센서 관측값에 반올림과 범위 제한을 적용한다")
    void normalizeFinalObservationValue() {
        projector = createZeroOffsetProjector();

        MeasurementConfiguration configuration =
                createStandardConfiguration();

        SensorObservationState roundedState =
                projector.project(
                        SENSOR_A_KEY,
                        new EnvironmentState(50.005, 1L),
                        configuration
                );

        SensorObservationState clampedState =
                projector.project(
                        SENSOR_A_KEY,
                        new EnvironmentState(150.0, 2L),
                        configuration
                );

        assertThat(roundedState.canonicalValue())
                .isEqualTo(50.01);
        assertThat(clampedState.canonicalValue())
                .isEqualTo(100.0);

        verifyNoInteractions(randomGenerator);
    }

    @Test
    @DisplayName("필수 투영 인자가 null이면 관측값 생성을 거절한다")
    void rejectNullProjectionArguments() {
        EnvironmentState environmentState =
                new EnvironmentState(50.0, 1L);

        MeasurementConfiguration configuration =
                createStandardConfiguration();

        assertThatThrownBy(() -> projector.project(
                null,
                environmentState,
                configuration
        ))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("observationKey");

        assertThatThrownBy(() -> projector.project(
                SENSOR_A_KEY,
                null,
                configuration
        ))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("environmentState");

        assertThatThrownBy(() -> projector.project(
                SENSOR_A_KEY,
                environmentState,
                null
        ))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("configuration");

        assertThatThrownBy(() -> projector.removeState(null))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("observationKey");

        verifyNoInteractions(randomGenerator);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    @DisplayName("삭제할 재배 ID는 양수여야 한다")
    void rejectInvalidCultivationId(long cultivationId) {
        assertThatThrownBy(
                () -> projector.removeStatesByCultivationId(
                        cultivationId
                )
        )
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("cultivationId");

        verifyNoInteractions(randomGenerator);
    }

    @Test
    @DisplayName("동일 키와 주기의 동시 투영은 한 번만 계산한다")
    void projectSameKeyAndCycleOnlyOnceConcurrently()
            throws Exception {
        projector = new SensorObservationProjector(
                randomGenerator,
                createPolicy(
                        "0.0",
                        "0.2",
                        "0.5"
                )
        );

        CountDownLatch randomCallEntered =
                new CountDownLatch(1);

        CountDownLatch releaseRandomCall =
                new CountDownLatch(1);

        when(randomGenerator.nextDouble(-1.0, 1.0))
                .thenAnswer(invocation -> {
                    randomCallEntered.countDown();

                    if (!releaseRandomCall.await(
                            5L,
                            SECONDS
                    )) {
                        throw new AssertionError(
                                "난수 호출 대기 시간이 초과되었습니다."
                        );
                    }

                    return 0.5;
                });

        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        try {
            Future<SensorObservationState> firstFuture =
                    executorService.submit(
                            () -> projector.project(
                                    SENSOR_A_KEY,
                                    new EnvironmentState(50.0, 1L),
                                    createStandardConfiguration()
                            )
                    );

            assertThat(randomCallEntered.await(5L, SECONDS))
                    .isTrue();

            Future<SensorObservationState> secondFuture =
                    executorService.submit(
                            () -> projector.project(
                                    SENSOR_A_KEY,
                                    new EnvironmentState(50.0, 1L),
                                    createStandardConfiguration()
                            )
                    );

            releaseRandomCall.countDown();

            SensorObservationState firstResult =
                    firstFuture.get(5L, SECONDS);

            SensorObservationState secondResult =
                    secondFuture.get(5L, SECONDS);

            assertThat(secondResult)
                    .isSameAs(firstResult);

            verify(randomGenerator)
                    .nextDouble(-1.0, 1.0);
        } finally {
            releaseRandomCall.countDown();
            executorService.shutdownNow();
        }
    }

    private SensorObservationProjector
    createZeroOffsetProjector() {
        return new SensorObservationProjector(
                randomGenerator,
                createPolicy(
                        "0.0",
                        "0.0",
                        "0.5"
                )
        );
    }

    private static SensorObservationPolicy createPolicy(
            String maximumBiasRatio,
            String maximumDeviationRatio,
            String deviationRetentionRatio
    ) {
        return new SensorObservationPolicy(
                new BigDecimal(maximumBiasRatio),
                new BigDecimal(maximumDeviationRatio),
                new BigDecimal(deviationRetentionRatio)
        );
    }

    private static SensorObservationKey createObservationKey(
            long cultivationId,
            String deviceEui
    ) {
        return new SensorObservationKey(
                new EnvironmentStateKey(
                        cultivationId,
                        "TEMPERATURE",
                        "°C"
                ),
                deviceEui
        );
    }

    private static MeasurementConfiguration
    createStandardConfiguration() {
        return new MeasurementConfiguration(
                50.0,
                0.0,
                100.0,
                1.0,
                2
        );
    }
}

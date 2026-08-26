package site.yesaido.data_generator.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.data_generator.converter.SensorUnitConverter;
import site.yesaido.data_generator.domain.EnvironmentState;
import site.yesaido.data_generator.domain.EnvironmentStateKey;
import site.yesaido.data_generator.domain.MeasurementConfiguration;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.domain.SensorGenerationPlan;
import site.yesaido.data_generator.domain.SensorObservationKey;
import site.yesaido.data_generator.domain.SensorObservationState;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

@ExtendWith(MockitoExtension.class)
class SharedEnvironmentSensorValueGeneratorTest {

    private static final long CULTIVATION_ID = 1L;
    private static final long CYCLE_ID = 7L;
    private static final double ACTUATOR_EFFECT = 0.5;
    private static final double OBSERVED_CANONICAL_VALUE = 20.3;

    private static final MeasurementConfiguration CONFIGURATION =
            new MeasurementConfiguration(
                    16.0,
                    10.0,
                    30.0,
                    0.3,
                    1
            );

    @Mock
    private SensorGenerationPlanResolver sensorGenerationPlanResolver;

    @Mock
    private EnvironmentRandomWalkGenerator environmentRandomWalkGenerator;

    @Mock
    private SensorObservationProjector sensorObservationProjector;

    @Mock
    private SensorUnitConverter sensorUnitConverter;

    private SharedEnvironmentSensorValueGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SharedEnvironmentSensorValueGenerator(
                sensorGenerationPlanResolver,
                environmentRandomWalkGenerator,
                sensorObservationProjector,
                sensorUnitConverter
        );
    }

    @Test
    void convertFixedSensorObservationToRequestedUnit() {
        SensorChannelKey channelKey =
                new SensorChannelKey(
                        "device-A",
                        "TEMPERATURE",
                        "°F"
                );

        SensorGenerationPlan plan =
                createPlan(
                        CULTIVATION_ID,
                        "TEMPERATURE",
                        "°C"
                );

        stubGenerationPipeline(
                channelKey,
                plan,
                OBSERVED_CANONICAL_VALUE
        );

        when(sensorUnitConverter.convertFromCanonical(
                "TEMPERATURE",
                "°F",
                OBSERVED_CANONICAL_VALUE
        )).thenReturn(Optional.of(68.5));

        Optional<Number> result =
                generator.generateNextValue(
                        CULTIVATION_ID,
                        channelKey,
                        ACTUATOR_EFFECT,
                        CYCLE_ID
                );

        assertThat(result).hasValueSatisfying(
                value -> assertThat(value.doubleValue())
                        .isEqualTo(68.5)
        );

        EnvironmentStateKey environmentStateKey =
                plan.environmentStateKey();

        verify(environmentRandomWalkGenerator).advance(
                environmentStateKey,
                CONFIGURATION,
                ACTUATOR_EFFECT,
                CYCLE_ID
        );

        verify(sensorObservationProjector).project(
                new SensorObservationKey(
                        environmentStateKey,
                        "device-A"
                ),
                new EnvironmentState(20.0, CYCLE_ID),
                CONFIGURATION
        );

        verify(sensorUnitConverter).convertFromCanonical(
                "TEMPERATURE",
                "°F",
                OBSERVED_CANONICAL_VALUE
        );
    }

    @Test
    void returnDynamicObservationWhenCanonicalUnitMatchesRequestedUnit() {
        SensorChannelKey channelKey =
                new SensorChannelKey(
                        "device-A",
                        "SOIL_MOISTURE",
                        "%"
                );

        SensorGenerationPlan plan =
                createPlan(
                        CULTIVATION_ID,
                        "SOIL_MOISTURE",
                        "%"
                );

        stubGenerationPipeline(
                channelKey,
                plan,
                OBSERVED_CANONICAL_VALUE
        );

        when(sensorUnitConverter.convertFromCanonical(
                "SOIL_MOISTURE",
                "%",
                OBSERVED_CANONICAL_VALUE
        )).thenReturn(Optional.empty());

        when(sensorUnitConverter
                .findCanonicalUnit("SOIL_MOISTURE"))
                .thenReturn(Optional.empty());

        Optional<Number> result =
                generator.generateNextValue(
                        CULTIVATION_ID,
                        channelKey,
                        ACTUATOR_EFFECT,
                        CYCLE_ID
                );

        assertThat(result).hasValueSatisfying(
                value -> assertThat(value.doubleValue())
                        .isEqualTo(OBSERVED_CANONICAL_VALUE)
        );
    }

    @Test
    void rejectFailedFixedConversionEvenWhenUnitsMatch() {
        SensorChannelKey channelKey =
                new SensorChannelKey(
                        "device-A",
                        "TEMPERATURE",
                        "°C"
                );

        SensorGenerationPlan plan =
                createPlan(
                        CULTIVATION_ID,
                        "TEMPERATURE",
                        "°C"
                );

        stubGenerationPipeline(
                channelKey,
                plan,
                OBSERVED_CANONICAL_VALUE
        );

        when(sensorUnitConverter.convertFromCanonical(
                "TEMPERATURE",
                "°C",
                OBSERVED_CANONICAL_VALUE
        )).thenReturn(Optional.empty());

        when(sensorUnitConverter
                .findCanonicalUnit("TEMPERATURE"))
                .thenReturn(Optional.of("°C"));

        assertThatThrownBy(() ->
                generator.generateNextValue(
                        CULTIVATION_ID,
                        channelKey,
                        ACTUATOR_EFFECT,
                        CYCLE_ID
                ))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("변환할 수 없습니다");
    }

    @Test
    void rejectDynamicIdentityFallbackWhenUnitsDiffer() {
        SensorChannelKey channelKey =
                new SensorChannelKey(
                        "device-A",
                        "SOIL_MOISTURE",
                        "ppm"
                );

        SensorGenerationPlan plan =
                createPlan(
                        CULTIVATION_ID,
                        "SOIL_MOISTURE",
                        "%"
                );

        stubGenerationPipeline(
                channelKey,
                plan,
                OBSERVED_CANONICAL_VALUE
        );

        when(sensorUnitConverter.convertFromCanonical(
                "SOIL_MOISTURE",
                "ppm",
                OBSERVED_CANONICAL_VALUE
        )).thenReturn(Optional.empty());

        when(sensorUnitConverter
                .findCanonicalUnit("SOIL_MOISTURE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                generator.generateNextValue(
                        CULTIVATION_ID,
                        channelKey,
                        ACTUATOR_EFFECT,
                        CYCLE_ID
                ))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("requestedUnit");
    }

    @Test
    void returnEmptyWithoutAdvancingWhenPlanDoesNotExist() {
        SensorChannelKey channelKey =
                new SensorChannelKey(
                        "device-A",
                        "SOIL_MOISTURE",
                        "%"
                );

        when(sensorGenerationPlanResolver.resolve(
                CULTIVATION_ID,
                channelKey
        )).thenReturn(Optional.empty());

        assertThat(generator.generateNextValue(
                CULTIVATION_ID,
                channelKey,
                ACTUATOR_EFFECT,
                CYCLE_ID
        )).isEmpty();

        verifyNoInteractions(
                environmentRandomWalkGenerator,
                sensorObservationProjector,
                sensorUnitConverter
        );
    }

    @Test
    void rejectPlanWithDifferentCultivationId() {
        SensorChannelKey channelKey =
                new SensorChannelKey(
                        "device-A",
                        "TEMPERATURE",
                        "°C"
                );

        SensorGenerationPlan mismatchedPlan =
                createPlan(
                        2L,
                        "TEMPERATURE",
                        "°C"
                );

        when(sensorGenerationPlanResolver.resolve(
                CULTIVATION_ID,
                channelKey
        )).thenReturn(Optional.of(mismatchedPlan));

        assertThatThrownBy(() ->
                generator.generateNextValue(
                        CULTIVATION_ID,
                        channelKey,
                        ACTUATOR_EFFECT,
                        CYCLE_ID
                ))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("cultivationId");

        verifyNoInteractions(
                environmentRandomWalkGenerator,
                sensorObservationProjector,
                sensorUnitConverter
        );
    }

    @Test
    void rejectPlanWithDifferentSensorType() {
        SensorChannelKey channelKey =
                new SensorChannelKey(
                        "device-A",
                        "TEMPERATURE",
                        "°C"
                );

        SensorGenerationPlan mismatchedPlan =
                createPlan(
                        CULTIVATION_ID,
                        "HUMIDITY",
                        "%"
                );

        when(sensorGenerationPlanResolver.resolve(
                CULTIVATION_ID,
                channelKey
        )).thenReturn(Optional.of(mismatchedPlan));

        assertThatThrownBy(() ->
                generator.generateNextValue(
                        CULTIVATION_ID,
                        channelKey,
                        ACTUATOR_EFFECT,
                        CYCLE_ID
                ))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("sensorType");

        verifyNoInteractions(
                environmentRandomWalkGenerator,
                sensorObservationProjector,
                sensorUnitConverter
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void rejectNonPositiveCultivationId(long cultivationId) {
        assertThatThrownBy(() ->
                generator.generateNextValue(
                        cultivationId,
                        validChannelKey(),
                        ACTUATOR_EFFECT,
                        CYCLE_ID
                ))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("cultivationId");

        verifyNoInteractions(sensorGenerationPlanResolver);
    }

    @Test
    void rejectNullSensorChannelKey() {
        assertThatThrownBy(() ->
                generator.generateNextValue(
                        CULTIVATION_ID,
                        null,
                        ACTUATOR_EFFECT,
                        CYCLE_ID
                ))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("sensorChannelKey");

        verifyNoInteractions(sensorGenerationPlanResolver);
    }

    @ParameterizedTest
    @MethodSource("nonFiniteActuatorEffects")
    void rejectNonFiniteActuatorEffect(double actuatorEffect) {
        assertThatThrownBy(() ->
                generator.generateNextValue(
                        CULTIVATION_ID,
                        validChannelKey(),
                        actuatorEffect,
                        CYCLE_ID
                ))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("actuatorEffectAmount");

        verifyNoInteractions(sensorGenerationPlanResolver);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void rejectNonPositiveCycleId(long cycleId) {
        assertThatThrownBy(() ->
                generator.generateNextValue(
                        CULTIVATION_ID,
                        validChannelKey(),
                        ACTUATOR_EFFECT,
                        cycleId
                ))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("cycleId");

        verifyNoInteractions(sensorGenerationPlanResolver);
    }

    @Test
    void rejectNullConstructorDependencies() {
        assertThatThrownBy(() ->
                new SharedEnvironmentSensorValueGenerator(
                        null,
                        environmentRandomWalkGenerator,
                        sensorObservationProjector,
                        sensorUnitConverter
                ))
                .hasMessageContaining(
                        "sensorGenerationPlanResolver"
                );

        assertThatThrownBy(() ->
                new SharedEnvironmentSensorValueGenerator(
                        sensorGenerationPlanResolver,
                        null,
                        sensorObservationProjector,
                        sensorUnitConverter
                ))
                .hasMessageContaining(
                        "environmentRandomWalkGenerator"
                );

        assertThatThrownBy(() ->
                new SharedEnvironmentSensorValueGenerator(
                        sensorGenerationPlanResolver,
                        environmentRandomWalkGenerator,
                        null,
                        sensorUnitConverter
                ))
                .hasMessageContaining(
                        "sensorObservationProjector"
                );

        assertThatThrownBy(() ->
                new SharedEnvironmentSensorValueGenerator(
                        sensorGenerationPlanResolver,
                        environmentRandomWalkGenerator,
                        sensorObservationProjector,
                        null
                ))
                .hasMessageContaining(
                        "sensorUnitConverter"
                );
    }

    private void stubGenerationPipeline(
            SensorChannelKey channelKey,
            SensorGenerationPlan plan,
            double observedCanonicalValue
    ) {
        EnvironmentState environmentState =
                new EnvironmentState(
                        20.0,
                        CYCLE_ID
                );

        SensorObservationKey observationKey =
                new SensorObservationKey(
                        plan.environmentStateKey(),
                        channelKey.deviceEui()
                );

        SensorObservationState observationState =
                new SensorObservationState(
                        0.1,
                        0.2,
                        observedCanonicalValue,
                        CYCLE_ID
                );

        when(sensorGenerationPlanResolver.resolve(
                CULTIVATION_ID,
                channelKey
        )).thenReturn(Optional.of(plan));

        when(environmentRandomWalkGenerator.advance(
                plan.environmentStateKey(),
                plan.measurementConfiguration(),
                ACTUATOR_EFFECT,
                CYCLE_ID
        )).thenReturn(environmentState);

        when(sensorObservationProjector.project(
                observationKey,
                environmentState,
                plan.measurementConfiguration()
        )).thenReturn(observationState);
    }

    private static SensorGenerationPlan createPlan(
            long cultivationId,
            String sensorType,
            String canonicalUnit
    ) {
        return new SensorGenerationPlan(
                new EnvironmentStateKey(
                        cultivationId,
                        sensorType,
                        canonicalUnit
                ),
                CONFIGURATION
        );
    }

    private static SensorChannelKey validChannelKey() {
        return new SensorChannelKey(
                "device-A",
                "TEMPERATURE",
                "°C"
        );
    }

    private static Stream<Double> nonFiniteActuatorEffects() {
        return Stream.of(
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        );
    }
}

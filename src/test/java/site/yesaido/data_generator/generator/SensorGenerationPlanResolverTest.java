package site.yesaido.data_generator.generator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.data_generator.cache.SensorThresholdCache;
import site.yesaido.data_generator.converter.SensorUnitConverter;
import site.yesaido.data_generator.domain.*;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensorGenerationPlanResolverTest {

    @Mock
    private FixedSensorConfigurationRegistry fixedSensorConfigurationRegistry;

    @Mock
    private SensorThresholdCache sensorThresholdCache;

    @Mock
    private DynamicSensorConfigurationFactory dynamicSensorConfigurationFactory;

    @Mock
    private SensorUnitConverter sensorUnitConverter;

    private final DynamicSensorGenerationPolicy dynamicSensorGenerationPolicy =
            new DynamicSensorGenerationPolicy(
                    new BigDecimal("0.1"),
                    new BigDecimal("0.05"),
                    1
            );

    private SensorGenerationPlanResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SensorGenerationPlanResolver(
                fixedSensorConfigurationRegistry,
                sensorThresholdCache,
                dynamicSensorConfigurationFactory,
                dynamicSensorGenerationPolicy,
                sensorUnitConverter
        );
    }

    @Test
    void resolveFixedSensorToCanonicalSharedEnvironment() {
        SensorChannelKey channelKey =
                new SensorChannelKey(
                        "device-A",
                        "TEMPERATURE",
                        "°F"
                );

        MeasurementConfiguration configuration =
                new MeasurementConfiguration(
                        16.0,
                        10.0,
                        30.0,
                        0.3,
                        1
                );

        when(fixedSensorConfigurationRegistry
                .findBySensorType("TEMPERATURE"))
                .thenReturn(Optional.of(configuration));

        when(sensorUnitConverter
                .findCanonicalUnit("TEMPERATURE"))
                .thenReturn(Optional.of("°C"));

        when(sensorUnitConverter.convertFromCanonical(
                "TEMPERATURE",
                "°F",
                16.0
        )).thenReturn(Optional.of(60.8));

        Optional<SensorGenerationPlan> result =
                resolver.resolve(1L, channelKey);

        assertThat(result).contains(
                new SensorGenerationPlan(
                        new EnvironmentStateKey(
                                1L,
                                "TEMPERATURE",
                                "°C"
                        ),
                        configuration
                )
        );

        verifyNoInteractions(sensorThresholdCache, dynamicSensorConfigurationFactory);
    }

    @Test
    void returnEmptyWhenFixedSensorOutputUnitIsUnsupported() {
        SensorChannelKey channelKey =
                new SensorChannelKey(
                        "device-A",
                        "TEMPERATURE",
                        "K"
                );

        MeasurementConfiguration configuration =
                new MeasurementConfiguration(
                        16.0,
                        10.0,
                        30.0,
                        0.3,
                        1
                );

        when(fixedSensorConfigurationRegistry
                .findBySensorType("TEMPERATURE"))
                .thenReturn(Optional.of(configuration));

        when(sensorUnitConverter
                .findCanonicalUnit("TEMPERATURE"))
                .thenReturn(Optional.of("°C"));

        when(sensorUnitConverter.convertFromCanonical("TEMPERATURE", "K", 16.0)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(1L, channelKey)).isEmpty();

        verifyNoInteractions(sensorThresholdCache, dynamicSensorConfigurationFactory);
    }

    @Test
    void rejectFixedSensorWithoutCanonicalUnit() {
        SensorChannelKey channelKey =
                new SensorChannelKey(
                        "device-A",
                        "TEMPERATURE",
                        "°C"
                );

        MeasurementConfiguration configuration =
                new MeasurementConfiguration(
                        16.0,
                        10.0,
                        30.0,
                        0.3,
                        1
                );

        when(fixedSensorConfigurationRegistry
                .findBySensorType("TEMPERATURE"))
                .thenReturn(Optional.of(configuration));

        when(sensorUnitConverter
                .findCanonicalUnit("TEMPERATURE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(1L, channelKey))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("표준 단위");

        verifyNoInteractions(sensorThresholdCache, dynamicSensorConfigurationFactory);
    }

    @Test
    void resolveDynamicSensorFromCultivationThreshold() {
        SensorChannelKey channelKey =
                new SensorChannelKey(
                        "device-A",
                        "SOIL_MOISTURE",
                        "%"
                );

        SensorThresholdKey thresholdKey =
                new SensorThresholdKey(
                        1L,
                        "SOIL_MOISTURE",
                        "%"
                );

        SensorThresholdRange thresholdRange =
                new SensorThresholdRange(
                        new BigDecimal("30"),
                        new BigDecimal("70")
                );

        MeasurementConfiguration configuration =
                new MeasurementConfiguration(
                        50.0,
                        20.0,
                        80.0,
                        2.0,
                        1
                );

        when(fixedSensorConfigurationRegistry
                .findBySensorType("SOIL_MOISTURE"))
                .thenReturn(Optional.empty());

        when(sensorThresholdCache.find(thresholdKey))
                .thenReturn(Optional.of(thresholdRange));

        when(dynamicSensorConfigurationFactory.create(
                thresholdRange,
                dynamicSensorGenerationPolicy
        )).thenReturn(configuration);

        Optional<SensorGenerationPlan> result =
                resolver.resolve(1L, channelKey);

        assertThat(result).contains(
                new SensorGenerationPlan(
                        new EnvironmentStateKey(
                                1L,
                                "SOIL_MOISTURE",
                                "%"
                        ),
                        configuration
                )
        );

        verifyNoInteractions(sensorUnitConverter);
    }

    @Test
    void returnEmptyWhenDynamicSensorThresholdDoesNotExist() {
        SensorChannelKey channelKey =
                new SensorChannelKey(
                        "device-A",
                        "SOIL_MOISTURE",
                        "%"
                );

        SensorThresholdKey thresholdKey =
                new SensorThresholdKey(
                        1L,
                        "SOIL_MOISTURE",
                        "%"
                );

        when(fixedSensorConfigurationRegistry.findBySensorType("SOIL_MOISTURE"))
                .thenReturn(Optional.empty());

        when(sensorThresholdCache.find(thresholdKey))
                .thenReturn(Optional.empty());

        assertThat(resolver.resolve(1L, channelKey)).isEmpty();

        verifyNoInteractions(dynamicSensorConfigurationFactory, sensorUnitConverter);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void rejectNonPositiveCultivationId(long cultivationId) {
        SensorChannelKey channelKey =
                new SensorChannelKey(
                        "device-A",
                        "TEMPERATURE",
                        "°C"
                );

        assertThatThrownBy(() ->
                resolver.resolve(cultivationId, channelKey))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("cultivationId");

        verifyNoInteractions(
                fixedSensorConfigurationRegistry,
                sensorThresholdCache,
                dynamicSensorConfigurationFactory,
                sensorUnitConverter
        );
    }

    @Test
    void rejectNullSensorChannelKey() {
        assertThatThrownBy(() -> resolver.resolve(1L, null))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("sensorChannelKey");

        verifyNoInteractions(
                fixedSensorConfigurationRegistry,
                sensorThresholdCache,
                dynamicSensorConfigurationFactory,
                sensorUnitConverter
        );
    }

    @Test
    void rejectNullConstructorDependencies() {
        assertThatThrownBy(() -> new SensorGenerationPlanResolver(
                null,
                sensorThresholdCache,
                dynamicSensorConfigurationFactory,
                dynamicSensorGenerationPolicy,
                sensorUnitConverter
        )).hasMessageContaining("fixedSensorConfigurationRegistry");

        assertThatThrownBy(() -> new SensorGenerationPlanResolver(
                fixedSensorConfigurationRegistry,
                null,
                dynamicSensorConfigurationFactory,
                dynamicSensorGenerationPolicy,
                sensorUnitConverter
        )).hasMessageContaining("sensorThresholdCache");

        assertThatThrownBy(() -> new SensorGenerationPlanResolver(
                fixedSensorConfigurationRegistry,
                sensorThresholdCache,
                null,
                dynamicSensorGenerationPolicy,
                sensorUnitConverter
        )).hasMessageContaining("dynamicSensorConfigurationFactory");

        assertThatThrownBy(() -> new SensorGenerationPlanResolver(
                fixedSensorConfigurationRegistry,
                sensorThresholdCache,
                dynamicSensorConfigurationFactory,
                null,
                sensorUnitConverter
        )).hasMessageContaining("dynamicSensorGenerationPolicy");

        assertThatThrownBy(() -> new SensorGenerationPlanResolver(
                fixedSensorConfigurationRegistry,
                sensorThresholdCache,
                dynamicSensorConfigurationFactory,
                dynamicSensorGenerationPolicy,
                null
        )).hasMessageContaining("sensorUnitConverter");
    }
}
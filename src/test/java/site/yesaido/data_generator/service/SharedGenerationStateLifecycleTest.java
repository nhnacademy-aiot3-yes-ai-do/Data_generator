package site.yesaido.data_generator.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.data_generator.cache.SensorCache;
import site.yesaido.data_generator.converter.SensorUnitConverter;
import site.yesaido.data_generator.converter.StandardSensorUnitConverter;
import site.yesaido.data_generator.domain.EnvironmentStateKey;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.domain.SensorObservationKey;
import site.yesaido.data_generator.domain.SensorTypeSpec;
import site.yesaido.data_generator.exception.SensorDataGenerationException;
import site.yesaido.data_generator.generator.EnvironmentRandomWalkGenerator;
import site.yesaido.data_generator.generator.FixedSensorConfigurationRegistry;
import site.yesaido.data_generator.generator.SensorObservationProjector;

@ExtendWith(MockitoExtension.class)
class SharedGenerationStateLifecycleTest {

    private static final long CULTIVATION_ID = 1L;
    private static final String DEVICE_A = "device-A";
    private static final String DEVICE_B = "device-B";

    @Mock
    private EnvironmentRandomWalkGenerator environmentRandomWalkGenerator;

    @Mock
    private SensorObservationProjector sensorObservationProjector;

    private SensorCache sensorCache;
    private FixedSensorConfigurationRegistry fixedSensorConfigurationRegistry;
    private SensorUnitConverter sensorUnitConverter;
    private SharedGenerationStateLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        sensorCache = new SensorCache();
        fixedSensorConfigurationRegistry =
                new FixedSensorConfigurationRegistry();
        sensorUnitConverter =
                new StandardSensorUnitConverter();

        lifecycle = new SharedGenerationStateLifecycle(
                sensorCache,
                fixedSensorConfigurationRegistry,
                sensorUnitConverter,
                environmentRandomWalkGenerator,
                sensorObservationProjector
        );
    }

    @Test
    void keepStatesWhenSameDeviceStillHasUnitAlias() {
        SensorChannelKey deletedFahrenheitChannel =
                new SensorChannelKey(
                        DEVICE_A,
                        "TEMPERATURE",
                        "°F"
                );

        sensorCache.upsert(
                createSensorEntry(
                        CULTIVATION_ID,
                        DEVICE_A,
                        new SensorTypeSpec("TEMPERATURE", "°C"),
                        new SensorTypeSpec("TEMPERATURE", "°F")
                )
        );

        sensorCache.removeChannel(deletedFahrenheitChannel);

        lifecycle.removeDeletedChannelState(
                CULTIVATION_ID,
                deletedFahrenheitChannel
        );

        verifyNoInteractions(
                sensorObservationProjector,
                environmentRandomWalkGenerator
        );
    }

    @Test
    void removeOnlyDeletedDeviceObservationWhenAnotherDeviceUsesEnvironment() {
        SensorChannelKey deletedChannel =
                new SensorChannelKey(
                        DEVICE_A,
                        "TEMPERATURE",
                        "°C"
                );

        sensorCache.upsert(
                createSensorEntry(
                        CULTIVATION_ID,
                        DEVICE_A,
                        new SensorTypeSpec("TEMPERATURE", "°C")
                )
        );

        sensorCache.upsert(
                createSensorEntry(
                        CULTIVATION_ID,
                        DEVICE_B,
                        new SensorTypeSpec("TEMPERATURE", "°F")
                )
        );

        sensorCache.removeChannel(deletedChannel);

        lifecycle.removeDeletedChannelState(
                CULTIVATION_ID,
                deletedChannel
        );

        EnvironmentStateKey environmentStateKey =
                new EnvironmentStateKey(
                        CULTIVATION_ID,
                        "TEMPERATURE",
                        "°C"
                );

        verify(sensorObservationProjector).removeState(
                new SensorObservationKey(
                        environmentStateKey,
                        DEVICE_A
                )
        );

        verifyNoInteractions(environmentRandomWalkGenerator);
    }

    @Test
    void removeObservationAndEnvironmentWhenLastFixedChannelIsDeleted() {
        SensorChannelKey deletedChannel =
                new SensorChannelKey(
                        DEVICE_A,
                        "TEMPERATURE",
                        "°F"
                );

        sensorCache.upsert(
                createSensorEntry(
                        CULTIVATION_ID,
                        DEVICE_A,
                        new SensorTypeSpec("TEMPERATURE", "°F")
                )
        );

        sensorCache.removeChannel(deletedChannel);

        lifecycle.removeDeletedChannelState(
                CULTIVATION_ID,
                deletedChannel
        );

        EnvironmentStateKey environmentStateKey =
                new EnvironmentStateKey(
                        CULTIVATION_ID,
                        "TEMPERATURE",
                        "°C"
                );

        SensorObservationKey observationKey =
                new SensorObservationKey(
                        environmentStateKey,
                        DEVICE_A
                );

        InOrder removalOrder = inOrder(
                sensorObservationProjector,
                environmentRandomWalkGenerator
        );

        removalOrder.verify(sensorObservationProjector)
                .removeState(observationKey);

        removalOrder.verify(environmentRandomWalkGenerator)
                .removeState(environmentStateKey);
    }

    @Test
    void removeDynamicStateUsingRegisteredUnitAsCanonicalUnit() {
        SensorChannelKey deletedChannel =
                new SensorChannelKey(
                        DEVICE_A,
                        "SOIL_MOISTURE",
                        "%"
                );

        sensorCache.upsert(
                createSensorEntry(
                        CULTIVATION_ID,
                        DEVICE_A,
                        new SensorTypeSpec(
                                "SOIL_MOISTURE",
                                "%"
                        )
                )
        );

        sensorCache.removeChannel(deletedChannel);

        lifecycle.removeDeletedChannelState(
                CULTIVATION_ID,
                deletedChannel
        );

        EnvironmentStateKey environmentStateKey =
                new EnvironmentStateKey(
                        CULTIVATION_ID,
                        "SOIL_MOISTURE",
                        "%"
                );

        verify(sensorObservationProjector).removeState(
                new SensorObservationKey(
                        environmentStateKey,
                        DEVICE_A
                )
        );

        verify(environmentRandomWalkGenerator).removeState(
                environmentStateKey
        );
    }

    @Test
    void treatDifferentDynamicUnitsAsDifferentEnvironments() {
        SensorChannelKey deletedPercentChannel =
                new SensorChannelKey(
                        DEVICE_A,
                        "SOIL_MOISTURE",
                        "%"
                );

        sensorCache.upsert(
                createSensorEntry(
                        CULTIVATION_ID,
                        DEVICE_A,
                        new SensorTypeSpec(
                                "SOIL_MOISTURE",
                                "%"
                        ),
                        new SensorTypeSpec(
                                "SOIL_MOISTURE",
                                "ppm"
                        )
                )
        );

        sensorCache.removeChannel(deletedPercentChannel);

        lifecycle.removeDeletedChannelState(
                CULTIVATION_ID,
                deletedPercentChannel
        );

        EnvironmentStateKey percentEnvironmentKey =
                new EnvironmentStateKey(
                        CULTIVATION_ID,
                        "SOIL_MOISTURE",
                        "%"
                );

        verify(sensorObservationProjector).removeState(
                new SensorObservationKey(
                        percentEnvironmentKey,
                        DEVICE_A
                )
        );

        verify(environmentRandomWalkGenerator).removeState(
                percentEnvironmentKey
        );
    }

    @Test
    void ignoreUnsupportedFixedSensorUnit() {
        SensorChannelKey unsupportedChannel =
                new SensorChannelKey(
                        DEVICE_A,
                        "TEMPERATURE",
                        "K"
                );

        sensorCache.upsert(
                createSensorEntry(
                        CULTIVATION_ID,
                        DEVICE_A,
                        new SensorTypeSpec("TEMPERATURE", "K")
                )
        );

        sensorCache.removeChannel(unsupportedChannel);

        lifecycle.removeDeletedChannelState(
                CULTIVATION_ID,
                unsupportedChannel
        );

        verifyNoInteractions(
                sensorObservationProjector,
                environmentRandomWalkGenerator
        );
    }

    @Test
    void removeAllSharedStatesForCultivation() {
        lifecycle.removeCultivationStates(7L);

        InOrder removalOrder = inOrder(
                sensorObservationProjector,
                environmentRandomWalkGenerator
        );

        removalOrder.verify(sensorObservationProjector)
                .removeStatesByCultivationId(7L);

        removalOrder.verify(environmentRandomWalkGenerator)
                .removeStatesByCultivationId(7L);
    }

    @Test
    void rejectInvalidRemovalRequestBeforeChangingState() {
        SensorChannelKey channelKey =
                new SensorChannelKey(
                        DEVICE_A,
                        "TEMPERATURE",
                        "°C"
                );

        assertThatThrownBy(() ->
                lifecycle.removeDeletedChannelState(
                        0L,
                        channelKey
                ))
                .isInstanceOf(
                        SensorDataGenerationException.class
                )
                .hasMessageContaining("cultivationId");

        assertThatThrownBy(() ->
                lifecycle.removeDeletedChannelState(
                        CULTIVATION_ID,
                        null
                ))
                .isInstanceOf(
                        SensorDataGenerationException.class
                )
                .hasMessageContaining("deletedChannelKey");

        assertThatThrownBy(() ->
                lifecycle.removeCultivationStates(0L))
                .isInstanceOf(
                        SensorDataGenerationException.class
                )
                .hasMessageContaining("cultivationId");

        verifyNoInteractions(
                sensorObservationProjector,
                environmentRandomWalkGenerator
        );
    }

    @Test
    void rejectNullConstructorDependencies() {
        assertThatThrownBy(() ->
                new SharedGenerationStateLifecycle(
                        null,
                        fixedSensorConfigurationRegistry,
                        sensorUnitConverter,
                        environmentRandomWalkGenerator,
                        sensorObservationProjector
                ))
                .isInstanceOf(
                        SensorDataGenerationException.class
                )
                .hasMessageContaining("sensorCache");

        assertThatThrownBy(() ->
                new SharedGenerationStateLifecycle(
                        sensorCache,
                        null,
                        sensorUnitConverter,
                        environmentRandomWalkGenerator,
                        sensorObservationProjector
                ))
                .isInstanceOf(
                        SensorDataGenerationException.class
                )
                .hasMessageContaining(
                        "fixedSensorConfigurationRegistry"
                );

        assertThatThrownBy(() ->
                new SharedGenerationStateLifecycle(
                        sensorCache,
                        fixedSensorConfigurationRegistry,
                        null,
                        environmentRandomWalkGenerator,
                        sensorObservationProjector
                ))
                .isInstanceOf(
                        SensorDataGenerationException.class
                )
                .hasMessageContaining("sensorUnitConverter");

        assertThatThrownBy(() ->
                new SharedGenerationStateLifecycle(
                        sensorCache,
                        fixedSensorConfigurationRegistry,
                        sensorUnitConverter,
                        null,
                        sensorObservationProjector
                ))
                .isInstanceOf(
                        SensorDataGenerationException.class
                )
                .hasMessageContaining(
                        "environmentRandomWalkGenerator"
                );

        assertThatThrownBy(() ->
                new SharedGenerationStateLifecycle(
                        sensorCache,
                        fixedSensorConfigurationRegistry,
                        sensorUnitConverter,
                        environmentRandomWalkGenerator,
                        null
                ))
                .isInstanceOf(
                        SensorDataGenerationException.class
                )
                .hasMessageContaining(
                        "sensorObservationProjector"
                );
    }

    private static SensorCacheEntry createSensorEntry(
            long cultivationId,
            String deviceEui,
            SensorTypeSpec... sensorTypeSpecs
    ) {
        return new SensorCacheEntry(
                cultivationId,
                deviceEui,
                "test-device",
                "test-location",
                "test-location-detail",
                "test-model",
                Set.of(sensorTypeSpecs)
        );
    }
}

package site.yesaido.data_generator.generator;

import org.springframework.stereotype.Component;
import site.yesaido.data_generator.cache.SensorThresholdCache;
import site.yesaido.data_generator.converter.SensorUnitConverter;
import site.yesaido.data_generator.domain.*;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.util.Optional;

@Component
public final class SensorGenerationPlanResolver {

    private final FixedSensorConfigurationRegistry fixedSensorConfigurationRegistry;
    private final SensorThresholdCache sensorThresholdCache;
    private final DynamicSensorConfigurationFactory dynamicSensorConfigurationFactory;
    private final DynamicSensorGenerationPolicy dynamicSensorGenerationPolicy;
    private final SensorUnitConverter sensorUnitConverter;

    public SensorGenerationPlanResolver(
            FixedSensorConfigurationRegistry fixedSensorConfigurationRegistry,
            SensorThresholdCache sensorThresholdCache,
            DynamicSensorConfigurationFactory dynamicSensorConfigurationFactory,
            DynamicSensorGenerationPolicy dynamicSensorGenerationPolicy,
            SensorUnitConverter sensorUnitConverter
    ) {
        if (fixedSensorConfigurationRegistry == null) {
            throw new SensorDataGenerationException("fixedSensorConfigurationRegistry는 null일 수 없습니다.");
        }
        if (sensorThresholdCache == null) {
            throw new SensorDataGenerationException("sensorThresholdCache는 null일 수 없습니다.");
        }
        if (dynamicSensorConfigurationFactory == null) {
            throw new SensorDataGenerationException("dynamicSensorConfigurationFactory는 null일 수 없습니다.");
        }
        if (dynamicSensorGenerationPolicy == null) {
            throw new SensorDataGenerationException("dynamicSensorGenerationPolicy는 null일 수 없습니다.");
        }
        if (sensorUnitConverter == null) {
            throw new SensorDataGenerationException("sensorUnitConverter는 null일 수 없습니다.");
        }

        this.fixedSensorConfigurationRegistry = fixedSensorConfigurationRegistry;
        this.sensorThresholdCache = sensorThresholdCache;
        this.dynamicSensorConfigurationFactory = dynamicSensorConfigurationFactory;
        this.dynamicSensorGenerationPolicy = dynamicSensorGenerationPolicy;
        this.sensorUnitConverter = sensorUnitConverter;
    }

    public Optional<SensorGenerationPlan> resolve(long cultivationId, SensorChannelKey sensorChannelKey) {
        validateCultivationId(cultivationId);
        validateSensorChannelKey(sensorChannelKey);

        Optional<MeasurementConfiguration> fixedConfiguration = fixedSensorConfigurationRegistry.findBySensorType(sensorChannelKey.sensorType());

        if (fixedConfiguration.isPresent()) {
            return resolveFixedSensorPlan(cultivationId, sensorChannelKey, fixedConfiguration.get());
        }

        return resolveDynamicSensorPlan(cultivationId, sensorChannelKey);
    }

    private Optional<SensorGenerationPlan> resolveFixedSensorPlan(
            long cultivationId,
            SensorChannelKey sensorChannelKey,
            MeasurementConfiguration measurementConfiguration
    ) {
        String canonicalUnit = sensorUnitConverter.findCanonicalUnit(sensorChannelKey.sensorType())
                .orElseThrow(() -> new SensorDataGenerationException(
                        "고정 센서 타입의 표준 단위를 찾을 수 없습니다. sensorType=" + sensorChannelKey.sensorType()
                ));

        boolean outputUnitSupported = sensorUnitConverter.convertFromCanonical(
                sensorChannelKey.sensorType(),
                sensorChannelKey.unit(),
                measurementConfiguration.initialValue()
        ).isPresent();

        if (!outputUnitSupported) {
            return Optional.empty();
        }

        EnvironmentStateKey environmentStateKey = new EnvironmentStateKey(
                        cultivationId, sensorChannelKey.sensorType(), canonicalUnit);

        return Optional.of(new SensorGenerationPlan(environmentStateKey, measurementConfiguration));
    }

    private Optional<SensorGenerationPlan> resolveDynamicSensorPlan(long cultivationId, SensorChannelKey sensorChannelKey) {
        SensorThresholdKey thresholdKey = new SensorThresholdKey(cultivationId, sensorChannelKey.sensorType(), sensorChannelKey.unit());

        return sensorThresholdCache.find(thresholdKey).map(sensorThresholdRange -> {
                    MeasurementConfiguration measurementConfiguration =
                            dynamicSensorConfigurationFactory.create(sensorThresholdRange, dynamicSensorGenerationPolicy);

                    EnvironmentStateKey environmentStateKey = new EnvironmentStateKey(
                            cultivationId, sensorChannelKey.sensorType(), sensorChannelKey.unit());

                    return new SensorGenerationPlan(environmentStateKey, measurementConfiguration);
                });
    }

    private static void validateCultivationId(long cultivationId) {
        if (cultivationId <= 0) {
            throw new SensorDataGenerationException("cultivationId는 0보다 커야 합니다.");
        }
    }

    private static void validateSensorChannelKey(SensorChannelKey sensorChannelKey) {
        if (sensorChannelKey == null) {
            throw new SensorDataGenerationException("sensorChannelKey는 null일 수 없습니다.");
        }
    }
}
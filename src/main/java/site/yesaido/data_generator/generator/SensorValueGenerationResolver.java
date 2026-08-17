package site.yesaido.data_generator.generator;

import org.springframework.stereotype.Component;
import site.yesaido.data_generator.cache.SensorThresholdCache;
import site.yesaido.data_generator.converter.SensorUnitConverter;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.domain.SensorThresholdKey;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.util.Optional;

// 고정 sensorType 생성기를 우선 사용하고, 미등록 타입만 임계값 기반 생성기로 연결하는 선택기
@Component
public final class SensorValueGenerationResolver {
    private final SensorValueGeneratorRegistry sensorValueGeneratorRegistry;
    private final SensorThresholdCache sensorThresholdCache;
    private final ThresholdBasedSensorValueGenerator thresholdBasedSensorValueGenerator;
    private final SensorUnitConverter sensorUnitConverter;

    public SensorValueGenerationResolver(
            SensorValueGeneratorRegistry sensorValueGeneratorRegistry,
            SensorThresholdCache sensorThresholdCache,
            ThresholdBasedSensorValueGenerator thresholdBasedSensorValueGenerator,
            SensorUnitConverter sensorUnitConverter) {
        if (sensorValueGeneratorRegistry == null) {
            throw new SensorDataGenerationException(
                    "sensorValueGeneratorRegistry는 null일 수 없습니다."
            );
        }

        if (sensorThresholdCache == null) {
            throw new SensorDataGenerationException(
                    "sensorThresholdCache는 null일 수 없습니다."
            );
        }

        if (thresholdBasedSensorValueGenerator == null) {
            throw new SensorDataGenerationException(
                    "thresholdBasedSensorValueGenerator는 null일 수 없습니다."
            );
        }

        if (sensorUnitConverter == null) {
            throw new SensorDataGenerationException(
                    "sensorUnitConverter는 null일 수 없습니다."
            );
        }

        this.sensorValueGeneratorRegistry = sensorValueGeneratorRegistry;
        this.sensorThresholdCache = sensorThresholdCache;
        this.thresholdBasedSensorValueGenerator = thresholdBasedSensorValueGenerator;
        this.sensorUnitConverter = sensorUnitConverter;
    }

    public Optional<Number> generateNextValue(long cultivationId, SensorChannelKey sensorChannelKey, double actuatorEffectAmount) {
        validateCultivationId(cultivationId);
        validateSensorChannelKey(sensorChannelKey);
        validateActuatorEffectAmount(actuatorEffectAmount);

        Optional<SensorValueGenerator> optionalSensorValueGenerator = sensorValueGeneratorRegistry.findBySensorType(sensorChannelKey.sensorType());

        if( optionalSensorValueGenerator.isPresent()) {
            SensorValueGenerator sensorValueGenerator = optionalSensorValueGenerator.get();

            Number canonicalValue = sensorValueGenerator.generateNextValue(sensorChannelKey, actuatorEffectAmount);

            return sensorUnitConverter.convertFromCanonical(sensorChannelKey.sensorType(), sensorChannelKey.unit(), canonicalValue);
        }

        SensorThresholdKey thresholdKey = new SensorThresholdKey(cultivationId, sensorChannelKey.sensorType(), sensorChannelKey.unit());

        return sensorThresholdCache.find(thresholdKey)
                .map(
                        sensorThresholdRange
                                -> thresholdBasedSensorValueGenerator.generateNextValue(sensorChannelKey, sensorThresholdRange)
                );

    }

    public void removeState(SensorChannelKey sensorChannelKey) {
        validateSensorChannelKey(sensorChannelKey);

        Optional<SensorValueGenerator> optionalSensorValueGenerator
                = sensorValueGeneratorRegistry.findBySensorType(sensorChannelKey.sensorType());

        if( optionalSensorValueGenerator.isPresent()) {
            optionalSensorValueGenerator.get().removeState(sensorChannelKey);
            return;
        }
        thresholdBasedSensorValueGenerator.removeState(sensorChannelKey);
    }




    private static void validateCultivationId(long cultivationId) {
        if( cultivationId <= 0) {
            throw new SensorDataGenerationException("cultivationId는 0보다 커야 합니다.");
        }
    }

    private static void validateSensorChannelKey(SensorChannelKey sensorChannelKey) {
        if(sensorChannelKey == null) {
            throw new SensorDataGenerationException("sensorChannelKey는 null일 수 없습니다.");
        }
    }

    private static void validateActuatorEffectAmount(double actuatorEffectAmount) {
        if(!Double.isFinite(actuatorEffectAmount)) {
            throw new SensorDataGenerationException("actuatorEffectAmount는 유한한 숫자여야 합니다.");
        }
    }
}

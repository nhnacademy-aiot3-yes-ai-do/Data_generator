package site.yesaido.data_generator.generator;

import org.springframework.stereotype.Component;
import site.yesaido.data_generator.domain.DynamicSensorGenerationPolicy;
import site.yesaido.data_generator.domain.MeasurementConfiguration;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.domain.SensorThresholdRange;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

// 등록된 고정 생성기가 없는 숫자형 센서를 임계값 기반 Random Walk로 생성하는 클래스
@Component
public final class ThresholdBasedSensorValueGenerator {
    private final RandomWalkGenerator randomWalkGenerator;
    private final DynamicSensorConfigurationFactory dynamicSensorConfigurationFactory;
    private final DynamicSensorGenerationPolicy dynamicSensorGenerationPolicy;

    public ThresholdBasedSensorValueGenerator(
            RandomWalkGenerator randomWalkGenerator,
            DynamicSensorConfigurationFactory dynamicSensorConfigurationFactory,
            DynamicSensorGenerationPolicy dynamicSensorGenerationPolicy
    ) {
        if( randomWalkGenerator == null) {
            throw new SensorDataGenerationException("randomWalkGenerator는 null일 수 없습니다.");
        }
        if(dynamicSensorConfigurationFactory == null){
            throw new SensorDataGenerationException("dynamicSensorConfigurationFactory는 null일 수 없습니다.");
        }

        if(dynamicSensorGenerationPolicy == null){
            throw new SensorDataGenerationException("dynamicSensorGenerationPolicy는 null일 수 없습니다.");
        }

        this.randomWalkGenerator = randomWalkGenerator;
        this.dynamicSensorConfigurationFactory = dynamicSensorConfigurationFactory;
        this.dynamicSensorGenerationPolicy = dynamicSensorGenerationPolicy;
    }

    public Number generateNextValue(
            SensorChannelKey sensorChannelKey,
            SensorThresholdRange sensorThresholdRange
    ) {
        validateSensorChannelKey(sensorChannelKey);
        validateSensorThresholdRange(sensorThresholdRange);

        MeasurementConfiguration measurementConfiguration
                = dynamicSensorConfigurationFactory.create(sensorThresholdRange, dynamicSensorGenerationPolicy);

        return randomWalkGenerator.generateNextValue(
                sensorChannelKey,
                measurementConfiguration,
                0.0
        );
    }
    public void removeState(SensorChannelKey sensorChannelKey) {
        validateSensorChannelKey(sensorChannelKey);

        randomWalkGenerator.removeState(sensorChannelKey);
    }


    private static void validateSensorChannelKey(SensorChannelKey sensorChannelKey) {
        if( sensorChannelKey == null) {
            throw new SensorDataGenerationException("sensorChannelKey는 null일 수 없습니다.");
        }
    }

    private static void validateSensorThresholdRange(SensorThresholdRange sensorThresholdRange) {
        if( sensorThresholdRange == null) {
            throw new SensorDataGenerationException("thresholdRange는 null일 수 없습니다.");
        }
    }
}

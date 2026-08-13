package site.yesaido.data_generator.generator;

import org.springframework.stereotype.Component;
import site.yesaido.data_generator.domain.MeasurementConfiguration;

// LIGHT 타입의 내부 표준 단위 값을 Random Walk 방식으로 생성하는 Spring Bean
@Component
public final class LightSensorValueGenerator extends AbstractRandomWalkSensorValueGenerator {

    private static final String SUPPORTED_SENSOR_TYPE = "LIGHT";

    private static final MeasurementConfiguration LIGHT_CONFIGURATION =
            new MeasurementConfiguration(100.0, 0.0, 1000.0, 20.0, 0);
    public LightSensorValueGenerator(RandomWalkGenerator randomWalkGenerator) {
        super(SUPPORTED_SENSOR_TYPE, LIGHT_CONFIGURATION, randomWalkGenerator);
    }
}

package site.yesaido.data_generator.generator;

import org.springframework.stereotype.Component;
import site.yesaido.data_generator.domain.MeasurementConfiguration;

// HUMIDITY 타입의 내부 표준 단위 값을 Random Walk 방식으로 생성하는 Spring Bean
@Component
public final class HumiditySensorValueGenerator extends AbstractRandomWalkSensorValueGenerator{

    private static final String SUPPORTED_SENSOR_TYPE = "HUMIDITY";

    private static final MeasurementConfiguration HUMIDITY_CONFIGURATION =
            new MeasurementConfiguration(80.0, 40.0, 120.0, 1.0, 1);

    public HumiditySensorValueGenerator(RandomWalkGenerator randomWalkGenerator) {
        super(SUPPORTED_SENSOR_TYPE, HUMIDITY_CONFIGURATION, randomWalkGenerator);
    }
}

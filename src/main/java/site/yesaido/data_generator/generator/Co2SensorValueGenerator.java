package site.yesaido.data_generator.generator;

import org.springframework.stereotype.Component;
import site.yesaido.data_generator.domain.MeasurementConfiguration;


// CO2 타입의 내부 표준 단위 값을 Random Walk 방식으로 생성하는 Spring Bean
@Component
public final class Co2SensorValueGenerator extends AbstractRandomWalkSensorValueGenerator {

    private static final String SUPPORTED_SENSOR_TYPE = "CO2";

    private static final MeasurementConfiguration CO2_CONFIGURATION =
            new MeasurementConfiguration(1500.0,500.0,4000.0,30.0,0);

    public Co2SensorValueGenerator(RandomWalkGenerator randomWalkGenerator) {
        super(SUPPORTED_SENSOR_TYPE, CO2_CONFIGURATION, randomWalkGenerator);
    }
}

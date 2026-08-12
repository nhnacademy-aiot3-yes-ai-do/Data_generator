package site.yesaido.data_generator.generator;

import org.springframework.stereotype.Component;
import site.yesaido.data_generator.domain.MeasurementConfiguration;

// TEMPERATURE 타입의 내부 표준 단위 값을 Random Walk 방식으로 생성하는 Spring Bean
@Component
public final class TemperatureSensorValueGenerator extends AbstractRandomWalkSensorValueGenerator {

    private static final String SUPPORTED_SENSOR_TYPE = "TEMPERATURE";

    private static final MeasurementConfiguration TEMPERATURE_CONFIGURATION =
            new MeasurementConfiguration(16.0, 10.0, 30.0, 0.3, 1);

    public TemperatureSensorValueGenerator(RandomWalkGenerator randomWalkGenerator) {
        super(SUPPORTED_SENSOR_TYPE, TEMPERATURE_CONFIGURATION, randomWalkGenerator);
    }
}

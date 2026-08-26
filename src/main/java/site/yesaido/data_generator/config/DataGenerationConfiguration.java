package site.yesaido.data_generator.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import site.yesaido.data_generator.domain.DynamicSensorGenerationPolicy;
import site.yesaido.data_generator.domain.SensorObservationPolicy;
import site.yesaido.data_generator.generator.EnvironmentRandomWalkGenerator;
import site.yesaido.data_generator.generator.SensorObservationProjector;
import site.yesaido.data_generator.mqtt.MqttPayloadSerializer;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.Random;
import java.util.random.RandomGenerator;

@Configuration(proxyBeanMethods = false)
public class DataGenerationConfiguration {

    @Bean
    public Clock createClock() {
        return Clock.systemUTC();
    }

    @Bean
    public RandomGenerator createRandomGenerator() {
        return new Random();
    }

    @Bean
    public MqttPayloadSerializer createMqttPayloadSerializer(ObjectMapper objectMapper, Clock clock){
        return new MqttPayloadSerializer(objectMapper,clock);
    }

    @Bean
    public EnvironmentRandomWalkGenerator createEnvironmentRandomWalkGenerator(RandomGenerator randomGenerator) {
        return new EnvironmentRandomWalkGenerator(randomGenerator);
    }

    @Bean
    public SensorObservationPolicy createSensorObservationPolicy(SensorObservationProperties properties) {
        return new SensorObservationPolicy(properties.getMaximumBiasRatio(),
                properties.getMaximumDeviationRatio(), properties.getDeviationRetentionRatio());
    }

    @Bean
    public SensorObservationProjector createSensorObservationProjector(
            RandomGenerator randomGenerator, SensorObservationPolicy sensorObservationPolicy) {
        return new SensorObservationProjector(randomGenerator, sensorObservationPolicy);
    }

    // 변경 가능한 외부 설정값을 애플리케이션 시작 시 불변 정책으로 고정
    @Bean
    public DynamicSensorGenerationPolicy createDynamicSensorGenerationPolicy(DynamicSensorGenerationProperties properties) {
        return new DynamicSensorGenerationPolicy(properties.getRangeExpansionRatio(),
                properties.getMaximumChangeRatio(), properties.getDecimalPlaces()
        );
    }

}

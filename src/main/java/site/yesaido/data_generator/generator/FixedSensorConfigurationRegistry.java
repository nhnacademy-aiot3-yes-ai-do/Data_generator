package site.yesaido.data_generator.generator;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import site.yesaido.data_generator.domain.MeasurementConfiguration;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

@Component
public final class FixedSensorConfigurationRegistry {

    private final Map<String, MeasurementConfiguration> configurationsBySensorType;

    public FixedSensorConfigurationRegistry() {
        configurationsBySensorType = Map.of(
                "TEMPERATURE", new MeasurementConfiguration(16.0, 10.0, 30.0, 0.3, 1),
                "HUMIDITY", new MeasurementConfiguration(80.0, 40.0, 120.0, 1.0, 1),
                "CO2", new MeasurementConfiguration(1500.0, 500.0, 4000.0, 30.0, 0),
                "LIGHT", new MeasurementConfiguration(100.0, 0.0, 1000.0, 20.0, 0)
        );
    }

    public Optional<MeasurementConfiguration> findBySensorType(String sensorType) {
        if (sensorType == null || sensorType.isBlank()) {
            throw new SensorDataGenerationException("sensorType은 null이거나 빈 문자열 또는 공백 문자열일 수 없습니다.");
        }

        return Optional.ofNullable(configurationsBySensorType.get(sensorType.strip()));
    }
}
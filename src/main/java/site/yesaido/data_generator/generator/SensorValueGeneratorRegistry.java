package site.yesaido.data_generator.generator;

import org.springframework.stereotype.Component;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// SensorValueGenerator 구현체를 String sensorType으로 조회하는 읽기 전용 Registry
@Component
public final class SensorValueGeneratorRegistry {
    private final Map<String, SensorValueGenerator> generatorsBySensorType;

    public SensorValueGeneratorRegistry(List<SensorValueGenerator> sensorValueGenerators) {
        if(sensorValueGenerators == null) {
            throw new SensorDataGenerationException("sensorValueGenerators는 null일 수 없습니다.");
        }
        if(sensorValueGenerators.isEmpty()) {
            throw new SensorDataGenerationException("sensorValueGenerators는 비어있을 수 없습니다.");
        }

        Map<String, SensorValueGenerator> mutableGeneratorsBySensorType = new HashMap<>();

        for(SensorValueGenerator sensorValueGenerator : sensorValueGenerators){
            if(sensorValueGenerator == null) {
                throw new SensorDataGenerationException("sensorValueGenerators는 null이 포함될 수 없습니다.");
            }

            String supportedSensorType = normalizeRequiredText(sensorValueGenerator.supportedSensorType(),"supportedSensorType");
            SensorValueGenerator existingSensorValueGenerator = mutableGeneratorsBySensorType.putIfAbsent(supportedSensorType, sensorValueGenerator);

            if( existingSensorValueGenerator != null) {
                throw new SensorDataGenerationException("같은 sensorType을 지원하는 생성기가 중복 등록되었습니다. sensorType=" + supportedSensorType);
            }

        }

        generatorsBySensorType = Map.copyOf(mutableGeneratorsBySensorType);
    }
    public Optional<SensorValueGenerator> findBySensorType(String sensorType) {
        String normalizedSensorType = normalizeRequiredText(sensorType,"sensorType");
        return Optional.ofNullable(generatorsBySensorType.get(normalizedSensorType));
    }

    private static String normalizeRequiredText(String value, String fieldName) {
        if(value ==null || value.isBlank()){
            throw new SensorDataGenerationException(fieldName + "은 null이거나 빈 문자열 또는 공백 문자열일 수 없습니다.");
        }

        return value.strip();
    }

}

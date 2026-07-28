package site.yesaido.data_generator.mqtt;

import org.springframework.stereotype.Component;
import site.yesaido.data_generator.domain.MeasurementType;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.exception.InvalidMqttTopicException;

@Component
public class MqttTopicGenerator {

    private static final String ROOT_TOPIC = "mushroom";
    private static final String TOPIC_SEPARATOR = "/";

    public String generateTopic(
            SensorCacheEntry sensorCacheEntry,
            MeasurementType measurementType
    ){
        if(sensorCacheEntry == null){
            throw new InvalidMqttTopicException("sensorCacheEntry는 null일 수 없습니다.");
        }

        if(measurementType == null){
            throw new InvalidMqttTopicException("measurementType은 null일 수 없습니다.");
        }

        if(!sensorCacheEntry.measurementTypes().contains(measurementType)){
            throw new InvalidMqttTopicException("장치가 지원하지 않는 측정 항목입니다. deviceEui=" + sensorCacheEntry.deviceEui() + ", measurementType=" + measurementType);
        }

        String measurementTopicValue = measurementType.getTopicValue();

        validateTopicComponent(sensorCacheEntry.location(),"location");
        validateTopicComponent(sensorCacheEntry.locationDetail(), "locationDetail");
        validateTopicComponent(sensorCacheEntry.deviceModel(), "deviceModel");
        validateTopicComponent(sensorCacheEntry.deviceEui(),"deviceEui");
        validateTopicComponent(measurementTopicValue, "measurementType");

        return String.join(
                TOPIC_SEPARATOR,
                ROOT_TOPIC,
                sensorCacheEntry.location(),
                sensorCacheEntry.locationDetail(),
                sensorCacheEntry.deviceModel(),
                sensorCacheEntry.deviceEui(),
                measurementTopicValue
        );
    }


    private void validateTopicComponent(String componentValue,String componentName){
        if(componentValue == null || componentValue.isBlank()){
            throw new InvalidMqttTopicException(componentName+ "은 null이거나 공백일 수 없습니다.");
        }
        if(componentValue.contains(TOPIC_SEPARATOR)){
            throw new InvalidMqttTopicException(componentName+ "에는 '/'를 포함할 수 없습니다.");
        }
        if(componentValue.contains("+")|| componentValue.contains("#")){
            throw new InvalidMqttTopicException(componentName+"에는 MQTT 와일드 카드 '+', '#'을 포함할 수 없습니다.");
        }
    }
}

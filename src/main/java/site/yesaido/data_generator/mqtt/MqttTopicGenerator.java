package site.yesaido.data_generator.mqtt;

import org.springframework.stereotype.Component;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.domain.SensorTypeSpec;
import site.yesaido.data_generator.exception.InvalidMqttTopicException;

// 센서 장치와 sensorType을 식별하는 MQTT 토픽을 생성
@Component
public class MqttTopicGenerator {

    private static final String ROOT_TOPIC = "mushroom";
    private static final String TOPIC_SEPARATOR = "/";
    private static final char NULL_CHARACTER = '\0';

    public String generateTopic(
            SensorCacheEntry sensorCacheEntry,
            SensorTypeSpec sensorTypeSpec
    ){
        if(sensorCacheEntry == null){
            throw new InvalidMqttTopicException("sensorCacheEntry는 null일 수 없습니다.");
        }

        if(sensorTypeSpec == null){
            throw new InvalidMqttTopicException("sensorTypeSpec은 null일 수 없습니다.");
        }

        if(!sensorCacheEntry.sensorTypes().contains(sensorTypeSpec)){
            throw new InvalidMqttTopicException("장치가 지원하지 않는 측정 항목입니다. deviceEui=" + sensorCacheEntry.deviceEui() + ", sensorTypeSpec=" + sensorTypeSpec);
        }


        validateTopicComponent(sensorCacheEntry.location(),"location");
        validateTopicComponent(sensorCacheEntry.locationDetail(), "locationDetail");
        validateTopicComponent(sensorCacheEntry.deviceModel(), "deviceModel");
        validateTopicComponent(sensorCacheEntry.deviceEui(),"deviceEui");
        validateTopicComponent(sensorTypeSpec.sensorType(), "sensorType");

        return String.join(
                TOPIC_SEPARATOR,
                ROOT_TOPIC,
                sensorCacheEntry.location(),
                sensorCacheEntry.locationDetail(),
                sensorCacheEntry.deviceModel(),
                sensorCacheEntry.deviceEui(),
                sensorTypeSpec.sensorType()
        );
    }


    private static void validateTopicComponent(String componentValue,String componentName){
        if(componentValue == null || componentValue.isBlank()){
            throw new InvalidMqttTopicException(componentName+ "은 null이거나 빈 문자열 또는 공백 문자열일 수 없습니다.");
        }
        if(componentValue.contains(TOPIC_SEPARATOR)){
            throw new InvalidMqttTopicException(componentName+ "에는 '/'를 포함할 수 없습니다.");
        }
        if(componentValue.contains("+")|| componentValue.contains("#")){
            throw new InvalidMqttTopicException(componentName+"에는 MQTT 와일드 카드 '+', '#'을 포함할 수 없습니다.");
        }
        if (componentValue.indexOf(NULL_CHARACTER) >= 0) {
            throw new InvalidMqttTopicException(componentName +"에는 null 문자를 포함할 수 없습니다.");
        }
    }
}

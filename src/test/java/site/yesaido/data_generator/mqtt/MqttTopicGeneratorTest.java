package site.yesaido.data_generator.mqtt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.domain.SensorTypeSpec;
import site.yesaido.data_generator.exception.InvalidMqttTopicException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// MQTT 토픽이 unit을 제외한 6구간 계약을 지키는지 검증합니다.
class MqttTopicGeneratorTest {

    private final MqttTopicGenerator mqttTopicGenerator = new MqttTopicGenerator();

    @Test
    @DisplayName("한글과 공백을 유지한 6구간 MQTT 토픽을 생성한다")
    void generateSixSegmentTopicWithoutUnit() {
        SensorTypeSpec sensorTypeSpec = new SensorTypeSpec("TEMPERATURE", "°C");

        SensorCacheEntry sensorCacheEntry = createSensorCacheEntry(
                        "device-A",
                        "송이버섯집",
                        "중앙 오른쪽",
                        "TEST123",
                        Set.of(sensorTypeSpec)
                );

        String topic = mqttTopicGenerator.generateTopic(sensorCacheEntry, sensorTypeSpec);

        assertThat(topic).isEqualTo("mushroom/송이버섯집/중앙 오른쪽/TEST123/device-A/TEMPERATURE");
        assertThat(topic.split("/")).hasSize(6);
        assertThat(topic).doesNotContain("°C");
    }

    @Test
    @DisplayName("같은 장치와 센서 타입은 unit이 달라도 같은 토픽을 사용한다")
    void generateSameTopicForDifferentUnits() {
        SensorTypeSpec celsiusSpec = new SensorTypeSpec("TEMPERATURE", "°C");
        SensorTypeSpec fahrenheitSpec = new SensorTypeSpec("TEMPERATURE", "°F");
        SensorCacheEntry sensorCacheEntry = createSensorCacheEntry(
                        "device-A",
                        "송이버섯집",
                        "중앙 오른쪽",
                        "TEST123",
                        Set.of(celsiusSpec, fahrenheitSpec));

        String celsiusTopic = mqttTopicGenerator.generateTopic(sensorCacheEntry, celsiusSpec);
        String fahrenheitTopic = mqttTopicGenerator.generateTopic(sensorCacheEntry, fahrenheitSpec);

        assertThat(celsiusTopic).isEqualTo(fahrenheitTopic)
                .isEqualTo("mushroom/송이버섯집/중앙 오른쪽/TEST123/device-A/TEMPERATURE");
    }

    @Test
    @DisplayName("unit의 슬래시는 MQTT 토픽 생성에 영향을 주지 않는다")
    void allowSlashInsidePayloadUnit() {
        SensorTypeSpec sensorTypeSpec = new SensorTypeSpec("PARTICULATE_MATTER", "µg/m³");
        SensorCacheEntry sensorCacheEntry = createSensorCacheEntry(
                        "device-A",
                        "송이버섯집",
                        "중앙 오른쪽",
                        "TEST123",
                        Set.of(sensorTypeSpec));

        String topic = mqttTopicGenerator.generateTopic(sensorCacheEntry, sensorTypeSpec);

        assertThat(topic).isEqualTo("mushroom/송이버섯집/중앙 오른쪽/TEST123/device-A/PARTICULATE_MATTER")
                .doesNotContain("µg/m³");
    }

    @Test
    @DisplayName("장치에 등록되지 않은 타입과 단위 조합을 거절한다")
    void rejectUnregisteredSensorTypeAndUnitCombination() {
        SensorTypeSpec registeredSensorTypeSpec = new SensorTypeSpec("TEMPERATURE", "°C");
        SensorTypeSpec unregisteredSensorTypeSpec = new SensorTypeSpec("TEMPERATURE", "°F");
        SensorCacheEntry sensorCacheEntry = createSensorCacheEntry(
                        "device-A",
                        "송이버섯집",
                        "중앙 오른쪽",
                        "TEST123",
                        Set.of(registeredSensorTypeSpec));

        assertThatThrownBy(() -> mqttTopicGenerator.generateTopic(
                        sensorCacheEntry, unregisteredSensorTypeSpec))
                .isInstanceOf(InvalidMqttTopicException.class);
    }

    @Test
    @DisplayName("MQTT 토픽 생성의 null 입력을 거절한다")
    void rejectNullInputs() {
        SensorTypeSpec sensorTypeSpec = new SensorTypeSpec("TEMPERATURE", "°C");
        SensorCacheEntry sensorCacheEntry = createSensorCacheEntry(
                        "device-A",
                        "송이버섯집",
                        "중앙 오른쪽",
                        "TEST123",
                        Set.of(sensorTypeSpec));

        assertThatThrownBy(() -> mqttTopicGenerator
                .generateTopic(null, sensorTypeSpec))
                .isInstanceOf(InvalidMqttTopicException.class);

        assertThatThrownBy(() -> mqttTopicGenerator
                .generateTopic(sensorCacheEntry, null))
                .isInstanceOf(InvalidMqttTopicException.class);
    }

    @Test
    @DisplayName("토픽 구성요소에 포함된 MQTT 금지 문자를 거절한다")
    void rejectForbiddenCharactersInTopicComponents() {
        SensorTypeSpec temperatureSpec = new SensorTypeSpec("TEMPERATURE", "°C");
        SensorCacheEntry invalidLocationEntry = createSensorCacheEntry(
                        "device-A",
                        "송이/버섯집",
                        "중앙 오른쪽",
                        "TEST123",
                        Set.of(temperatureSpec));

        SensorCacheEntry invalidLocationDetailEntry = createSensorCacheEntry(
                        "device-A",
                        "송이버섯집",
                        "중앙+오른쪽",
                        "TEST123",
                        Set.of(temperatureSpec));

        SensorCacheEntry invalidDeviceModelEntry = createSensorCacheEntry(
                        "device-A",
                        "송이버섯집",
                        "중앙 오른쪽",
                        "TEST#123",
                        Set.of(temperatureSpec));

        SensorCacheEntry invalidDeviceEuiEntry = createSensorCacheEntry(
                        "device-A" + '\0',
                        "송이버섯집",
                        "중앙 오른쪽",
                        "TEST123",
                        Set.of(temperatureSpec));

        SensorTypeSpec invalidSensorTypeSpec
                = new SensorTypeSpec("PARTICULATE/MATTER", "µg/m³");

        SensorCacheEntry invalidSensorTypeEntry = createSensorCacheEntry(
                        "device-A",
                        "송이버섯집",
                        "중앙 오른쪽",
                        "TEST123",
                        Set.of(invalidSensorTypeSpec));

        assertThatThrownBy(() -> mqttTopicGenerator
                .generateTopic(invalidLocationEntry, temperatureSpec))
                .isInstanceOf(InvalidMqttTopicException.class);

        assertThatThrownBy(() -> mqttTopicGenerator
                .generateTopic(invalidLocationDetailEntry, temperatureSpec))
                .isInstanceOf(InvalidMqttTopicException.class);

        assertThatThrownBy(() -> mqttTopicGenerator
                .generateTopic(invalidDeviceModelEntry, temperatureSpec))
                .isInstanceOf(InvalidMqttTopicException.class);

        assertThatThrownBy(() -> mqttTopicGenerator
                .generateTopic(invalidDeviceEuiEntry, temperatureSpec
                )
        ).isInstanceOf(InvalidMqttTopicException.class);

        assertThatThrownBy(() -> mqttTopicGenerator
                .generateTopic(invalidSensorTypeEntry, invalidSensorTypeSpec))
                .isInstanceOf(InvalidMqttTopicException.class);
    }

    private static SensorCacheEntry createSensorCacheEntry(
            String deviceEui,
            String location,
            String locationDetail,
            String deviceModel,
            Set<SensorTypeSpec> sensorTypes
    ) {
        return new SensorCacheEntry(
                1L,
                deviceEui,
                "TEST123-DEVICE",
                location,
                locationDetail,
                deviceModel,
                sensorTypes
        );
    }
}
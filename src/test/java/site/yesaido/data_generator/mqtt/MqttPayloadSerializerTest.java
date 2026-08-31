package site.yesaido.data_generator.mqtt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.domain.SensorTypeSpec;
import site.yesaido.data_generator.exception.InvalidMqttPayloadException;
import site.yesaido.data_generator.exception.MqttPayloadSerializationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MqttPayloadSerializerTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-08T10:15:30.123Z");
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    private final MqttPayloadSerializer mqttPayloadSerializer = new MqttPayloadSerializer(objectMapper, clock);

    @Test
    @DisplayName("정상 센서값을 확정된 5개 필드의 MQTT JSON으로 직렬화한다")
    void serializeSensorPayloadWithExactContract() throws Exception {
        SensorTypeSpec sensorTypeSpec = new SensorTypeSpec("TEMPERATURE", "°C");
        SensorCacheEntry sensorCacheEntry = createSensorCacheEntry(Set.of(sensorTypeSpec));

        byte[] serializedPayload = mqttPayloadSerializer.serializePayload(
                        23.5,
                        sensorTypeSpec,
                        sensorCacheEntry
                );

        JsonNode payload = objectMapper.readTree(serializedPayload);

        assertThat(payload.size()).isEqualTo(5);

        assertThat(payload.get("value").isNumber()).isTrue();
        assertThat(payload.get("value").doubleValue()).isEqualTo(23.5);
        assertThat(payload.get("unit").asString()).isEqualTo("°C");
        assertThat(payload.get("time").asString()).isEqualTo("2026-08-08T19:15:30.123+09:00");
        assertThat(payload.get("device_name").asString()).isEqualTo("TEST123-DEVICE");
        assertThat(payload.get("device_eui").asString()).isEqualTo("device-A");

        assertThat(payload.has("sensorType")).isFalse();
        assertThat(payload.has("cultivationId")).isFalse();
    }

    @Test
    @DisplayName("같은 타입의 서로 다른 unit을 payload에 각각 보존한다")
    void preserveDifferentUnitsInPayload() throws Exception {
        SensorTypeSpec celsiusSpec = new SensorTypeSpec("TEMPERATURE", "°C");
        SensorTypeSpec fahrenheitSpec = new SensorTypeSpec("TEMPERATURE", "°F");
        SensorCacheEntry sensorCacheEntry = createSensorCacheEntry(Set.of(celsiusSpec, fahrenheitSpec));

        JsonNode celsiusPayload = objectMapper.readTree(
                mqttPayloadSerializer.serializePayload(
                        20.0,
                        celsiusSpec,
                        sensorCacheEntry
                )
        );

        JsonNode fahrenheitPayload = objectMapper.readTree(
                mqttPayloadSerializer.serializePayload(
                        68.0,
                        fahrenheitSpec,
                        sensorCacheEntry
                )
        );

        assertThat(celsiusPayload.get("unit").asString()).isEqualTo("°C");
        assertThat(fahrenheitPayload.get("unit").asString()).isEqualTo("°F");
    }

    @Test
    @DisplayName("슬래시가 포함된 unit도 payload에 그대로 저장한다")
    void preserveUnitContainingSlash() throws Exception {
        SensorTypeSpec sensorTypeSpec = new SensorTypeSpec("PARTICULATE_MATTER", "µg/m³");
        SensorCacheEntry sensorCacheEntry = createSensorCacheEntry(Set.of(sensorTypeSpec));

        JsonNode payload = objectMapper.readTree(
                mqttPayloadSerializer.serializePayload(
                        12.4,
                        sensorTypeSpec,
                        sensorCacheEntry
                )
        );

        assertThat(payload.get("unit").asString()).isEqualTo("µg/m³");
    }

    @Test
    @DisplayName("장치에 등록되지 않은 타입과 단위 조합을 거절한다")
    void rejectUnregisteredSensorChannel() {
        SensorTypeSpec registeredSpec = new SensorTypeSpec("TEMPERATURE", "°C");
        SensorTypeSpec unregisteredSpec = new SensorTypeSpec("TEMPERATURE", "°F");
        SensorCacheEntry sensorCacheEntry = createSensorCacheEntry(Set.of(registeredSpec));

        assertThatThrownBy(() -> mqttPayloadSerializer
                .serializePayload(68.0, unregisteredSpec, sensorCacheEntry))
                .isInstanceOf(InvalidMqttPayloadException.class);
    }

    @Test
    @DisplayName("null 또는 유한하지 않은 센서값을 거절한다")
    void rejectInvalidPayloadValues() {
        SensorTypeSpec sensorTypeSpec = new SensorTypeSpec("TEMPERATURE", "°C");
        SensorCacheEntry sensorCacheEntry = createSensorCacheEntry(Set.of(sensorTypeSpec));

        assertThatThrownBy(() -> mqttPayloadSerializer
                .serializePayload(null, sensorTypeSpec, sensorCacheEntry))
                .isInstanceOf(InvalidMqttPayloadException.class);

        assertThatThrownBy(() -> mqttPayloadSerializer
                .serializePayload(Double.NaN, sensorTypeSpec, sensorCacheEntry))
                .isInstanceOf(InvalidMqttPayloadException.class);

        assertThatThrownBy(() -> mqttPayloadSerializer
                .serializePayload(Double.POSITIVE_INFINITY, sensorTypeSpec, sensorCacheEntry))
                .isInstanceOf(InvalidMqttPayloadException.class);

        assertThatThrownBy(() -> mqttPayloadSerializer
                .serializePayload(20.0, null, sensorCacheEntry))
                .isInstanceOf(InvalidMqttPayloadException.class);

        assertThatThrownBy(() -> mqttPayloadSerializer
                .serializePayload(20.0, sensorTypeSpec, null))
                .isInstanceOf(InvalidMqttPayloadException.class);
    }

    @Test
    @DisplayName("직렬화기의 필수 의존성이 null이면 생성하지 않는다")
    void rejectNullSerializerDependencies() {
        assertThatThrownBy(() -> new MqttPayloadSerializer(null, clock))
                .isInstanceOf(MqttPayloadSerializationException.class);

        assertThatThrownBy(() -> new MqttPayloadSerializer(objectMapper, null))
                .isInstanceOf(MqttPayloadSerializationException.class);
    }

    private static SensorCacheEntry createSensorCacheEntry(
            Set<SensorTypeSpec> sensorTypes
    ) {
        return new SensorCacheEntry(
                1L,
                "device-A",
                "TEST123-DEVICE",
                "송이버섯집",
                "중앙 오른쪽",
                "TEST123",
                sensorTypes
        );
    }
}

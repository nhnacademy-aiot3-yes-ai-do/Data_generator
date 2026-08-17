package site.yesaido.data_generator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.data_generator.cache.SensorCache;
import site.yesaido.data_generator.domain.ActuatorType;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.domain.SensorTypeSpec;
import site.yesaido.data_generator.exception.SensorDataGenerationException;
import site.yesaido.data_generator.generator.SensorValueGenerationResolver;
import site.yesaido.data_generator.mqtt.MqttPayloadSerializer;
import site.yesaido.data_generator.mqtt.MqttPublishable;
import site.yesaido.data_generator.mqtt.MqttTopicGenerator;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CultivationDataGenerationServiceTest {

    @Mock
    private SensorCache sensorCache;

    @Mock
    private MqttTopicGenerator mqttTopicGenerator;

    @Mock
    private MqttPayloadSerializer mqttPayloadSerializer;

    @Mock
    private MqttPublishable mqttPublishable;

    @Mock
    private VirtualActuatorService virtualActuatorService;

    @Mock
    private SensorValueGenerationResolver sensorValueGenerationResolver;

    private CultivationDataGenerationService cultivationDataGenerationService;

    @BeforeEach
    void setUp() {
        cultivationDataGenerationService = new CultivationDataGenerationService(
                        sensorCache,
                        mqttTopicGenerator,
                        mqttPayloadSerializer,
                        mqttPublishable,
                        virtualActuatorService,
                        sensorValueGenerationResolver
                );
    }

    @Test
    @DisplayName("등록된 각 센서 채널의 값을 생성하여 MQTT 발행을 요청한다")
    void generateAndPublishEveryRegisteredSensorChannel() {
        SensorTypeSpec celsiusSpec = new SensorTypeSpec("TEMPERATURE", "°C");
        SensorTypeSpec fahrenheitSpec = new SensorTypeSpec("TEMPERATURE", "°F");
        SensorCacheEntry sensorCacheEntry = createSensorCacheEntry(
                "device-A", Set.of(celsiusSpec, fahrenheitSpec));

        SensorChannelKey celsiusKey = new SensorChannelKey(
                        "device-A",
                        "TEMPERATURE",
                        "°C"
                );

        SensorChannelKey fahrenheitKey = new SensorChannelKey(
                        "device-A",
                        "TEMPERATURE",
                        "°F"
                );

        String topic = "mushroom/송이버섯집/중앙 오른쪽/TEST123/device-A/TEMPERATURE";

        byte[] celsiusPayload = {1};
        byte[] fahrenheitPayload = {2};

        when(virtualActuatorService.getActiveActuatorTypesSnapshot(1L))
                .thenReturn(Set.of(ActuatorType.HEATER, ActuatorType.LED));

        when(sensorCache.findByDeviceEui("device-A")).thenReturn(Optional.of(sensorCacheEntry));

        when(sensorValueGenerationResolver.generateNextValue(1L, celsiusKey, 0.5))
                .thenReturn(Optional.of(20.0));
        when(sensorValueGenerationResolver.generateNextValue(1L, fahrenheitKey, 0.5))
                .thenReturn(Optional.of(68.0));

        when(mqttTopicGenerator.generateTopic(sensorCacheEntry, celsiusSpec)).thenReturn(topic);
        when(mqttTopicGenerator.generateTopic(sensorCacheEntry, fahrenheitSpec)).thenReturn(topic);

        when(mqttPayloadSerializer.serializePayload(20.0, celsiusSpec, sensorCacheEntry))
                .thenReturn(celsiusPayload);
        when(mqttPayloadSerializer.serializePayload(68.0, fahrenheitSpec, sensorCacheEntry))
                .thenReturn(fahrenheitPayload);

        when(mqttPublishable.publishMessage(topic, celsiusPayload))
                .thenReturn(CompletableFuture.completedFuture(null));

        when(mqttPublishable.publishMessage(topic, fahrenheitPayload))
                .thenReturn(CompletableFuture.completedFuture(null));

        cultivationDataGenerationService.generateAndPublishSensorData(1L, List.of(sensorCacheEntry));

        verify(sensorValueGenerationResolver).generateNextValue(1L, celsiusKey, 0.5);
        verify(sensorValueGenerationResolver).generateNextValue(1L, fahrenheitKey, 0.5);

        verify(mqttPublishable).publishMessage(topic, celsiusPayload);
        verify(mqttPublishable).publishMessage(topic, fahrenheitPayload);

        verify(sensorValueGenerationResolver, never()).removeState(any());
    }

    @Test
    @DisplayName("생성할 수 없는 센서 채널은 MQTT 발행을 건너뛴다")
    void skipChannelWhenGeneratedValueDoesNotExist() {
        SensorTypeSpec sensorTypeSpec = new SensorTypeSpec("SOIL_MOISTURE", "%");
        SensorCacheEntry sensorCacheEntry = createSensorCacheEntry("device-A", Set.of(sensorTypeSpec));
        SensorChannelKey sensorChannelKey = new SensorChannelKey("device-A", "SOIL_MOISTURE", "%");

        when(virtualActuatorService.getActiveActuatorTypesSnapshot(1L))
                .thenReturn(Set.of());

        when(sensorCache.findByDeviceEui("device-A"))
                .thenReturn(Optional.of(sensorCacheEntry));

        when(sensorValueGenerationResolver.generateNextValue(1L, sensorChannelKey, 0.0))
                .thenReturn(Optional.empty());

        cultivationDataGenerationService.generateAndPublishSensorData(1L, List.of(sensorCacheEntry));

        verify(sensorValueGenerationResolver).generateNextValue(1L, sensorChannelKey, 0.0);

        verifyNoInteractions(mqttTopicGenerator, mqttPayloadSerializer, mqttPublishable);

        verify(sensorValueGenerationResolver, never()).removeState(any());
    }

    @Test
    @DisplayName("실행 중 삭제된 정확한 센서 채널의 생성 상태만 제거한다")
    void removeOnlyDeletedSensorChannelState() {
        SensorTypeSpec celsiusSpec = new SensorTypeSpec("TEMPERATURE", "°C");
        SensorTypeSpec fahrenheitSpec = new SensorTypeSpec("TEMPERATURE", "°F");

        SensorCacheEntry snapshotEntry = createSensorCacheEntry("device-A", Set.of(celsiusSpec, fahrenheitSpec));
        SensorCacheEntry currentEntry = createSensorCacheEntry("device-A", Set.of(celsiusSpec));

        SensorChannelKey celsiusKey = new SensorChannelKey("device-A", "TEMPERATURE", "°C");
        SensorChannelKey fahrenheitKey = new SensorChannelKey("device-A", "TEMPERATURE", "°F");

        when(virtualActuatorService.getActiveActuatorTypesSnapshot(1L))
                .thenReturn(Set.of());

        when(sensorCache.findByDeviceEui("device-A"))
                .thenReturn(Optional.of(currentEntry));

        cultivationDataGenerationService.generateAndPublishSensorData(1L, List.of(snapshotEntry));

        verify(sensorValueGenerationResolver).removeState(fahrenheitKey);
        verify(sensorValueGenerationResolver, never()).removeState(celsiusKey);
        verify(sensorValueGenerationResolver, never()).generateNextValue(anyLong(), any(), anyDouble());

        verifyNoInteractions(mqttTopicGenerator, mqttPayloadSerializer, mqttPublishable);
    }

    @Test
    @DisplayName("한 장치의 센서값 생성 실패 후에도 다음 장치의 MQTT 발행을 계속한다")
    void continuePublishingNextDeviceAfterSensorValueGenerationFailure() {
        SensorTypeSpec firstSensorTypeSpec = new SensorTypeSpec("TEMPERATURE", "°C");
        SensorTypeSpec secondSensorTypeSpec = new SensorTypeSpec("HUMIDITY", "%RH");

        SensorCacheEntry firstSensorCacheEntry = createSensorCacheEntry("device-A", Set.of(firstSensorTypeSpec));
        SensorCacheEntry secondSensorCacheEntry = createSensorCacheEntry("device-B", Set.of(secondSensorTypeSpec));

        SensorChannelKey firstSensorChannelKey = new SensorChannelKey("device-A", "TEMPERATURE", "°C");
        SensorChannelKey secondSensorChannelKey = new SensorChannelKey("device-B", "HUMIDITY", "%RH");

        String firstTopic = "mushroom/송이버섯집/중앙 오른쪽/TEST123/device-A/TEMPERATURE";
        String secondTopic = "mushroom/송이버섯집/중앙 오른쪽/TEST123/device-B/HUMIDITY";

        byte[] secondPayload = {2};

        when(virtualActuatorService.getActiveActuatorTypesSnapshot(1L)).thenReturn(Set.of());
        when(sensorCache.findByDeviceEui("device-A")).thenReturn(Optional.of(firstSensorCacheEntry));
        when(sensorCache.findByDeviceEui("device-B")).thenReturn(Optional.of(secondSensorCacheEntry));
        when(sensorValueGenerationResolver.generateNextValue(1L, firstSensorChannelKey, 0.0))
                .thenThrow(new RuntimeException("의도적인 센서값 생성 실패"));

        when(sensorValueGenerationResolver.generateNextValue(1L, secondSensorChannelKey, 0.0))
                .thenReturn(Optional.of(80.0));

        when(mqttTopicGenerator.generateTopic(secondSensorCacheEntry, secondSensorTypeSpec))
                .thenReturn(secondTopic);

        when(mqttPayloadSerializer.serializePayload(80.0, secondSensorTypeSpec, secondSensorCacheEntry))
                .thenReturn(secondPayload);

        when(mqttPublishable.publishMessage(secondTopic, secondPayload))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertThatCode(() -> cultivationDataGenerationService
                .generateAndPublishSensorData(1L, List.of(firstSensorCacheEntry, secondSensorCacheEntry)))
                .doesNotThrowAnyException();

        InOrder resolverCallOrder = inOrder(sensorValueGenerationResolver);

        resolverCallOrder.verify(sensorValueGenerationResolver)
                .generateNextValue(1L, firstSensorChannelKey, 0.0);

        resolverCallOrder.verify(sensorValueGenerationResolver)
                .generateNextValue(1L, secondSensorChannelKey, 0.0);

        verify(mqttTopicGenerator, never()).generateTopic(firstSensorCacheEntry, firstSensorTypeSpec);
        verify(mqttPayloadSerializer, never()).serializePayload(any(), eq(firstSensorTypeSpec), eq(firstSensorCacheEntry));
        verify(mqttPublishable, never()).publishMessage(eq(firstTopic), any());

        verify(mqttTopicGenerator).generateTopic(secondSensorCacheEntry, secondSensorTypeSpec);
        verify(mqttPayloadSerializer).serializePayload(80.0, secondSensorTypeSpec, secondSensorCacheEntry);
        verify(mqttPublishable).publishMessage(secondTopic, secondPayload);

        verify(sensorValueGenerationResolver, never()).removeState(any());
        verifyNoMoreInteractions(mqttTopicGenerator, mqttPayloadSerializer, mqttPublishable);
    }

    @Test
    @DisplayName("MQTT 비동기 발행 실패를 서비스 호출자에게 전파하지 않는다")
    void doNotPropagateAsynchronousMqttPublishFailure() {
        SensorTypeSpec sensorTypeSpec = new SensorTypeSpec("TEMPERATURE", "°C");
        SensorCacheEntry sensorCacheEntry = createSensorCacheEntry("device-A", Set.of(sensorTypeSpec));
        SensorChannelKey sensorChannelKey = new SensorChannelKey("device-A", "TEMPERATURE", "°C");
        String topic = "mushroom/송이버섯집/중앙 오른쪽/TEST123/device-A/TEMPERATURE";

        byte[] payload = {1};

        when(virtualActuatorService.getActiveActuatorTypesSnapshot(1L)).thenReturn(Set.of());

        when(sensorCache.findByDeviceEui("device-A")).thenReturn(Optional.of(sensorCacheEntry));
        when(sensorValueGenerationResolver.generateNextValue(1L, sensorChannelKey, 0.0))
                .thenReturn(Optional.of(20.0));

        when(mqttTopicGenerator.generateTopic(sensorCacheEntry, sensorTypeSpec)).thenReturn(topic);
        when(mqttPayloadSerializer.serializePayload(20.0, sensorTypeSpec, sensorCacheEntry))
                .thenReturn(payload);

        when(mqttPublishable.publishMessage(topic, payload))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("의도적인 MQTT 비동기 발행 실패")));

        assertThatCode(() -> cultivationDataGenerationService
                .generateAndPublishSensorData(1L, List.of(sensorCacheEntry)))
                .doesNotThrowAnyException();

        verify(sensorValueGenerationResolver).generateNextValue(1L, sensorChannelKey, 0.0);
        verify(mqttTopicGenerator).generateTopic(sensorCacheEntry, sensorTypeSpec);
        verify(mqttPayloadSerializer).serializePayload(20.0, sensorTypeSpec, sensorCacheEntry);
        verify(mqttPublishable).publishMessage(topic, payload);
        verify(sensorValueGenerationResolver, never()).removeState(any());
    }

    @Test
    @DisplayName("잘못된 생성 요청을 외부 의존성 호출 전에 거절한다")
    void rejectInvalidGenerationRequestsBeforeUsingDependencies() {
        SensorTypeSpec sensorTypeSpec = new SensorTypeSpec("TEMPERATURE", "°C");

        SensorCacheEntry sensorCacheEntry = createSensorCacheEntry("device-A", Set.of(sensorTypeSpec));

        assertThatThrownBy(() -> cultivationDataGenerationService
                .generateAndPublishSensorData(0L, List.of(sensorCacheEntry)))
                .isInstanceOf(SensorDataGenerationException.class);

        assertThatThrownBy(() -> cultivationDataGenerationService.generateAndPublishSensorData(1L, null))
                .isInstanceOf(SensorDataGenerationException.class);

        assertThatThrownBy(() -> cultivationDataGenerationService.generateAndPublishSensorData(1L, Collections.singletonList(null)))
                .isInstanceOf(SensorDataGenerationException.class);

        assertThatThrownBy(() -> cultivationDataGenerationService.generateAndPublishSensorData(2L, List.of(sensorCacheEntry)))
                .isInstanceOf(SensorDataGenerationException.class);

        verifyNoInteractions(
                sensorCache,
                mqttTopicGenerator,
                mqttPayloadSerializer,
                mqttPublishable,
                virtualActuatorService,
                sensorValueGenerationResolver
        );
    }

    private static SensorCacheEntry createSensorCacheEntry(
            String deviceEui,
            Set<SensorTypeSpec> sensorTypes
    ) {
        return new SensorCacheEntry(
                1L,
                deviceEui,
                "TEST123-DEVICE",
                "송이버섯집",
                "중앙 오른쪽",
                "TEST123",
                sensorTypes
        );
    }

}

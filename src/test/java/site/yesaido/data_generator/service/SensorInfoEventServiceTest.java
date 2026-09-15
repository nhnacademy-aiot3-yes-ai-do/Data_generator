package site.yesaido.data_generator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.data_generator.cache.SensorCache;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.domain.SensorTypeSpec;
import site.yesaido.data_generator.exception.SensorSynchronizationException;
import site.yesaido.data_generator.rabbitmq.event.SensorInfoDeleteEvent;
import site.yesaido.data_generator.rabbitmq.event.SensorInfoUpsertEvent;

import java.time.OffsetDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorInfoEventServiceTest {

    private static final OffsetDateTime OCCURRED_AT =
            OffsetDateTime.parse("2026-08-17T06:00:00Z");

    @Mock
    private SharedGenerationStateLifecycle sharedGenerationStateLifecycle;

    private SensorCache sensorCache;
    private SensorInfoEventService service;

    @BeforeEach
    void setUp() {
        sensorCache = spy(new SensorCache());

        service = new SensorInfoEventService(
                sensorCache,
                sharedGenerationStateLifecycle
        );
    }

    @Test
    @DisplayName("Upsert 이벤트를 센서 캐시에 반영한다")
    void processUpsertEvent() {
        SensorInfoUpsertEvent event =
                upsertEvent(
                        1L,
                        "device-A",
                        "TEMPERATURE",
                        "°C"
                );

        service.processUpsertEvent(event);

        assertThat(
                sensorCache.findByDeviceEui("device-A")
        )
                .get()
                .satisfies(entry -> {
                    assertThat(entry.cultivationId())
                            .isEqualTo(1L);

                    assertThat(entry.sensorTypes())
                            .containsExactly(
                                    new SensorTypeSpec(
                                            "TEMPERATURE",
                                            "°C"
                                    )
                            );
                });

        verifyNoInteractions(sharedGenerationStateLifecycle);
    }

    @Test
    @DisplayName("null Upsert 및 Delete 이벤트를 거부한다")
    void rejectNullEvents() {
        assertThatThrownBy(() ->
                service.processUpsertEvent(null))
                .isInstanceOf(
                        SensorSynchronizationException.class
                );

        assertThatThrownBy(() ->
                service.processDeleteEvent(null))
                .isInstanceOf(
                        SensorSynchronizationException.class
                );

        verifyNoInteractions(sharedGenerationStateLifecycle);
    }

    @Test
    @DisplayName("Delete 이벤트는 캐시 삭제 후 공유 생성 상태를 정리한다")
    void processDeleteEvent() {
        SensorTypeSpec celsius =
                new SensorTypeSpec(
                        "TEMPERATURE",
                        "°C"
                );

        SensorTypeSpec fahrenheit =
                new SensorTypeSpec(
                        "TEMPERATURE",
                        "°F"
                );

        sensorCache.upsert(
                cacheEntry(
                        1L,
                        "device-A",
                        Set.of(celsius, fahrenheit)
                )
        );

        SensorInfoDeleteEvent event =
                deleteEvent(
                        1L,
                        "device-A",
                        "TEMPERATURE",
                        "°C"
                );

        SensorChannelKey deletedChannelKey =
                new SensorChannelKey(
                        "device-A",
                        "TEMPERATURE",
                        "°C"
                );

        service.processDeleteEvent(event);

        assertThat(
                sensorCache.findByDeviceEui("device-A")
        )
                .get()
                .extracting(SensorCacheEntry::sensorTypes)
                .isEqualTo(Set.of(fahrenheit));

        InOrder deletionOrder = inOrder(
                sensorCache,
                sharedGenerationStateLifecycle
        );

        deletionOrder.verify(sensorCache)
                .removeChannel(deletedChannelKey);

        deletionOrder.verify(sharedGenerationStateLifecycle)
                .removeDeletedChannelState(
                        1L,
                        deletedChannelKey
                );
    }

    @Test
    @DisplayName("캐시에 없는 장치의 Delete 이벤트도 멱등하게 상태 정리를 요청한다")
    void processDeleteForMissingDeviceIdempotently() {
        SensorInfoDeleteEvent event =
                deleteEvent(
                        1L,
                        "device-A",
                        "TEMPERATURE",
                        "°C"
                );

        SensorChannelKey deletedChannelKey =
                new SensorChannelKey(
                        "device-A",
                        "TEMPERATURE",
                        "°C"
                );

        service.processDeleteEvent(event);

        assertThat(sensorCache.getSnapshot())
                .isEmpty();

        InOrder deletionOrder = inOrder(
                sensorCache,
                sharedGenerationStateLifecycle
        );

        deletionOrder.verify(sensorCache)
                .removeChannel(deletedChannelKey);

        deletionOrder.verify(sharedGenerationStateLifecycle)
                .removeDeletedChannelState(
                        1L,
                        deletedChannelKey
                );
    }

    @Test
    @DisplayName("다른 cultivation 소속 장치를 삭제하려는 이벤트를 거부한다")
    void rejectDeleteForDifferentCultivation() {
        sensorCache.upsert(
                cacheEntry(
                        1L,
                        "device-A",
                        Set.of(
                                new SensorTypeSpec(
                                        "TEMPERATURE",
                                        "°C"
                                )
                        )
                )
        );

        SensorInfoDeleteEvent event =
                deleteEvent(
                        2L,
                        "device-A",
                        "TEMPERATURE",
                        "°C"
                );

        SensorChannelKey channelKey =
                new SensorChannelKey(
                        "device-A",
                        "TEMPERATURE",
                        "°C"
                );

        assertThatThrownBy(() ->
                service.processDeleteEvent(event))
                .isInstanceOf(
                        SensorSynchronizationException.class
                )
                .hasMessageContaining(
                        "현재 센서 소속과 다릅니다"
                );

        assertThat(
                sensorCache.findByDeviceEui("device-A")
        )
                .isPresent();

        verify(sensorCache, never())
                .removeChannel(channelKey);

        verify(sharedGenerationStateLifecycle, never())
                .removeDeletedChannelState(
                        2L,
                        channelKey
                );
    }

    private static SensorInfoUpsertEvent upsertEvent(
            long cultivationId,
            String deviceEui,
            String sensorType,
            String unit
    ) {
        return new SensorInfoUpsertEvent(
                cultivationId,
                "location",
                "location-detail",
                "model",
                "device-name",
                deviceEui,
                sensorType,
                unit,
                OCCURRED_AT
        );
    }

    private static SensorInfoDeleteEvent deleteEvent(
            long cultivationId,
            String deviceEui,
            String sensorType,
            String unit
    ) {
        return new SensorInfoDeleteEvent(
                cultivationId,
                deviceEui,
                sensorType,
                unit,
                OCCURRED_AT
        );
    }

    private static SensorCacheEntry cacheEntry(
            long cultivationId,
            String deviceEui,
            Set<SensorTypeSpec> sensorTypes
    ) {
        return new SensorCacheEntry(
                cultivationId,
                deviceEui,
                "device-name",
                "location",
                "location-detail",
                "model",
                sensorTypes
        );
    }
}

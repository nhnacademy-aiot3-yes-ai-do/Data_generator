package site.yesaido.data_generator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import site.yesaido.data_generator.cache.SensorCache;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.domain.SensorTypeSpec;
import site.yesaido.data_generator.exception.SensorSynchronizationException;
import site.yesaido.data_generator.generator.SensorValueGenerationResolver;
import site.yesaido.data_generator.rabbitmq.event.SensorInfoDeleteEvent;
import site.yesaido.data_generator.rabbitmq.event.SensorInfoUpsertEvent;

import java.time.OffsetDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SensorInfoEventServiceTest {

    private static final OffsetDateTime OCCURRED_AT = OffsetDateTime.parse("2026-08-17T06:00:00Z");

    @Mock
    private SensorValueGenerationResolver sensorValueGenerationResolver;

    private SensorCache sensorCache;
    private SensorInfoEventService service;

    @BeforeEach
    void setUp() {
        sensorCache = new SensorCache();
        service = new SensorInfoEventService(sensorCache, sensorValueGenerationResolver);
    }

    @Test
    @DisplayName("Upsert 이벤트를 센서 캐시에 반영한다")
    void processUpsertEvent() {
        SensorInfoUpsertEvent event = upsertEvent(1L, "device-A", "TEMPERATURE", "°C");

        service.processUpsertEvent(event);

        assertThat(sensorCache.findByDeviceEui("device-A")).get()
                .satisfies(entry -> {
                    assertThat(entry.cultivationId()).isEqualTo(1L);
                    assertThat(entry.sensorTypes()).containsExactly(new SensorTypeSpec("TEMPERATURE", "°C"));
                });
    }

    @Test
    @DisplayName("null Upsert 및 Delete 이벤트를 거부한다")
    void rejectNullEvents() {
        assertThatThrownBy(() -> service.processUpsertEvent(null))
                .isInstanceOf(SensorSynchronizationException.class);
        assertThatThrownBy(() -> service.processDeleteEvent(null))
                .isInstanceOf(SensorSynchronizationException.class);
    }

    @Test
    @DisplayName("Delete 이벤트는 정확한 채널과 생성 상태만 제거한다")
    void processDeleteEvent() {
        SensorTypeSpec celsius = new SensorTypeSpec("TEMPERATURE", "°C");
        SensorTypeSpec fahrenheit = new SensorTypeSpec("TEMPERATURE", "°F");
        sensorCache.upsert(cacheEntry(1L, "device-A", Set.of(celsius, fahrenheit)));
        SensorInfoDeleteEvent event = deleteEvent(1L, "device-A", "TEMPERATURE", "°C");

        service.processDeleteEvent(event);

        assertThat(sensorCache.findByDeviceEui("device-A")).get()
                .extracting(SensorCacheEntry::sensorTypes)
                .isEqualTo(Set.of(fahrenheit));
        verify(sensorValueGenerationResolver)
                .removeState(new SensorChannelKey("device-A", "TEMPERATURE", "°C"));
    }

    @Test
    @DisplayName("캐시에 없는 장치의 Delete 이벤트도 멱등하게 상태 제거를 요청한다")
    void processDeleteForMissingDeviceIdempotently() {
        SensorInfoDeleteEvent event = deleteEvent(1L, "device-A", "TEMPERATURE", "°C");

        service.processDeleteEvent(event);

        assertThat(sensorCache.getSnapshot()).isEmpty();
        verify(sensorValueGenerationResolver)
                .removeState(new SensorChannelKey("device-A", "TEMPERATURE", "°C"));
    }

    @Test
    @DisplayName("다른 cultivation 소속 장치를 삭제하려는 이벤트를 거부한다")
    void rejectDeleteForDifferentCultivation() {
        sensorCache.upsert(cacheEntry(
                1L,
                "device-A",
                Set.of(new SensorTypeSpec("TEMPERATURE", "°C"))
        ));
        SensorInfoDeleteEvent event = deleteEvent(2L, "device-A", "TEMPERATURE", "°C");

        assertThatThrownBy(() -> service.processDeleteEvent(event))
                .isInstanceOf(SensorSynchronizationException.class)
                .hasMessageContaining("현재 센서 소속과 다릅니다");

        assertThat(sensorCache.findByDeviceEui("device-A")).isPresent();
        verify(sensorValueGenerationResolver, never()).removeState(new SensorChannelKey("device-A", "TEMPERATURE", "°C"));
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
        return new SensorInfoDeleteEvent(cultivationId, deviceEui, sensorType, unit, OCCURRED_AT);
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

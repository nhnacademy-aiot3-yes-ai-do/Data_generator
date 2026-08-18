package site.yesaido.data_generator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.data_generator.cache.SensorCache;
import site.yesaido.data_generator.cache.SensorThresholdCache;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.domain.SensorThresholdKey;
import site.yesaido.data_generator.domain.SensorThresholdRange;
import site.yesaido.data_generator.domain.SensorTypeSpec;
import site.yesaido.data_generator.exception.SensorSynchronizationException;
import site.yesaido.data_generator.generator.SensorValueGenerationResolver;
import site.yesaido.data_generator.rabbitmq.event.SensorRange;
import site.yesaido.data_generator.rabbitmq.event.ThresholdInfoEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ThresholdInfoEventServiceTest {

    private static final OffsetDateTime OCCURRED_AT = OffsetDateTime.parse("2026-08-17T06:00:00Z");

    @Mock
    private SensorValueGenerationResolver sensorValueGenerationResolver;

    @Mock
    private VirtualActuatorService virtualActuatorService;

    private SensorThresholdCache sensorThresholdCache;
    private SensorCache sensorCache;
    private ThresholdInfoEventService service;

    @BeforeEach
    void setUp() {
        sensorThresholdCache = new SensorThresholdCache();
        sensorCache = new SensorCache();
        service = new ThresholdInfoEventService(
                sensorThresholdCache,
                sensorCache,
                sensorValueGenerationResolver,
                virtualActuatorService
        );
    }

    @Test
    @DisplayName("1개 이상의 임계값 이벤트는 모두 Upsert한다")
    void upsertEveryThresholdInEvent() {
        ThresholdInfoEvent event = new ThresholdInfoEvent(
                1L,
                List.of(
                        range("TEMPERATURE", "°C", "10", "30"),
                        range("HUMIDITY", "%", "40", "80")
                ),
                OCCURRED_AT
        );

        service.processThresholdEvent(event);

        assertThat(sensorThresholdCache.getThresholdCount()).isEqualTo(2);
        assertThat(sensorThresholdCache.find(new SensorThresholdKey(1L, "TEMPERATURE", "°C")))
                .contains(new SensorThresholdRange(new BigDecimal("10"), new BigDecimal("30")));
        assertThat(sensorThresholdCache.find(new SensorThresholdKey(1L, "HUMIDITY", "%")))
                .contains(new SensorThresholdRange(new BigDecimal("40"), new BigDecimal("80")));
        verifyNoInteractions(sensorValueGenerationResolver, virtualActuatorService);
    }

    @Test
    @DisplayName("이벤트 안의 중복 임계값 키를 거부하고 부분 반영하지 않는다")
    void rejectDuplicateThresholdKey() {
        ThresholdInfoEvent event = new ThresholdInfoEvent(
                1L,
                List.of(
                        range("TEMPERATURE", "°C", "10", "30"),
                        range("TEMPERATURE", "°C", "11", "31")
                ),
                OCCURRED_AT
        );

        assertThatThrownBy(() -> service.processThresholdEvent(event))
                .isInstanceOf(SensorSynchronizationException.class)
                .hasMessageContaining("중복 임계값 키");

        assertThat(sensorThresholdCache.getSnapshot()).isEmpty();
    }

    @Test
    @DisplayName("빈 임계값 이벤트는 해당 cultivation의 생성 상태와 캐시만 정리한다")
    void stopOnlyTargetCultivationForEmptyEvent() {
        SensorTypeSpec celsius = new SensorTypeSpec("TEMPERATURE", "°C");
        SensorTypeSpec humidity = new SensorTypeSpec("HUMIDITY", "%");
        SensorTypeSpec co2 = new SensorTypeSpec("CO2", "ppm");
        sensorCache.replaceAll(List.of(
                cacheEntry(1L, "device-A", Set.of(celsius, humidity)),
                cacheEntry(2L, "device-B", Set.of(co2))
        ));
        SensorThresholdKey targetKey = new SensorThresholdKey(1L, "TEMPERATURE", "°C");
        SensorThresholdKey otherKey = new SensorThresholdKey(2L, "CO2", "ppm");
        SensorThresholdRange thresholdRange = new SensorThresholdRange(
                new BigDecimal("1"),
                new BigDecimal("2")
        );
        sensorThresholdCache.replaceAll(Map.of(targetKey, thresholdRange, otherKey, thresholdRange));

        service.processThresholdEvent(new ThresholdInfoEvent(1L, List.of(), OCCURRED_AT));

        assertThat(sensorCache.findByDeviceEui("device-A")).isEmpty();
        assertThat(sensorCache.findByDeviceEui("device-B")).isPresent();
        assertThat(sensorThresholdCache.find(targetKey)).isEmpty();
        assertThat(sensorThresholdCache.find(otherKey)).contains(thresholdRange);
        verify(sensorValueGenerationResolver)
                .removeState(new SensorChannelKey("device-A", "TEMPERATURE", "°C"));
        verify(sensorValueGenerationResolver)
                .removeState(new SensorChannelKey("device-A", "HUMIDITY", "%"));
        verify(sensorValueGenerationResolver, never())
                .removeState(new SensorChannelKey("device-B", "CO2", "ppm"));
        verify(virtualActuatorService).removeCultivationState(1L);
    }

    @Test
    @DisplayName("null 임계값 이벤트를 거부한다")
    void rejectNullEvent() {
        assertThatThrownBy(() -> service.processThresholdEvent(null))
                .isInstanceOf(SensorSynchronizationException.class);
    }

    private static SensorRange range(
            String sensorType,
            String unit,
            String minValue,
            String maxValue
    ) {
        return new SensorRange(
                sensorType,
                unit,
                new BigDecimal(minValue),
                new BigDecimal(maxValue)
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

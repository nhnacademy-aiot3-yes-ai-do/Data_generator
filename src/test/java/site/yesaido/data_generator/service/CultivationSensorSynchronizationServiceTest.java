package site.yesaido.data_generator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.data_generator.cache.SensorCache;
import site.yesaido.data_generator.cache.SensorThresholdCache;
import site.yesaido.data_generator.client.CultivationSensorReadable;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.domain.SensorThresholdKey;
import site.yesaido.data_generator.domain.SensorThresholdRange;
import site.yesaido.data_generator.domain.SensorTypeSpec;
import site.yesaido.data_generator.dto.response.CultivationSensorTypeResponse;
import site.yesaido.data_generator.dto.response.DataGeneratorSensorResponse;
import site.yesaido.data_generator.dto.response.DataGeneratorSnapshotResponse;
import site.yesaido.data_generator.dto.response.DataGeneratorThresholdResponse;
import site.yesaido.data_generator.exception.SensorSynchronizationException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CultivationSensorSynchronizationServiceTest {

    private static final OffsetDateTime SNAPSHOT_AT = OffsetDateTime.parse("2026-08-17T06:00:00Z");

    @Mock
    private CultivationSensorReadable cultivationSensorReadable;

    private SensorCache sensorCache;
    private SensorThresholdCache sensorThresholdCache;
    private CultivationSensorSynchronizationService service;

    @BeforeEach
    void setUp() {
        sensorCache = new SensorCache();
        sensorThresholdCache = new SensorThresholdCache();
        service = new CultivationSensorSynchronizationService(
                cultivationSensorReadable,
                sensorCache,
                sensorThresholdCache
        );
    }

    @Test
    @DisplayName("snapshot의 센서와 임계값을 검증한 뒤 두 캐시를 전체 교체한다")
    void synchronizeValidSnapshot() {
        SensorCacheEntry staleSensor = new SensorCacheEntry(
                99L,
                "stale-device",
                "stale-name",
                "stale-location",
                "stale-detail",
                "stale-model",
                Set.of(new SensorTypeSpec("LIGHT", "lux"))
        );
        SensorThresholdKey staleThresholdKey =
                new SensorThresholdKey(99L, "LIGHT", "lux");
        SensorThresholdRange staleThresholdRange =
                new SensorThresholdRange(BigDecimal.ZERO, BigDecimal.ONE);
        sensorCache.replaceAll(List.of(staleSensor));
        sensorThresholdCache.replaceAll(Map.of(staleThresholdKey, staleThresholdRange));

        DataGeneratorSensorResponse firstSensor = sensor(
                1L,
                "device-A",
                List.of(
                        new CultivationSensorTypeResponse("TEMPERATURE", "°C"),
                        new CultivationSensorTypeResponse("HUMIDITY", "%RH")
                )
        );
        DataGeneratorSensorResponse secondSensor = sensor(
                2L,
                "device-B",
                List.of(new CultivationSensorTypeResponse("CO2", "ppm"))
        );
        DataGeneratorThresholdResponse threshold = threshold(1L, "TEMPERATURE", "°C", "10", "30");

        when(cultivationSensorReadable.getSnapshot()).thenReturn(
                new DataGeneratorSnapshotResponse(
                        SNAPSHOT_AT,
                        List.of(firstSensor, secondSensor),
                        List.of(threshold)
                )
        );

        service.synchronizeAllSensors();

        assertThat(sensorCache.isInitialSynchronizationCompleted()).isTrue();
        assertThat(sensorCache.getSnapshot()).hasSize(2);
        assertThat(sensorCache.findByDeviceEui("device-A")).get()
                .extracting(entry -> entry.sensorTypes().size())
                .isEqualTo(2);
        assertThat(sensorCache.findByDeviceEui("stale-device")).isEmpty();
        assertThat(sensorThresholdCache.isInitialSynchronizationCompleted()).isTrue();
        assertThat(sensorThresholdCache.find(staleThresholdKey)).isEmpty();
        assertThat(sensorThresholdCache.find(new SensorThresholdKey(1L, "TEMPERATURE", "°C")))
                .get()
                .satisfies(range -> {
                    assertThat(range.thresholdMin()).isEqualByComparingTo("10");
                    assertThat(range.thresholdMax()).isEqualByComparingTo("30");
                });
    }

    @Test
    @DisplayName("빈 snapshot도 정상적인 전체 교체로 처리한다")
    void synchronizeEmptySnapshot() {
        when(cultivationSensorReadable.getSnapshot()).thenReturn(
                new DataGeneratorSnapshotResponse(SNAPSHOT_AT, List.of(), List.of())
        );

        service.synchronizeAllSensors();

        assertThat(sensorCache.isInitialSynchronizationCompleted()).isTrue();
        assertThat(sensorThresholdCache.isInitialSynchronizationCompleted()).isTrue();
        assertThat(sensorCache.getSnapshot()).isEmpty();
        assertThat(sensorThresholdCache.getSnapshot()).isEmpty();
    }

    @Test
    @DisplayName("잘못된 snapshot이면 기존 센서와 임계값 캐시를 보존한다")
    void preserveExistingCachesWhenSnapshotIsInvalid() {
        SensorCacheEntry existingSensor = new SensorCacheEntry(
                1L,
                "existing-device",
                "existing-name",
                "existing-location",
                "existing-detail",
                "existing-model",
                Set.of(new SensorTypeSpec("TEMPERATURE", "°C"))
        );
        SensorThresholdKey existingThresholdKey =
                new SensorThresholdKey(1L, "TEMPERATURE", "°C");
        SensorThresholdRange existingThresholdRange =
                new SensorThresholdRange(new BigDecimal("10"), new BigDecimal("30"));
        sensorCache.replaceAll(List.of(existingSensor));
        sensorThresholdCache.replaceAll(Map.of(existingThresholdKey, existingThresholdRange));
        when(cultivationSensorReadable.getSnapshot()).thenReturn(
                new DataGeneratorSnapshotResponse(
                        SNAPSHOT_AT,
                        List.of(sensor(
                                2L,
                                "new-device",
                                List.of(new CultivationSensorTypeResponse("HUMIDITY", "%RH"))
                        )),
                        null
                )
        );

        assertThatThrownBy(service::synchronizeAllSensors)
                .isInstanceOf(SensorSynchronizationException.class)
                .hasMessageContaining("thresholds");

        assertThat(sensorCache.getSnapshot()).containsExactly(existingSensor);
        assertThat(sensorCache.findByDeviceEui("new-device")).isEmpty();
        assertThat(sensorThresholdCache.getSnapshot())
                .containsExactly(Map.entry(existingThresholdKey, existingThresholdRange));
    }

    @Test
    @DisplayName("snapshot 응답과 snapshotAt의 null을 거부한다")
    void rejectNullSnapshotAndSnapshotAt() {
        when(cultivationSensorReadable.getSnapshot()).thenReturn(null);

        assertThatThrownBy(service::synchronizeAllSensors)
                .isInstanceOf(SensorSynchronizationException.class)
                .hasMessageContaining("snapshot 응답");

        when(cultivationSensorReadable.getSnapshot()).thenReturn(
                new DataGeneratorSnapshotResponse(null, List.of(), List.of())
        );

        assertThatThrownBy(service::synchronizeAllSensors)
                .isInstanceOf(SensorSynchronizationException.class)
                .hasMessageContaining("snapshotAt");
    }

    @Test
    @DisplayName("snapshot 센서 목록의 null 요소와 null 목록을 거부한다")
    void rejectInvalidSensorCollection() {
        when(cultivationSensorReadable.getSnapshot()).thenReturn(
                new DataGeneratorSnapshotResponse(SNAPSHOT_AT, null, List.of())
        );

        assertThatThrownBy(service::synchronizeAllSensors)
                .isInstanceOf(SensorSynchronizationException.class)
                .hasMessageContaining("sensors");

        when(cultivationSensorReadable.getSnapshot()).thenReturn(
                new DataGeneratorSnapshotResponse(
                        SNAPSHOT_AT,
                        Arrays.asList((DataGeneratorSensorResponse) null),
                        List.of()
                )
        );

        assertThatThrownBy(service::synchronizeAllSensors)
                .isInstanceOf(SensorSynchronizationException.class)
                .hasMessageContaining("null 응답");
    }

    @Test
    @DisplayName("sensorTypes가 null 또는 비어 있으면 동기화를 거부한다")
    void rejectNullOrEmptySensorTypes() {
        when(cultivationSensorReadable.getSnapshot()).thenReturn(
                snapshotWithSensors(List.of(sensor(1L, "device-A", null)))
        );

        assertThatThrownBy(service::synchronizeAllSensors)
                .isInstanceOf(SensorSynchronizationException.class)
                .hasMessageContaining("sensorTypes");

        when(cultivationSensorReadable.getSnapshot()).thenReturn(
                snapshotWithSensors(List.of(sensor(1L, "device-A", List.of())))
        );

        assertThatThrownBy(service::synchronizeAllSensors)
                .isInstanceOf(SensorSynchronizationException.class)
                .hasMessageContaining("sensorTypes");
    }

    @Test
    @DisplayName("sensorTypes의 null 요소와 중복 채널을 거부한다")
    void rejectNullOrDuplicateSensorType() {
        DataGeneratorSensorResponse nullTypeSensor = sensor(
                1L,
                "device-A",
                Arrays.asList((CultivationSensorTypeResponse) null)
        );
        when(cultivationSensorReadable.getSnapshot()).thenReturn(
                snapshotWithSensors(List.of(nullTypeSensor))
        );

        assertThatThrownBy(service::synchronizeAllSensors)
                .isInstanceOf(SensorSynchronizationException.class)
                .hasMessageContaining("sensorTypes에 null");

        CultivationSensorTypeResponse duplicated = new CultivationSensorTypeResponse("TEMPERATURE", "°C");
        when(cultivationSensorReadable.getSnapshot()).thenReturn(
                snapshotWithSensors(List.of(sensor(1L, "device-A", List.of(duplicated, duplicated))))
        );

        assertThatThrownBy(service::synchronizeAllSensors)
                .isInstanceOf(SensorSynchronizationException.class)
                .hasMessageContaining("중복 채널");
    }

    @Test
    @DisplayName("snapshot에 같은 deviceEui가 두 번 있으면 거부한다")
    void rejectDuplicateDeviceEui() {
        CultivationSensorTypeResponse type = new CultivationSensorTypeResponse("TEMPERATURE", "°C");
        when(cultivationSensorReadable.getSnapshot()).thenReturn(
                snapshotWithSensors(List.of(
                        sensor(1L, "device-A", List.of(type)),
                        sensor(1L, "device-A", List.of(type))
                ))
        );

        assertThatThrownBy(service::synchronizeAllSensors)
                .isInstanceOf(SensorSynchronizationException.class)
                .hasMessageContaining("중복된 deviceEui");
    }

    @Test
    @DisplayName("임계값 목록의 null과 null 요소를 거부한다")
    void rejectInvalidThresholdCollection() {
        when(cultivationSensorReadable.getSnapshot()).thenReturn(
                new DataGeneratorSnapshotResponse(SNAPSHOT_AT, List.of(), null)
        );

        assertThatThrownBy(service::synchronizeAllSensors)
                .isInstanceOf(SensorSynchronizationException.class)
                .hasMessageContaining("thresholds");

        when(cultivationSensorReadable.getSnapshot()).thenReturn(
                new DataGeneratorSnapshotResponse(
                        SNAPSHOT_AT,
                        List.of(),
                        Arrays.asList((DataGeneratorThresholdResponse) null)
                )
        );

        assertThatThrownBy(service::synchronizeAllSensors)
                .isInstanceOf(SensorSynchronizationException.class)
                .hasMessageContaining("null 응답");
    }

    @Test
    @DisplayName("snapshot의 중복 임계값 키를 거부한다")
    void rejectDuplicateThresholdKey() {
        DataGeneratorThresholdResponse first = threshold(1L, "TEMPERATURE", "°C", "10", "30");
        DataGeneratorThresholdResponse second = threshold(1L, "TEMPERATURE", "°C", "11", "31");
        when(cultivationSensorReadable.getSnapshot()).thenReturn(
                new DataGeneratorSnapshotResponse(SNAPSHOT_AT, List.of(), List.of(first, second))
        );

        assertThatThrownBy(service::synchronizeAllSensors)
                .isInstanceOf(SensorSynchronizationException.class)
                .hasMessageContaining("중복된 임계값 키");
    }

    @Test
    @DisplayName("동기화 예외는 그대로 전달하고 그 밖의 실행 예외는 원인을 보존해 감싼다")
    void preserveSynchronizationExceptionAndWrapOtherRuntimeException() {
        SensorSynchronizationException synchronizationException =
                new SensorSynchronizationException("의도적인 동기화 실패");
        when(cultivationSensorReadable.getSnapshot()).thenThrow(synchronizationException);

        assertThatThrownBy(service::synchronizeAllSensors)
                .isSameAs(synchronizationException);

        IllegalStateException runtimeException = new IllegalStateException("외부 호출 실패");
        doThrow(runtimeException).when(cultivationSensorReadable).getSnapshot();

        assertThatThrownBy(service::synchronizeAllSensors)
                .isInstanceOf(SensorSynchronizationException.class)
                .hasMessage("Cultivation Service snapshot 동기화에 실패했습니다.")
                .hasCause(runtimeException);
    }

    private static DataGeneratorSnapshotResponse snapshotWithSensors(List<DataGeneratorSensorResponse> sensors) {
        return new DataGeneratorSnapshotResponse(SNAPSHOT_AT, sensors, List.of());
    }

    private static DataGeneratorSensorResponse sensor(
            long cultivationId,
            String deviceEui,
            List<CultivationSensorTypeResponse> sensorTypes
    ) {
        return new DataGeneratorSensorResponse(
                cultivationId,
                deviceEui,
                "device-name",
                "location",
                "location-detail",
                "model",
                sensorTypes
        );
    }

    private static DataGeneratorThresholdResponse threshold(
            long cultivationId,
            String sensorType,
            String unit,
            String minValue,
            String maxValue
    ) {
        return new DataGeneratorThresholdResponse(
                cultivationId,
                sensorType,
                unit,
                new BigDecimal(minValue),
                new BigDecimal(maxValue)
        );
    }
}

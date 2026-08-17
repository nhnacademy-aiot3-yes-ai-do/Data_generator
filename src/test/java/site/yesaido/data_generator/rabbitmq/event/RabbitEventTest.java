package site.yesaido.data_generator.rabbitmq.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.domain.SensorThresholdKey;
import site.yesaido.data_generator.domain.SensorThresholdRange;
import site.yesaido.data_generator.domain.SensorTypeSpec;
import site.yesaido.data_generator.exception.SensorSynchronizationException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RabbitEventTest {

    private static final OffsetDateTime OCCURRED_AT = OffsetDateTime.parse("2026-08-17T06:00:00Z");

    @Test
    @DisplayName("센서 Upsert 이벤트는 문자열을 정규화하고 캐시 항목으로 변환한다")
    void normalizeAndConvertSensorInfoUpsertEvent() {
        SensorInfoUpsertEvent event = new SensorInfoUpsertEvent(
                1L,
                " location ",
                " detail ",
                " model ",
                " device name ",
                " device-A ",
                " TEMPERATURE ",
                " °C ",
                OCCURRED_AT
        );

        assertThat(event.location()).isEqualTo("location");
        assertThat(event.locationDetail()).isEqualTo("detail");
        assertThat(event.deviceModel()).isEqualTo("model");
        assertThat(event.deviceName()).isEqualTo("device name");
        assertThat(event.deviceEui()).isEqualTo("device-A");
        assertThat(event.sensorType()).isEqualTo("TEMPERATURE");
        assertThat(event.unit()).isEqualTo("°C");

        SensorCacheEntry converted = event.convertToSensorCacheEntry();

        assertThat(converted.cultivationId()).isEqualTo(1L);
        assertThat(converted.deviceEui()).isEqualTo("device-A");
        assertThat(converted.sensorTypes())
                .containsExactly(new SensorTypeSpec("TEMPERATURE", "°C"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    @DisplayName("센서 Upsert 이벤트는 유효하지 않은 cultivationId를 거부한다")
    void rejectInvalidUpsertCultivationId(Long cultivationId) {
        assertThatThrownBy(() -> new SensorInfoUpsertEvent(
                cultivationId,
                "location",
                "detail",
                "model",
                "device name",
                "device-A",
                "TEMPERATURE",
                "°C",
                OCCURRED_AT
        )).isInstanceOf(SensorSynchronizationException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("센서 Upsert 이벤트는 null 또는 공백 필수 문자열을 거부한다")
    void rejectInvalidUpsertRequiredText(String location) {
        assertThatThrownBy(() -> new SensorInfoUpsertEvent(
                1L,
                location,
                "detail",
                "model",
                "device name",
                "device-A",
                "TEMPERATURE",
                "°C",
                OCCURRED_AT
        )).isInstanceOf(SensorSynchronizationException.class);
    }

    @Test
    @DisplayName("센서 Upsert 이벤트는 null 발생 시각을 거부한다")
    void rejectNullUpsertOccurredAt() {
        assertThatThrownBy(() -> new SensorInfoUpsertEvent(
                1L,
                "location",
                "detail",
                "model",
                "device name",
                "device-A",
                "TEMPERATURE",
                "°C",
                null
        )).isInstanceOf(SensorSynchronizationException.class);
    }

    @Test
    @DisplayName("센서 Delete 이벤트는 문자열을 정규화하고 채널 키로 변환한다")
    void normalizeAndConvertSensorInfoDeleteEvent() {
        SensorInfoDeleteEvent event = new SensorInfoDeleteEvent(
                1L,
                " device-A ",
                " TEMPERATURE ",
                " °C ",
                OCCURRED_AT
        );

        assertThat(event.deviceEui()).isEqualTo("device-A");
        assertThat(event.sensorType()).isEqualTo("TEMPERATURE");
        assertThat(event.unit()).isEqualTo("°C");
        assertThat(event.convertToSensorChannelKey())
                .isEqualTo(new SensorChannelKey("device-A", "TEMPERATURE", "°C"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    @DisplayName("센서 Delete 이벤트는 유효하지 않은 cultivationId를 거부한다")
    void rejectInvalidDeleteCultivationId(Long cultivationId) {
        assertThatThrownBy(() -> new SensorInfoDeleteEvent(
                cultivationId,
                "device-A",
                "TEMPERATURE",
                "°C",
                OCCURRED_AT
        )).isInstanceOf(SensorSynchronizationException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("센서 Delete 이벤트는 null 또는 공백 필수 문자열을 거부한다")
    void rejectInvalidDeleteRequiredText(String deviceEui) {
        assertThatThrownBy(() -> new SensorInfoDeleteEvent(
                1L,
                deviceEui,
                "TEMPERATURE",
                "°C",
                OCCURRED_AT
        )).isInstanceOf(SensorSynchronizationException.class);
    }

    @Test
    @DisplayName("센서 Delete 이벤트는 null 발생 시각을 거부한다")
    void rejectNullDeleteOccurredAt() {
        assertThatThrownBy(() -> new SensorInfoDeleteEvent(
                1L,
                "device-A",
                "TEMPERATURE",
                "°C",
                null
        )).isInstanceOf(SensorSynchronizationException.class);
    }

    @Test
    @DisplayName("SensorRange는 문자열을 정규화하고 임계값 키와 범위로 변환한다")
    void normalizeAndConvertSensorRange() {
        SensorRange range = new SensorRange(
                " TEMPERATURE ",
                " °C ",
                new BigDecimal("20"),
                new BigDecimal("20")
        );

        assertThat(range.sensorType()).isEqualTo("TEMPERATURE");
        assertThat(range.unit()).isEqualTo("°C");
        assertThat(range.convertToSensorThresholdKey(1L))
                .isEqualTo(new SensorThresholdKey(1L, "TEMPERATURE", "°C"));
        assertThat(range.convertToSensorThresholdRange())
                .isEqualTo(new SensorThresholdRange(new BigDecimal("20"), new BigDecimal("20")));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("SensorRange는 null 또는 공백 센서 타입을 거부한다")
    void rejectInvalidSensorRangeText(String sensorType) {
        assertThatThrownBy(() -> new SensorRange(
                sensorType,
                "°C",
                BigDecimal.ZERO,
                BigDecimal.ONE
        )).isInstanceOf(SensorSynchronizationException.class);
    }

    @Test
    @DisplayName("SensorRange는 null 범위와 역전된 범위를 거부한다")
    void rejectInvalidSensorRangeBounds() {
        assertThatThrownBy(() -> new SensorRange("TEMPERATURE", "°C", null, BigDecimal.ONE))
                .isInstanceOf(SensorSynchronizationException.class);
        assertThatThrownBy(() -> new SensorRange("TEMPERATURE", "°C", BigDecimal.ZERO, null))
                .isInstanceOf(SensorSynchronizationException.class);
        assertThatThrownBy(() -> new SensorRange(
                "TEMPERATURE",
                "°C",
                BigDecimal.TEN,
                BigDecimal.ONE
        )).isInstanceOf(SensorSynchronizationException.class);
    }

    @Test
    @DisplayName("Threshold 이벤트는 입력 목록을 방어 복사하고 빈 목록도 허용한다")
    void defensivelyCopyThresholdRangesAndAllowEmptyList() {
        SensorRange range = new SensorRange("TEMPERATURE", "°C", BigDecimal.ZERO, BigDecimal.TEN);
        List<SensorRange> mutableRanges = new ArrayList<>(List.of(range));
        ThresholdInfoEvent event = new ThresholdInfoEvent(1L, mutableRanges, OCCURRED_AT);

        mutableRanges.clear();

        assertThat(event.sensorRangeList()).containsExactly(range);
        assertThatThrownBy(() -> event.sensorRangeList().add(range))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(new ThresholdInfoEvent(1L, List.of(), OCCURRED_AT).sensorRangeList()).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    @DisplayName("Threshold 이벤트는 유효하지 않은 cultivationId를 거부한다")
    void rejectInvalidThresholdCultivationId(Long cultivationId) {
        assertThatThrownBy(() -> new ThresholdInfoEvent(cultivationId, List.of(), OCCURRED_AT))
                .isInstanceOf(SensorSynchronizationException.class);
    }

    @Test
    @DisplayName("Threshold 이벤트는 null 목록, null 요소와 null 발생 시각을 거부한다")
    void rejectInvalidThresholdEventMembers() {
        assertThatThrownBy(() -> new ThresholdInfoEvent(1L, null, OCCURRED_AT))
                .isInstanceOf(SensorSynchronizationException.class);
        assertThatThrownBy(() -> new ThresholdInfoEvent(
                1L,
                Arrays.asList((SensorRange) null),
                OCCURRED_AT
        )).isInstanceOf(SensorSynchronizationException.class);
        assertThatThrownBy(() -> new ThresholdInfoEvent(1L, List.of(), null))
                .isInstanceOf(SensorSynchronizationException.class);
    }
}

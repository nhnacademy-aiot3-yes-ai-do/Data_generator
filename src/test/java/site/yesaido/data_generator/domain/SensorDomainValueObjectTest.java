package site.yesaido.data_generator.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import site.yesaido.data_generator.exception.SensorCacheException;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensorDomainValueObjectTest {

    private static final SensorTypeSpec TEMPERATURE =
            new SensorTypeSpec("TEMPERATURE", "°C");

    @Test
    @DisplayName("센서 캐시 항목의 문자열을 정규화하고 센서 타입을 불변 복사한다")
    void normalizeAndDefensivelyCopySensorCacheEntry() {
        Set<SensorTypeSpec> mutableSensorTypes = new HashSet<>();
        mutableSensorTypes.add(TEMPERATURE);

        SensorCacheEntry entry = new SensorCacheEntry(
                1L,
                "  device-A  ",
                "  TEST-DEVICE  ",
                "  mushroom-house  ",
                "  center  ",
                "  TEST123  ",
                mutableSensorTypes
        );

        mutableSensorTypes.add(new SensorTypeSpec("HUMIDITY", "%RH"));
        Set<SensorTypeSpec> immutableSensorTypes = entry.sensorTypes();
        SensorTypeSpec co2 = new SensorTypeSpec("CO2", "ppm");

        assertThat(entry.deviceEui()).isEqualTo("device-A");
        assertThat(entry.deviceName()).isEqualTo("TEST-DEVICE");
        assertThat(entry.location()).isEqualTo("mushroom-house");
        assertThat(entry.locationDetail()).isEqualTo("center");
        assertThat(entry.deviceModel()).isEqualTo("TEST123");
        assertThat(immutableSensorTypes).containsExactly(TEMPERATURE);
        assertThatThrownBy(() -> immutableSensorTypes.add(co2))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("센서 캐시 항목의 재배 ID는 양수여야 한다")
    void rejectNonPositiveSensorCacheCultivationId(long cultivationId) {
        Set<SensorTypeSpec> sensorTypes = Set.of(TEMPERATURE);

        assertThatThrownBy(() -> createSensorCacheEntry(
                cultivationId, "device-A", "TEST-DEVICE", "house",
                "center", "TEST123", sensorTypes))
                .isInstanceOf(SensorCacheException.class)
                .hasMessageContaining("cultivationId");
    }

    @ParameterizedTest(name = "{0}={1}")
    @MethodSource("missingSensorCacheTexts")
    @DisplayName("센서 캐시 항목의 필수 문자열이 null 또는 공백이면 예외가 발생한다")
    void rejectMissingSensorCacheText(String fieldName, String invalidValue) {
        assertThatThrownBy(() -> createSensorCacheEntryWithInvalidText(
                fieldName, invalidValue))
                .isInstanceOf(SensorCacheException.class)
                .hasMessageContaining(fieldName);
    }

    private static Stream<Arguments> missingSensorCacheTexts() {
        return Stream.of("deviceEui", "deviceName", "location", "locationDetail", "deviceModel")
                .flatMap(fieldName -> Stream.of(
                        Arguments.of(fieldName, null),
                        Arguments.of(fieldName, "   ")
                ));
    }

    @Test
    @DisplayName("센서 캐시 항목에는 한 개 이상의 null이 아닌 센서 타입이 필요하다")
    void rejectInvalidSensorTypesInSensorCacheEntry() {
        Set<SensorTypeSpec> emptySensorTypes = Set.of();

        assertThatThrownBy(() -> createSensorCacheEntry(
                1L, "device-A", "TEST-DEVICE", "house", "center", "TEST123", null))
                .isInstanceOf(SensorCacheException.class);

        assertThatThrownBy(() -> createSensorCacheEntry(
                1L, "device-A", "TEST-DEVICE", "house", "center", "TEST123", emptySensorTypes))
                .isInstanceOf(SensorCacheException.class);

        Set<SensorTypeSpec> sensorTypesContainingNull = new HashSet<>();
        sensorTypesContainingNull.add(null);

        assertThatThrownBy(() -> createSensorCacheEntry(
                1L, "device-A", "TEST-DEVICE", "house", "center", "TEST123",
                sensorTypesContainingNull))
                .isInstanceOf(SensorCacheException.class)
                .hasMessageContaining("null");
    }

    @Test
    @DisplayName("센서 채널 키와 타입 명세의 문자열을 정규화한다")
    void normalizeSensorChannelAndTypeSpec() {
        SensorChannelKey channelKey = new SensorChannelKey(
                "  device-A  ", "  TEMPERATURE  ", "  °C  ");
        SensorTypeSpec sensorTypeSpec = new SensorTypeSpec(
                "  TEMPERATURE  ", "  °C  ");

        assertThat(channelKey).isEqualTo(
                new SensorChannelKey("device-A", "TEMPERATURE", "°C"));
        assertThat(sensorTypeSpec).isEqualTo(TEMPERATURE);
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidSensorTextValueObjects")
    @DisplayName("센서 채널과 타입 명세의 필수 문자열이 없으면 예외가 발생한다")
    void rejectMissingSensorValueObjectText(String objectType, String invalidValue) {
        ThrowingCallable valueObjectCreation = switch (objectType) {
            case "channel-device" -> () -> new SensorChannelKey(
                    invalidValue, "TEMPERATURE", "°C");
            case "channel-type" -> () -> new SensorChannelKey(
                    "device-A", invalidValue, "°C");
            case "channel-unit" -> () -> new SensorChannelKey(
                    "device-A", "TEMPERATURE", invalidValue);
            case "spec-type" -> () -> new SensorTypeSpec(invalidValue, "°C");
            case "spec-unit" -> () -> new SensorTypeSpec("TEMPERATURE", invalidValue);
            default -> throw new AssertionError("unexpected objectType=" + objectType);
        };

        assertThatThrownBy(valueObjectCreation)
                .isInstanceOf(SensorDataGenerationException.class);
    }

    private static Stream<Arguments> invalidSensorTextValueObjects() {
        return Stream.of("channel-device", "channel-type", "channel-unit",
                        "spec-type", "spec-unit")
                .flatMap(objectType -> Stream.of(
                        Arguments.of(objectType, null),
                        Arguments.of(objectType, "   ")
                ));
    }

    @Test
    @DisplayName("센서 임계값 키의 문자열을 정규화한다")
    void normalizeSensorThresholdKey() {
        SensorThresholdKey thresholdKey = new SensorThresholdKey(
                1L, "  TEMPERATURE  ", "  °C  ");

        assertThat(thresholdKey).isEqualTo(
                new SensorThresholdKey(1L, "TEMPERATURE", "°C"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("센서 임계값 키의 재배 ID는 양수여야 한다")
    void rejectNonPositiveThresholdCultivationId(long cultivationId) {
        assertThatThrownBy(() -> new SensorThresholdKey(
                cultivationId, "TEMPERATURE", "°C"))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("cultivationId");
    }

    @ParameterizedTest(name = "{0}={1}")
    @MethodSource("missingThresholdKeyTexts")
    @DisplayName("센서 임계값 키의 타입과 단위는 필수이다")
    void rejectMissingThresholdKeyText(String fieldName, String invalidValue) {
        ThrowingCallable thresholdKeyCreation = fieldName.equals("sensorType")
                ? () -> new SensorThresholdKey(1L, invalidValue, "°C")
                : () -> new SensorThresholdKey(1L, "TEMPERATURE", invalidValue);

        assertThatThrownBy(thresholdKeyCreation)
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining(fieldName);
    }

    private static Stream<Arguments> missingThresholdKeyTexts() {
        return Stream.of("sensorType", "unit")
                .flatMap(fieldName -> Stream.of(
                        Arguments.of(fieldName, null),
                        Arguments.of(fieldName, "   ")
                ));
    }

    @Test
    @DisplayName("센서 임계값 범위를 정규화하고 폭과 중간값을 계산한다")
    void normalizeAndCalculateSensorThresholdRange() {
        SensorThresholdRange range = new SensorThresholdRange(
                new BigDecimal("10.00"), new BigDecimal("20.000"));

        assertThat(range.thresholdMin()).isEqualTo(new BigDecimal("1E+1"));
        assertThat(range.thresholdMax()).isEqualTo(new BigDecimal("2E+1"));
        assertThat(range.rangeWidth()).isEqualTo(new BigDecimal("1E+1"));
        assertThat(range.midpoint()).isEqualTo(new BigDecimal("15"));
    }

    @Test
    @DisplayName("센서 임계값 범위는 같은 최솟값과 최댓값을 허용한다")
    void allowEqualSensorThresholdBounds() {
        SensorThresholdRange range = new SensorThresholdRange(
                new BigDecimal("15"), new BigDecimal("15"));

        assertThat(range.rangeWidth()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(range.midpoint()).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("센서 임계값 범위의 null과 역전된 범위를 거절한다")
    void rejectInvalidSensorThresholdRange() {
        assertThatThrownBy(() -> new SensorThresholdRange(null, BigDecimal.ONE))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("thresholdMin");

        assertThatThrownBy(() -> new SensorThresholdRange(BigDecimal.ZERO, null))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("thresholdMax");

        assertThatThrownBy(() -> new SensorThresholdRange(
                BigDecimal.TEN, BigDecimal.ONE))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("클 수 없습니다");
    }

    private static SensorCacheEntry createSensorCacheEntryWithInvalidText(
            String fieldName,
            String invalidValue
    ) {
        return createSensorCacheEntry(
                1L,
                fieldName.equals("deviceEui") ? invalidValue : "device-A",
                fieldName.equals("deviceName") ? invalidValue : "TEST-DEVICE",
                fieldName.equals("location") ? invalidValue : "house",
                fieldName.equals("locationDetail") ? invalidValue : "center",
                fieldName.equals("deviceModel") ? invalidValue : "TEST123",
                Set.of(TEMPERATURE)
        );
    }

    private static SensorCacheEntry createSensorCacheEntry(
            long cultivationId,
            String deviceEui,
            String deviceName,
            String location,
            String locationDetail,
            String deviceModel,
            Set<SensorTypeSpec> sensorTypes
    ) {
        return new SensorCacheEntry(
                cultivationId, deviceEui, deviceName, location,
                locationDetail, deviceModel, sensorTypes);
    }
}

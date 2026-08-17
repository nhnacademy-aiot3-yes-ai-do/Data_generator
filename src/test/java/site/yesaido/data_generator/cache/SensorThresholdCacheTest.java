package site.yesaido.data_generator.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.data_generator.domain.SensorThresholdKey;
import site.yesaido.data_generator.domain.SensorThresholdRange;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensorThresholdCacheTest {

    private SensorThresholdCache sensorThresholdCache;

    @BeforeEach
    void setUp() {
        sensorThresholdCache = new SensorThresholdCache();
    }

    @Test
    @DisplayName("새 캐시는 비어 있고 초기 동기화가 완료되지 않은 상태다")
    void initializeEmptyCacheBeforeSynchronization() {
        assertThat(sensorThresholdCache.isInitialSynchronizationCompleted()).isFalse();
        assertThat(sensorThresholdCache.getThresholdCount()).isZero();
        assertThat(sensorThresholdCache.getSnapshot()).isEmpty();
    }

    @Test
    @DisplayName("임계값을 Upsert한 뒤 같은 키로 조회할 수 있다")
    void upsertAndFindThresholdRange() {
        SensorThresholdKey thresholdKey = createThresholdKey(1L, "SOIL_MOISTURE", "%");
        SensorThresholdRange thresholdRange = createThresholdRange("30", "70");

        sensorThresholdCache.upsert(thresholdKey, thresholdRange);

        assertThat(sensorThresholdCache.find(thresholdKey)).contains(thresholdRange);
        assertThat(sensorThresholdCache.getThresholdCount()).isEqualTo(1);

        Map<SensorThresholdKey, SensorThresholdRange> snapshot = sensorThresholdCache.getSnapshot();

        SensorThresholdKey anotherThresholdKey = createThresholdKey(2L, "SOIL_MOISTURE", "%");

        assertThatThrownBy(() -> snapshot.put(anotherThresholdKey, thresholdRange))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("같은 키를 새로운 임계값으로 Upsert하면 기존 값을 교체한다")
    void replaceThresholdRangeWhenSameKeyIsUpserted() {
        SensorThresholdKey thresholdKey = createThresholdKey(1L, "SOIL_MOISTURE", "%");
        SensorThresholdRange initialThresholdRange = createThresholdRange("30", "70");
        SensorThresholdRange updatedThresholdRange = createThresholdRange("35", "65");

        sensorThresholdCache.upsert(thresholdKey, initialThresholdRange);
        sensorThresholdCache.upsert(thresholdKey, updatedThresholdRange);

        assertThat(sensorThresholdCache.find(thresholdKey)).contains(updatedThresholdRange);
        assertThat(sensorThresholdCache.getThresholdCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 키와 값을 다시 Upsert하면 기존 불변 Map을 재사용한다")
    void reuseCurrentMapWhenSameEntryIsUpserted() {
        SensorThresholdKey thresholdKey = createThresholdKey(1L, "SOIL_MOISTURE", "%");
        SensorThresholdRange thresholdRange = createThresholdRange("30", "70");

        sensorThresholdCache.upsert(thresholdKey, thresholdRange);
        Map<SensorThresholdKey, SensorThresholdRange> beforeSnapshot = sensorThresholdCache.getSnapshot();

        SensorThresholdRange equalThresholdRange = createThresholdRange("30.0", "70.00");

        sensorThresholdCache.upsert(thresholdKey, equalThresholdRange);

        Map<SensorThresholdKey, SensorThresholdRange> afterSnapshot = sensorThresholdCache.getSnapshot();

        assertThat(afterSnapshot).isSameAs(beforeSnapshot);
    }

    @Test
    @DisplayName("정확한 임계값 키 하나만 멱등하게 삭제한다")
    void removeOnlyExactThresholdKeyIdempotently() {
        SensorThresholdKey celsiusKey = createThresholdKey(1L, "TEMPERATURE", "°C");
        SensorThresholdKey fahrenheitKey = createThresholdKey(1L, "TEMPERATURE", "°F");
        SensorThresholdKey anotherCultivationKey = createThresholdKey(2L, "TEMPERATURE", "°C");

        SensorThresholdRange thresholdRange = createThresholdRange("10", "20");

        sensorThresholdCache.upsert(celsiusKey, thresholdRange);
        sensorThresholdCache.upsert(fahrenheitKey, thresholdRange);
        sensorThresholdCache.upsert(anotherCultivationKey, thresholdRange);

        sensorThresholdCache.remove(celsiusKey);

        assertThat(sensorThresholdCache.find(celsiusKey)).isEmpty();
        assertThat(sensorThresholdCache.find(fahrenheitKey)).contains(thresholdRange);
        assertThat(sensorThresholdCache.find(anotherCultivationKey)).contains(thresholdRange);
        assertThat(sensorThresholdCache.getThresholdCount()).isEqualTo(2);

        sensorThresholdCache.remove(celsiusKey);

        assertThat(sensorThresholdCache.getThresholdCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("전체 임계값을 방어적으로 복사하고 초기 동기화를 완료한다")
    void replaceAllWithDefensiveCopy() {
        SensorThresholdKey soilMoistureKey = createThresholdKey(1L, "SOIL_MOISTURE", "%");
        SensorThresholdKey acidityKey = createThresholdKey(1L, "SOIL_ACIDITY", "pH");

        SensorThresholdRange soilMoistureRange = createThresholdRange("30", "70");
        SensorThresholdRange acidityRange = createThresholdRange("5", "7");

        Map<SensorThresholdKey, SensorThresholdRange> thresholdEntries = new HashMap<>();

        thresholdEntries.put(soilMoistureKey, soilMoistureRange);
        thresholdEntries.put(acidityKey, acidityRange);

        sensorThresholdCache.replaceAll(thresholdEntries);

        assertThat(sensorThresholdCache.isInitialSynchronizationCompleted()).isTrue();
        assertThat(sensorThresholdCache.getThresholdCount()).isEqualTo(2);
        assertThat(sensorThresholdCache.find(soilMoistureKey)).contains(soilMoistureRange);
        assertThat(sensorThresholdCache.find(acidityKey)).contains(acidityRange);

        thresholdEntries.clear();

        assertThat(sensorThresholdCache.getThresholdCount()).isEqualTo(2);
        assertThat(sensorThresholdCache.find(soilMoistureKey)).contains(soilMoistureRange);
        assertThat(sensorThresholdCache.find(acidityKey)).contains(acidityRange);
    }

    @Test
    @DisplayName("전체 교체 입력에 null 값이 있으면 캐시를 부분 적용하지 않는다")
    void doNotPartiallyReplaceWhenEntryContainsNullValue() {
        SensorThresholdKey validKey = createThresholdKey(1L, "SOIL_MOISTURE", "%");
        SensorThresholdKey invalidKey = createThresholdKey(1L, "SOIL_ACIDITY", "pH");
        SensorThresholdRange validRange = createThresholdRange("30", "70");

        Map<SensorThresholdKey, SensorThresholdRange> invalidThresholdEntries = new HashMap<>();

        invalidThresholdEntries.put(validKey, validRange);
        invalidThresholdEntries.put(invalidKey, null);

        assertThatThrownBy(() -> sensorThresholdCache.replaceAll(invalidThresholdEntries))
                .isInstanceOf(SensorDataGenerationException.class);

        assertThat(sensorThresholdCache.isInitialSynchronizationCompleted()).isFalse();
        assertThat(sensorThresholdCache.getThresholdCount()).isZero();
        assertThat(sensorThresholdCache.getSnapshot()).isEmpty();
    }

    @Test
    @DisplayName("캐시 작업의 null 입력을 거부한다")
    void rejectNullInputs() {
        SensorThresholdKey thresholdKey = createThresholdKey(1L, "SOIL_MOISTURE", "%");
        SensorThresholdRange thresholdRange = createThresholdRange("30", "70");

        assertThatThrownBy(() -> sensorThresholdCache.upsert(null, thresholdRange))
                .isInstanceOf(SensorDataGenerationException.class);

        assertThatThrownBy(() -> sensorThresholdCache.upsert(thresholdKey, null))
                .isInstanceOf(SensorDataGenerationException.class);

        assertThatThrownBy(() -> sensorThresholdCache.remove(null))
                .isInstanceOf(SensorDataGenerationException.class);

        assertThatThrownBy(() -> sensorThresholdCache.find(null))
                .isInstanceOf(SensorDataGenerationException.class);

        assertThatThrownBy(() -> sensorThresholdCache.replaceAll(null))
                .isInstanceOf(SensorDataGenerationException.class);
    }

    private static SensorThresholdKey createThresholdKey(long cultivationId, String sensorType, String unit) {
        return new SensorThresholdKey(cultivationId, sensorType, unit);
    }

    private static SensorThresholdRange createThresholdRange(String thresholdMin, String thresholdMax) {
        return new SensorThresholdRange(new BigDecimal(thresholdMin), new BigDecimal(thresholdMax));
    }
}
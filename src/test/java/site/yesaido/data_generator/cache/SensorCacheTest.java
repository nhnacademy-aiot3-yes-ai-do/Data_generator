package site.yesaido.data_generator.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.domain.SensorTypeSpec;
import site.yesaido.data_generator.exception.SensorCacheException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensorCacheTest {

    private static final SensorTypeSpec TEMPERATURE = new SensorTypeSpec("TEMPERATURE", "°C");
    private static final SensorTypeSpec TEMPERATURE_FAHRENHEIT = new SensorTypeSpec("TEMPERATURE", "°F");
    private static final SensorTypeSpec HUMIDITY = new SensorTypeSpec("HUMIDITY", "%");
    private static final SensorTypeSpec CO2 = new SensorTypeSpec("CO2", "ppm");

    private SensorCache sensorCache;

    @BeforeEach
    void setUp() {
        sensorCache = new SensorCache();
    }

    @Test
    @DisplayName("새 캐시는 비어 있고 초기 동기화가 완료되지 않은 상태다")
    void initializeEmptyCacheBeforeSynchronization() {
        assertThat(sensorCache.isInitialSynchronizationCompleted()).isFalse();
        assertThat(sensorCache.getSensorCount()).isZero();
        assertThat(sensorCache.getSnapshot()).isEmpty();
        assertThat(sensorCache.findByDeviceEui("unknown-device")).isEmpty();
    }

    @Test
    @DisplayName("새 센서 장치를 Upsert하면 캐시에 추가한다")
    void addNewSensorEntry() {
        SensorCacheEntry sensorEntry = createSensorEntry(1L, "device-1", "sensor-1", TEMPERATURE);

        sensorCache.upsert(sensorEntry);

        assertThat(sensorCache.findByDeviceEui("device-1")).contains(sensorEntry);
        assertThat(sensorCache.getSensorCount()).isEqualTo(1);
        assertThat(sensorCache.isInitialSynchronizationCompleted()).isFalse();
    }

    @Test
    @DisplayName("동일한 센서 정보를 다시 Upsert하면 기존 엔트리를 재사용한다")
    void reuseCurrentEntryWhenEqualSensorEntryIsUpserted() {
        SensorCacheEntry initialEntry = createSensorEntry(1L, "device-1", "sensor-1", TEMPERATURE);

        sensorCache.upsert(initialEntry);
        SensorCacheEntry beforeUpsert = sensorCache.findByDeviceEui("device-1").orElseThrow();

        SensorCacheEntry equalEntry = createSensorEntry(1L, "device-1", "sensor-1", TEMPERATURE);
        sensorCache.upsert(equalEntry);

        SensorCacheEntry afterUpsert = sensorCache.findByDeviceEui("device-1").orElseThrow();

        assertThat(afterUpsert).isSameAs(beforeUpsert);
        assertThat(sensorCache.getSensorCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("기존 장치를 Upsert하면 최신 메타데이터를 쓰고 센서 채널을 병합한다")
    void mergeChannelsAndUseLatestMetadataWhenExistingEntryIsUpserted() {
        SensorCacheEntry initialEntry = createSensorEntry(
                1L,
                "device-1",
                "old-name",
                "old-location",
                "old-detail",
                "old-model",
                TEMPERATURE
        );
        SensorCacheEntry updatedEntry = createSensorEntry(
                1L,
                "device-1",
                "new-name",
                "new-location",
                "new-detail",
                "new-model",
                HUMIDITY
        );

        sensorCache.upsert(initialEntry);
        sensorCache.upsert(updatedEntry);

        SensorCacheEntry mergedEntry = sensorCache.findByDeviceEui("device-1").orElseThrow();

        assertThat(mergedEntry.cultivationId()).isEqualTo(1L);
        assertThat(mergedEntry.deviceName()).isEqualTo("new-name");
        assertThat(mergedEntry.location()).isEqualTo("new-location");
        assertThat(mergedEntry.locationDetail()).isEqualTo("new-detail");
        assertThat(mergedEntry.deviceModel()).isEqualTo("new-model");
        assertThat(mergedEntry.sensorTypes()).containsExactlyInAnyOrder(TEMPERATURE, HUMIDITY);
        assertThat(sensorCache.getSensorCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Upsert는 null과 다른 재배에 속한 동일 장치를 거부한다")
    void rejectInvalidUpsertInputs() {
        SensorCacheEntry existingEntry = createSensorEntry(1L, "device-1", "sensor-1", TEMPERATURE);
        SensorCacheEntry anotherCultivationEntry = createSensorEntry(2L, "device-1", "sensor-2", HUMIDITY);

        sensorCache.upsert(existingEntry);

        assertThatThrownBy(() -> sensorCache.upsert(null))
                .isInstanceOf(SensorCacheException.class)
                .hasMessage("sensorCacheEntry는 null일 수 없습니다.");

        assertThatThrownBy(() -> sensorCache.upsert(anotherCultivationEntry))
                .isInstanceOf(SensorCacheException.class)
                .hasMessageContaining("같은 deviceEui를 다른 cultivation으로 변경할 수 없습니다.")
                .hasMessageContaining("currentCultivationId=1")
                .hasMessageContaining("requestedCultivationId=2");

        assertThat(sensorCache.findByDeviceEui("device-1")).contains(existingEntry);
        assertThat(sensorCache.getSensorCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("채널 삭제는 null과 없는 장치를 안전하게 처리한다")
    void rejectNullChannelAndIgnoreMissingDevice() {
        assertThatThrownBy(() -> sensorCache.removeChannel(null))
                .isInstanceOf(SensorCacheException.class)
                .hasMessage("sensorChannelKey는 null일 수 없습니다.");

        sensorCache.removeChannel(new SensorChannelKey("missing-device", "TEMPERATURE", "°C"));

        assertThat(sensorCache.getSensorCount()).isZero();
    }

    @Test
    @DisplayName("센서 타입이나 단위가 다르면 채널을 삭제하지 않는다")
    void ignoreChannelRemovalWhenTypeOrUnitDoesNotMatch() {
        SensorCacheEntry sensorEntry = createSensorEntry(
                1L,
                "device-1",
                "sensor-1",
                TEMPERATURE,
                TEMPERATURE_FAHRENHEIT,
                HUMIDITY
        );
        sensorCache.upsert(sensorEntry);

        sensorCache.removeChannel(new SensorChannelKey("device-1", "PRESSURE", "hPa"));
        sensorCache.removeChannel(new SensorChannelKey("device-1", "TEMPERATURE", "K"));

        SensorCacheEntry unchangedEntry = sensorCache.findByDeviceEui("device-1").orElseThrow();

        assertThat(unchangedEntry).isSameAs(sensorEntry);
        assertThat(unchangedEntry.sensorTypes())
                .containsExactlyInAnyOrder(TEMPERATURE, TEMPERATURE_FAHRENHEIT, HUMIDITY);
    }

    @Test
    @DisplayName("정확한 채널 하나만 삭제하고 마지막 채널이면 장치도 삭제한다")
    void removeOnlyExactChannelAndThenRemoveDeviceWithLastChannel() {
        SensorCacheEntry multipleChannelEntry = createSensorEntry(
                1L,
                "device-1",
                "sensor-1",
                TEMPERATURE,
                HUMIDITY
        );
        SensorCacheEntry singleChannelEntry = createSensorEntry(1L, "device-2", "sensor-2", CO2);
        sensorCache.upsert(multipleChannelEntry);
        sensorCache.upsert(singleChannelEntry);

        sensorCache.removeChannel(new SensorChannelKey("device-1", "TEMPERATURE", "°C"));

        assertThat(sensorCache.findByDeviceEui("device-1"))
                .get()
                .extracting(SensorCacheEntry::sensorTypes)
                .isEqualTo(Set.of(HUMIDITY));
        assertThat(sensorCache.getSensorCount()).isEqualTo(2);

        sensorCache.removeChannel(new SensorChannelKey("device-2", "CO2", "ppm"));

        assertThat(sensorCache.findByDeviceEui("device-2")).isEmpty();
        assertThat(sensorCache.findByDeviceEui("device-1")).isPresent();
        assertThat(sensorCache.getSensorCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("장치 EUI로 삭제하며 공백을 제거하고 없는 장치 삭제는 멱등하다")
    void removeByNormalizedDeviceEuiIdempotently() {
        SensorCacheEntry sensorEntry = createSensorEntry(1L, "device-1", "sensor-1", TEMPERATURE);
        sensorCache.upsert(sensorEntry);

        sensorCache.removeByDeviceEui("  device-1  ");

        assertThat(sensorCache.findByDeviceEui("device-1")).isEmpty();
        assertThat(sensorCache.getSensorCount()).isZero();

        sensorCache.removeByDeviceEui("device-1");

        assertThat(sensorCache.getSensorCount()).isZero();
    }

    @Test
    @DisplayName("장치 EUI로 삭제할 때 null과 공백을 거부한다")
    void rejectNullOrBlankDeviceEuiRemoval() {
        assertThatThrownBy(() -> sensorCache.removeByDeviceEui(null))
                .isInstanceOf(SensorCacheException.class)
                .hasMessage("deviceEui는 null이거나 공백일 수 없습니다.");

        assertThatThrownBy(() -> sensorCache.removeByDeviceEui("   "))
                .isInstanceOf(SensorCacheException.class)
                .hasMessage("deviceEui는 null이거나 공백일 수 없습니다.");
    }

    @Test
    @DisplayName("재배 ID는 0보다 커야 한다")
    void rejectInvalidCultivationIdRemoval() {
        assertThatThrownBy(() -> sensorCache.removeByCultivationId(0L))
                .isInstanceOf(SensorCacheException.class)
                .hasMessage("cultivationId는 0보다 커야 합니다.");

        assertThatThrownBy(() -> sensorCache.removeByCultivationId(-1L))
                .isInstanceOf(SensorCacheException.class)
                .hasMessage("cultivationId는 0보다 커야 합니다.");
    }

    @Test
    @DisplayName("해당 재배의 장치가 없으면 빈 목록을 반환하고 캐시를 유지한다")
    void keepCacheWhenCultivationIdDoesNotMatch() {
        SensorCacheEntry sensorEntry = createSensorEntry(1L, "device-1", "sensor-1", TEMPERATURE);
        sensorCache.upsert(sensorEntry);

        List<SensorCacheEntry> removedEntries = sensorCache.removeByCultivationId(2L);

        assertThat(removedEntries).isEmpty();
        assertThat(sensorCache.findByDeviceEui("device-1")).contains(sensorEntry);
        assertThat(sensorCache.getSensorCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("재배 ID와 일치하는 모든 장치를 반환하고 삭제하며 다른 재배는 유지한다")
    void removeAndReturnAllEntriesForCultivationId() {
        SensorCacheEntry firstEntry = createSensorEntry(1L, "device-1", "sensor-1", TEMPERATURE);
        SensorCacheEntry secondEntry = createSensorEntry(1L, "device-2", "sensor-2", HUMIDITY);
        SensorCacheEntry remainingEntry = createSensorEntry(2L, "device-3", "sensor-3", CO2);
        sensorCache.upsert(firstEntry);
        sensorCache.upsert(secondEntry);
        sensorCache.upsert(remainingEntry);

        List<SensorCacheEntry> removedEntries = sensorCache.removeByCultivationId(1L);

        assertThat(removedEntries).containsExactlyInAnyOrder(firstEntry, secondEntry);
        assertThatThrownBy(removedEntries::clear)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(sensorCache.getSnapshot()).containsExactly(remainingEntry);
        assertThat(sensorCache.getSensorCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("전체 교체는 null 목록과 null 엔트리를 거부하고 기존 캐시를 유지한다")
    void rejectNullReplacementInputsWithoutPartialApplication() {
        SensorCacheEntry existingEntry = createSensorEntry(1L, "existing-device", "existing-sensor", TEMPERATURE);
        SensorCacheEntry candidateEntry = createSensorEntry(1L, "candidate-device", "candidate-sensor", HUMIDITY);
        sensorCache.upsert(existingEntry);

        assertThatThrownBy(() -> sensorCache.replaceAll(null))
                .isInstanceOf(SensorCacheException.class)
                .hasMessage("sensorCacheEntries는 null일 수 없습니다.");

        List<SensorCacheEntry> entriesWithNull = new ArrayList<>();
        entriesWithNull.add(candidateEntry);
        entriesWithNull.add(null);

        assertThatThrownBy(() -> sensorCache.replaceAll(entriesWithNull))
                .isInstanceOf(SensorCacheException.class)
                .hasMessage("sensorCacheEntries에 null이 포함될 수 없습니다.");

        assertThat(sensorCache.isInitialSynchronizationCompleted()).isFalse();
        assertThat(sensorCache.getSnapshot()).containsExactly(existingEntry);
    }

    @Test
    @DisplayName("전체 교체는 중복된 deviceEui를 거부하고 기존 캐시를 유지한다")
    void rejectDuplicateDeviceEuiWithoutReplacingCache() {
        SensorCacheEntry existingEntry = createSensorEntry(1L, "existing-device", "existing-sensor", TEMPERATURE);
        SensorCacheEntry firstDuplicate = createSensorEntry(1L, "duplicate-device", "sensor-1", TEMPERATURE);
        SensorCacheEntry secondDuplicate = createSensorEntry(2L, " duplicate-device ", "sensor-2", HUMIDITY);
        sensorCache.upsert(existingEntry);

        assertThatThrownBy(() -> sensorCache.replaceAll(List.of(firstDuplicate, secondDuplicate)))
                .isInstanceOf(SensorCacheException.class)
                .hasMessage("중복된 deviceEui입니다: duplicate-device");

        assertThat(sensorCache.isInitialSynchronizationCompleted()).isFalse();
        assertThat(sensorCache.getSnapshot()).containsExactly(existingEntry);
    }

    @Test
    @DisplayName("전체 교체는 입력 목록을 방어적으로 복사하고 초기 동기화를 완료한다")
    void replaceAllWithDefensiveCopyAndCompleteSynchronization() {
        SensorCacheEntry firstEntry = createSensorEntry(1L, "device-1", "sensor-1", TEMPERATURE);
        SensorCacheEntry secondEntry = createSensorEntry(2L, "device-2", "sensor-2", HUMIDITY);
        List<SensorCacheEntry> replacementEntries = new ArrayList<>(List.of(firstEntry, secondEntry));

        sensorCache.replaceAll(replacementEntries);

        assertThat(sensorCache.isInitialSynchronizationCompleted()).isTrue();
        assertThat(sensorCache.getSensorCount()).isEqualTo(2);
        assertThat(sensorCache.findByDeviceEui("  device-1  ")).contains(firstEntry);
        assertThat(sensorCache.findByDeviceEui("missing-device")).isEmpty();
        assertThat(sensorCache.getSnapshot()).containsExactlyInAnyOrder(firstEntry, secondEntry);

        replacementEntries.clear();

        assertThat(sensorCache.getSensorCount()).isEqualTo(2);
        assertThat(sensorCache.getSnapshot()).containsExactlyInAnyOrder(firstEntry, secondEntry);
    }

    @Test
    @DisplayName("조회 스냅샷은 수정할 수 없는 목록이다")
    void returnUnmodifiableSnapshot() {
        SensorCacheEntry sensorEntry = createSensorEntry(1L, "device-1", "sensor-1", TEMPERATURE);
        sensorCache.upsert(sensorEntry);

        List<SensorCacheEntry> snapshot = sensorCache.getSnapshot();

        assertThat(snapshot).containsExactly(sensorEntry);
        assertThatThrownBy(() -> snapshot.add(createSensorEntry(2L, "device-2", "sensor-2", HUMIDITY)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static SensorCacheEntry createSensorEntry(
            long cultivationId,
            String deviceEui,
            String deviceName,
            SensorTypeSpec... sensorTypes
    ) {
        return createSensorEntry(
                cultivationId,
                deviceEui,
                deviceName,
                "greenhouse",
                "zone-a",
                "sensor-model",
                sensorTypes
        );
    }

    private static SensorCacheEntry createSensorEntry(
            long cultivationId,
            String deviceEui,
            String deviceName,
            String location,
            String locationDetail,
            String deviceModel,
            SensorTypeSpec... sensorTypes
    ) {
        return new SensorCacheEntry(
                cultivationId,
                deviceEui,
                deviceName,
                location,
                locationDetail,
                deviceModel,
                Set.of(sensorTypes)
        );
    }
}

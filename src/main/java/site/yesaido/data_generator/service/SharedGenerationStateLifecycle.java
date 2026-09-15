package site.yesaido.data_generator.service;

import org.springframework.stereotype.Service;
import site.yesaido.data_generator.cache.SensorCache;
import site.yesaido.data_generator.converter.SensorUnitConverter;
import site.yesaido.data_generator.domain.*;
import site.yesaido.data_generator.exception.SensorDataGenerationException;
import site.yesaido.data_generator.generator.EnvironmentRandomWalkGenerator;
import site.yesaido.data_generator.generator.FixedSensorConfigurationRegistry;
import site.yesaido.data_generator.generator.SensorObservationProjector;

import java.util.List;
import java.util.Optional;

// 삭제된 센서 채널에 더 이상 필요하지 않은 공용 환경·EUI 관측 상태를 정리합니다.
@Service
public final class SharedGenerationStateLifecycle {

    private final SensorCache sensorCache;
    private final FixedSensorConfigurationRegistry fixedSensorConfigurationRegistry;
    private final SensorUnitConverter sensorUnitConverter;
    private final EnvironmentRandomWalkGenerator environmentRandomWalkGenerator;
    private final SensorObservationProjector sensorObservationProjector;

    public SharedGenerationStateLifecycle(
            SensorCache sensorCache,
            FixedSensorConfigurationRegistry fixedSensorConfigurationRegistry,
            SensorUnitConverter sensorUnitConverter,
            EnvironmentRandomWalkGenerator environmentRandomWalkGenerator,
            SensorObservationProjector sensorObservationProjector
    ) {
        if (sensorCache == null) {
            throw new SensorDataGenerationException("sensorCache는 null일 수 없습니다.");
        }

        if (fixedSensorConfigurationRegistry == null) {
            throw new SensorDataGenerationException("fixedSensorConfigurationRegistry는 null일 수 없습니다.");
        }

        if (sensorUnitConverter == null) {
            throw new SensorDataGenerationException("sensorUnitConverter는 null일 수 없습니다.");
        }

        if (environmentRandomWalkGenerator == null) {
            throw new SensorDataGenerationException("environmentRandomWalkGenerator는 null일 수 없습니다.");
        }

        if (sensorObservationProjector == null) {
            throw new SensorDataGenerationException("sensorObservationProjector는 null일 수 없습니다.");
        }

        this.sensorCache = sensorCache;
        this.fixedSensorConfigurationRegistry = fixedSensorConfigurationRegistry;
        this.sensorUnitConverter = sensorUnitConverter;
        this.environmentRandomWalkGenerator = environmentRandomWalkGenerator;
        this.sensorObservationProjector = sensorObservationProjector;
    }

    /*
     * SensorCache.removeChannel()로 캐시에서 채널을 먼저 삭제한 뒤
     * 호출해야 합니다.
     */
    public void removeDeletedChannelState(long cultivationId, SensorChannelKey deletedChannelKey) {
        validateCultivationId(cultivationId);
        validateSensorChannelKey(deletedChannelKey);

        Optional<EnvironmentStateKey> optionalEnvironmentStateKey =
                resolveEnvironmentStateKey(cultivationId, deletedChannelKey);

        // 지원하지 않는 고정 센서 단위는 생성 상태도 만들지 않았습니다.
        if (optionalEnvironmentStateKey.isEmpty()) {
            return;
        }

        EnvironmentStateKey environmentStateKey = optionalEnvironmentStateKey.get();

        List<SensorCacheEntry> currentSensorEntries = sensorCache.getSnapshot();

        boolean sameDeviceStillObservesEnvironment = currentSensorEntries.stream()
                .filter(entry -> entry.cultivationId() == cultivationId)
                .filter(entry -> entry.deviceEui().equals(deletedChannelKey.deviceEui()))
                .anyMatch(entry -> referencesEnvironment(entry, environmentStateKey)
                );

        /*
         * 예: 같은 EUI에 TEMPERATURE °C와 °F가 함께 있고
         * °F만 삭제된 경우입니다. 두 채널은 같은 공용 환경과
         * 같은 EUI 관측 상태를 공유하므로 아무 상태도 삭제하지 않습니다.
         */
        if (sameDeviceStillObservesEnvironment) {
            return;
        }

        SensorObservationKey observationKey = new SensorObservationKey(environmentStateKey, deletedChannelKey.deviceEui());

        sensorObservationProjector.removeState(observationKey);

        boolean cultivationStillObservesEnvironment = currentSensorEntries.stream()
                .filter(entry -> entry.cultivationId() == cultivationId)
                .anyMatch(entry -> referencesEnvironment(entry, environmentStateKey)
                );

        /*
         * 다른 EUI도 같은 환경을 관측하지 않을 때만
         * cultivation 공용 환경 상태를 삭제합니다.
         */
        if (!cultivationStillObservesEnvironment) {
            environmentRandomWalkGenerator.removeState(environmentStateKey);
        }
    }

    public void removeCultivationStates(long cultivationId) {
        validateCultivationId(cultivationId);

        sensorObservationProjector.removeStatesByCultivationId(cultivationId);
        environmentRandomWalkGenerator.removeStatesByCultivationId(cultivationId);
    }

    private boolean referencesEnvironment(SensorCacheEntry sensorCacheEntry,
                                          EnvironmentStateKey expectedEnvironmentStateKey) {
        if (sensorCacheEntry.cultivationId() != expectedEnvironmentStateKey.cultivationId()) {
            return false;
        }

        return sensorCacheEntry.sensorTypes().stream()
                .map(sensorTypeSpec ->
                        createChannelKey(sensorCacheEntry, sensorTypeSpec))
                .map(sensorChannelKey ->
                        resolveEnvironmentStateKey(sensorCacheEntry.cultivationId(), sensorChannelKey))
                .flatMap(Optional::stream)
                .anyMatch(expectedEnvironmentStateKey::equals);
    }

    private Optional<EnvironmentStateKey> resolveEnvironmentStateKey(long cultivationId, SensorChannelKey sensorChannelKey) {
        Optional<MeasurementConfiguration> fixedConfiguration = fixedSensorConfigurationRegistry
                .findBySensorType(sensorChannelKey.sensorType());

        if (fixedConfiguration.isEmpty()) {
            return Optional.of(new EnvironmentStateKey(cultivationId, sensorChannelKey.sensorType(), sensorChannelKey.unit()));
        }

        String canonicalUnit = sensorUnitConverter.findCanonicalUnit(sensorChannelKey.sensorType())
                .orElseThrow(() ->
                                new SensorDataGenerationException("고정 센서 타입의 표준 단위를 찾을 수 없습니다. sensorType="
                                        + sensorChannelKey.sensorType()));

        boolean outputUnitSupported = sensorUnitConverter
                .convertFromCanonical(sensorChannelKey.sensorType(),
                        sensorChannelKey.unit(), fixedConfiguration.get().initialValue())
                .isPresent();

        if (!outputUnitSupported) {
            return Optional.empty();
        }

        return Optional.of(new EnvironmentStateKey(cultivationId, sensorChannelKey.sensorType(), canonicalUnit));
    }

    private static SensorChannelKey createChannelKey(SensorCacheEntry sensorCacheEntry, SensorTypeSpec sensorTypeSpec) {
        return new SensorChannelKey(sensorCacheEntry.deviceEui(), sensorTypeSpec.sensorType(), sensorTypeSpec.unit());
    }

    private static void validateCultivationId(long cultivationId) {
        if (cultivationId <= 0) {
            throw new SensorDataGenerationException("cultivationId는 0보다 커야 합니다.");
        }
    }

    private static void validateSensorChannelKey(SensorChannelKey sensorChannelKey) {
        if (sensorChannelKey == null) {
            throw new SensorDataGenerationException("deletedChannelKey는 null일 수 없습니다.");
        }
    }
}

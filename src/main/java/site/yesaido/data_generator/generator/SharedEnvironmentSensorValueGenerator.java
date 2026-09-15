package site.yesaido.data_generator.generator;

import org.springframework.stereotype.Component;
import site.yesaido.data_generator.converter.SensorUnitConverter;
import site.yesaido.data_generator.domain.*;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.util.Optional;

@Component
public final class SharedEnvironmentSensorValueGenerator {

    private final SensorGenerationPlanResolver sensorGenerationPlanResolver;
    private final EnvironmentRandomWalkGenerator environmentRandomWalkGenerator;
    private final SensorObservationProjector sensorObservationProjector;
    private final SensorUnitConverter sensorUnitConverter;

    public SharedEnvironmentSensorValueGenerator(
            SensorGenerationPlanResolver sensorGenerationPlanResolver,
            EnvironmentRandomWalkGenerator environmentRandomWalkGenerator,
            SensorObservationProjector sensorObservationProjector,
            SensorUnitConverter sensorUnitConverter
    ) {
        if (sensorGenerationPlanResolver == null) {
            throw new SensorDataGenerationException("sensorGenerationPlanResolver는 null일 수 없습니다.");
        }
        if (environmentRandomWalkGenerator == null) {
            throw new SensorDataGenerationException("environmentRandomWalkGenerator는 null일 수 없습니다.");
        }
        if (sensorObservationProjector == null) {
            throw new SensorDataGenerationException("sensorObservationProjector는 null일 수 없습니다.");
        }
        if (sensorUnitConverter == null) {
            throw new SensorDataGenerationException("sensorUnitConverter는 null일 수 없습니다.");
        }

        this.sensorGenerationPlanResolver = sensorGenerationPlanResolver;
        this.environmentRandomWalkGenerator = environmentRandomWalkGenerator;
        this.sensorObservationProjector = sensorObservationProjector;
        this.sensorUnitConverter = sensorUnitConverter;
    }

    public Optional<Number> generateNextValue(long cultivationId, SensorChannelKey sensorChannelKey,
            double actuatorEffectAmount, long cycleId) {
        validateCultivationId(cultivationId);
        validateSensorChannelKey(sensorChannelKey);
        validateActuatorEffectAmount(actuatorEffectAmount);
        validateCycleId(cycleId);

        return sensorGenerationPlanResolver.resolve(cultivationId, sensorChannelKey)
                .flatMap(plan -> generateFromPlan(
                        cultivationId, sensorChannelKey, actuatorEffectAmount, cycleId, plan));
    }

    private Optional<Number> generateFromPlan(long cultivationId, SensorChannelKey sensorChannelKey,
            double actuatorEffectAmount, long cycleId, SensorGenerationPlan plan) {
        validateResolvedPlan(cultivationId, sensorChannelKey, plan);

        EnvironmentStateKey environmentStateKey = plan.environmentStateKey();

        EnvironmentState environmentState = environmentRandomWalkGenerator.advance(
                environmentStateKey, plan.measurementConfiguration(), actuatorEffectAmount, cycleId);

        SensorObservationKey observationKey = new SensorObservationKey(environmentStateKey, sensorChannelKey.deviceEui());
        SensorObservationState observationState = sensorObservationProjector.project(
                observationKey, environmentState, plan.measurementConfiguration());

        Optional<Number> convertedValue = sensorUnitConverter.convertFromCanonical(
                sensorChannelKey.sensorType(), sensorChannelKey.unit(), observationState.canonicalValue());

        if (convertedValue.isPresent()) {
            return convertedValue;
        }

        boolean dynamicSensorType = sensorUnitConverter
                        .findCanonicalUnit(sensorChannelKey.sensorType())
                        .isEmpty();

        // 동적 타입만 등록 단위 자체를 내부 기준 단위로 사용합니다.
        if (dynamicSensorType && environmentStateKey.canonicalUnit().equals(sensorChannelKey.unit())) {
            return Optional.of(observationState.canonicalValue());
        }

        throw new SensorDataGenerationException("표준 단위 관측값을 요청 단위로 변환할 수 없습니다. sensorType=" + sensorChannelKey.sensorType()
                + ", canonicalUnit=" + environmentStateKey.canonicalUnit() + ", requestedUnit=" + sensorChannelKey.unit());
    }

    private static void validateResolvedPlan(long cultivationId, SensorChannelKey sensorChannelKey, SensorGenerationPlan plan) {
        EnvironmentStateKey environmentStateKey = plan.environmentStateKey();

        if (environmentStateKey.cultivationId() != cultivationId) {
            throw new SensorDataGenerationException("생성 계획의 cultivationId가 요청과 일치하지 않습니다.");
        }

        if (!environmentStateKey.sensorType().equals(sensorChannelKey.sensorType())) {
            throw new SensorDataGenerationException("생성 계획의 sensorType이 요청과 일치하지 않습니다.");
        }
    }

    private static void validateCultivationId(long cultivationId) {
        if (cultivationId <= 0) {
            throw new SensorDataGenerationException("cultivationId는 0보다 커야 합니다.");
        }
    }

    private static void validateSensorChannelKey(SensorChannelKey sensorChannelKey) {
        if (sensorChannelKey == null) {
            throw new SensorDataGenerationException("sensorChannelKey는 null일 수 없습니다.");
        }
    }

    private static void validateActuatorEffectAmount(double actuatorEffectAmount) {
        if (!Double.isFinite(actuatorEffectAmount)) {
            throw new SensorDataGenerationException("actuatorEffectAmount는 유한한 숫자여야 합니다.");
        }
    }

    private static void validateCycleId(long cycleId) {
        if (cycleId <= 0) {
            throw new SensorDataGenerationException("cycleId는 0보다 커야 합니다.");
        }
    }
}

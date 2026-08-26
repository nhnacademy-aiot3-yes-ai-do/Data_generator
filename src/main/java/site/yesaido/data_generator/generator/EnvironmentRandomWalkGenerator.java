package site.yesaido.data_generator.generator;

import site.yesaido.data_generator.domain.EnvironmentState;
import site.yesaido.data_generator.domain.EnvironmentStateKey;
import site.yesaido.data_generator.domain.MeasurementConfiguration;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.random.RandomGenerator;

// 재배 공용 환경값을 환경 키와 생성 주기별로 관리하는 생성기
public final class EnvironmentRandomWalkGenerator {

    private final RandomWalkStepCalculator stepCalculator;
    private final ConcurrentMap<EnvironmentStateKey, EnvironmentState> environmentStates = new ConcurrentHashMap<>();

    public EnvironmentRandomWalkGenerator(RandomGenerator randomGenerator) {
        this.stepCalculator = new RandomWalkStepCalculator(randomGenerator);
    }

    public EnvironmentState advance(EnvironmentStateKey environmentStateKey, MeasurementConfiguration configuration,
            double actuatorEffectAmount, long cycleId
    ) {
        validateEnvironmentStateKey(environmentStateKey);
        validateMeasurementConfiguration(configuration);
        validateActuatorEffectAmount(actuatorEffectAmount);
        validateCycleId(cycleId);

        return environmentStates.compute(environmentStateKey,
                (stateKey, previousState)
                        -> calculateNextState(previousState, configuration, actuatorEffectAmount, cycleId)
        );
    }

    public void removeState(EnvironmentStateKey environmentStateKey) {
        validateEnvironmentStateKey(environmentStateKey);

        environmentStates.remove(environmentStateKey);
    }

    public void removeStatesByCultivationId(long cultivationId) {
        validateCultivationId(cultivationId);

        environmentStates.keySet().removeIf(
                key -> key.cultivationId() == cultivationId
        );
    }

    private EnvironmentState calculateNextState(EnvironmentState previousState, MeasurementConfiguration configuration,
            double actuatorEffectAmount, long cycleId
    ) {
        if (previousState != null) {
            long lastCycleId = previousState.lastAdvancedCycleId();

            if (cycleId < lastCycleId) {
                throw new SensorDataGenerationException(
                        "cycleId는 마지막으로 적용된 생성 주기보다 작을 수 없습니다. cycleId=" + cycleId
                                + ", lastAdvancedCycleId=" + lastCycleId);
            }

            if (cycleId == lastCycleId) {
                return previousState;
            }
        }

        Double previousValue = previousState == null ? null : previousState.canonicalValue();

        double nextValue = stepCalculator.calculateNextValue(previousValue, configuration, actuatorEffectAmount);

        return new EnvironmentState(nextValue, cycleId);
    }

    private static void validateEnvironmentStateKey(EnvironmentStateKey environmentStateKey) {
        if (environmentStateKey == null) {
            throw new SensorDataGenerationException("environmentStateKey는 null일 수 없습니다.");
        }
    }

    private static void validateMeasurementConfiguration(MeasurementConfiguration configuration) {
        if (configuration == null) {
            throw new SensorDataGenerationException("configuration은 null일 수 없습니다.");
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

    private static void validateCultivationId(long cultivationId) {
        if (cultivationId <= 0) {
            throw new SensorDataGenerationException("cultivationId는 0보다 커야 합니다.");
        }
    }
}

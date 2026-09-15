package site.yesaido.data_generator.generator;

import site.yesaido.data_generator.domain.*;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.random.RandomGenerator;

// 재배 공용 환경값을 EUI별 센서 관측값으로 변환하는 생성기
public final class SensorObservationProjector {

    private final RandomGenerator randomGenerator;
    private final double maximumBiasRatio;
    private final double maximumDeviationRatio;
    private final double deviationRetentionRatio;

    private final ConcurrentMap<SensorObservationKey, SensorObservationState>
            observationStates = new ConcurrentHashMap<>();

    public SensorObservationProjector(RandomGenerator randomGenerator, SensorObservationPolicy policy) {
        if (randomGenerator == null) {
            throw new SensorDataGenerationException("randomGenerator는 null일 수 없습니다.");
        }

        if (policy == null) {
            throw new SensorDataGenerationException("policy는 null일 수 없습니다.");
        }

        double convertedRetentionRatio = policy.deviationRetentionRatio().doubleValue();

        if (!Double.isFinite(convertedRetentionRatio) || convertedRetentionRatio < 0.0 || convertedRetentionRatio >= 1.0) {
            throw new SensorDataGenerationException("deviationRetentionRatio를 유효한 double 값으로 변환할 수 없습니다.");
        }

        this.randomGenerator = randomGenerator;
        this.maximumBiasRatio = policy.maximumBiasRatio().doubleValue();
        this.maximumDeviationRatio = policy.maximumDeviationRatio().doubleValue();
        this.deviationRetentionRatio = convertedRetentionRatio;
    }

    /*
     * 전달된 environmentState는 observationKey의 EnvironmentStateKey에
     * 해당하는 공용 환경 상태여야 합니다.
     */
    public SensorObservationState project(SensorObservationKey observationKey,
            EnvironmentState environmentState, MeasurementConfiguration configuration) {
        validateObservationKey(observationKey);
        validateEnvironmentState(environmentState);
        validateMeasurementConfiguration(configuration);

        double rangeSpan = calculateRangeSpan(configuration);
        double maximumBias = rangeSpan * maximumBiasRatio;
        double maximumDeviation = rangeSpan * maximumDeviationRatio;
        long cycleId = environmentState.lastAdvancedCycleId();

        return observationStates.compute(observationKey,
                (ignoredKey, previousState) -> calculateNextState(
                        previousState, environmentState, configuration, maximumBias, maximumDeviation, cycleId)
        );
    }

    public void removeState(SensorObservationKey observationKey) {
        validateObservationKey(observationKey);

        observationStates.remove(observationKey);
    }

    public void removeStatesByCultivationId(long cultivationId) {
        validateCultivationId(cultivationId);

        observationStates.keySet().removeIf(key
                -> key.environmentStateKey().cultivationId() == cultivationId);
    }

    private SensorObservationState calculateNextState(SensorObservationState previousState,
            EnvironmentState environmentState, MeasurementConfiguration configuration,
            double maximumBias, double maximumDeviation, long cycleId) {
        if (previousState != null) {
            long lastCycleId = previousState.lastProjectedCycleId();

            if (cycleId < lastCycleId) {
                throw new SensorDataGenerationException("환경 상태의 생성 주기가 마지막 관측 주기보다 오래되었습니다. cycleId="
                        + cycleId + ", lastProjectedCycleId=" + lastCycleId);
            }

            if (cycleId == lastCycleId) {
                return previousState;
            }
        }

        double canonicalBias = calculateCanonicalBias(previousState, maximumBias);
        double canonicalDeviation = calculateCanonicalDeviation(previousState, maximumDeviation);

        double candidateValue = environmentState.canonicalValue() + canonicalBias + canonicalDeviation;
        double canonicalValue = RandomWalkStepCalculator.normalizeValue(candidateValue, configuration);

        return new SensorObservationState(canonicalBias, canonicalDeviation, canonicalValue, cycleId);
    }

    private double calculateCanonicalBias(SensorObservationState previousState, double maximumBias) {
        if (previousState != null) {
            return previousState.canonicalBias();
        }

        return generateSymmetricValue(maximumBias);
    }

    private double calculateCanonicalDeviation(SensorObservationState previousState, double maximumDeviation) {
        if (maximumDeviation == 0.0) {
            return 0.0;
        }

        double previousDeviation = previousState == null ? 0.0 : Math.clamp(previousState.canonicalDeviation(), -maximumDeviation, maximumDeviation);
        double targetDeviation = generateSymmetricValue(maximumDeviation);
        double nextDeviation = deviationRetentionRatio * previousDeviation + (1.0 - deviationRetentionRatio) * targetDeviation;

        return Math.clamp(nextDeviation, -maximumDeviation, maximumDeviation);
    }

    /*
     * nextDouble(-maximum, maximum)은 범위 차 계산에서 overflow가
     * 발생할 수 있으므로 -1~1 난수에 최대 크기를 곱합니다.
     */
    private double generateSymmetricValue(double maximumMagnitude) {
        if (maximumMagnitude == 0.0) {
            return 0.0;
        }

        return randomGenerator.nextDouble(-1.0, 1.0) * maximumMagnitude;
    }

    private static double calculateRangeSpan(MeasurementConfiguration configuration) {
        double rangeSpan = configuration.maximumValue() - configuration.minimumValue();

        if (!Double.isFinite(rangeSpan) || rangeSpan < 0.0) {
            throw new SensorDataGenerationException("측정 가능 범위 폭은 유한한 0 이상의 숫자여야 합니다.");
        }

        return rangeSpan;
    }

    private static void validateObservationKey(SensorObservationKey observationKey) {
        if (observationKey == null) {
            throw new SensorDataGenerationException("observationKey는 null일 수 없습니다.");
        }
    }

    private static void validateEnvironmentState(EnvironmentState environmentState) {
        if (environmentState == null) {
            throw new SensorDataGenerationException("environmentState는 null일 수 없습니다.");
        }
    }

    private static void validateMeasurementConfiguration(MeasurementConfiguration configuration) {
        if (configuration == null) {
            throw new SensorDataGenerationException("configuration은 null일 수 없습니다.");
        }
    }

    private static void validateCultivationId(long cultivationId) {
        if (cultivationId <= 0) {
            throw new SensorDataGenerationException("cultivationId는 0보다 커야 합니다.");
        }
    }
}

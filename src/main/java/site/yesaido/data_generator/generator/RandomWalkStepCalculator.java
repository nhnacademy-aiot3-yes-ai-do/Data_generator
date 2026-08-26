package site.yesaido.data_generator.generator;

import site.yesaido.data_generator.domain.MeasurementConfiguration;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.random.RandomGenerator;

// Random Walk의 다음 값을 계산하는 내부 전용 상태 없는 계산기
final class RandomWalkStepCalculator {

    private final RandomGenerator randomGenerator;

    RandomWalkStepCalculator(RandomGenerator randomGenerator) {
        if (randomGenerator == null) {
            throw new SensorDataGenerationException("randomGenerator는 null일 수 없습니다.");
        }

        this.randomGenerator = randomGenerator;
    }

    double calculateNextValue(Double previousValue, MeasurementConfiguration configuration, double actuatorEffectAmount) {
        double candidateValue;

        if (previousValue == null) {
            candidateValue = configuration.initialValue() + actuatorEffectAmount;
        } else {
            double randomChangeAmount = generateRandomChangeAmount(configuration);

            candidateValue = previousValue + randomChangeAmount + actuatorEffectAmount;
        }

        return normalizeValue(candidateValue, configuration);
    }

    static double normalizeValue(double candidateValue, MeasurementConfiguration configuration) {
        double boundedValue = clampValue(candidateValue, configuration.minimumValue(), configuration.maximumValue());
        double roundedValue = roundValue(boundedValue, configuration.decimalPlaces());

        return clampValue(roundedValue, configuration.minimumValue(), configuration.maximumValue());
    }

    // 최대 변화량이 0이면 RandomGenerator를 호출하지 않습니다.
    private double generateRandomChangeAmount(MeasurementConfiguration configuration) {
        double maximumChange = configuration.maximumChange();

        if (maximumChange == 0.0) {
            return 0.0;
        }

        return randomGenerator.nextDouble(-maximumChange, maximumChange);
    }

    private static double clampValue(double value, double minimumValue, double maximumValue) {
        return Math.clamp(value, minimumValue, maximumValue);
    }

    private static double roundValue(double value, int decimalPlaces) {
        return BigDecimal.valueOf(value)
                .setScale(decimalPlaces, RoundingMode.HALF_UP)
                .doubleValue();
    }
}

package site.yesaido.data_generator.generator;

import site.yesaido.data_generator.domain.MeasurementConfiguration;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.random.RandomGenerator;

public class RandomWalkGenerator {

    private final RandomGenerator randomGenerator;

    // deviceEui, sensorType, unit 조합별 Random Walk 상태를 저장합니다.
    private final ConcurrentMap<SensorChannelKey, Double> sensorChannelPreviousValues = new ConcurrentHashMap<>();

    public RandomWalkGenerator(RandomGenerator randomGenerator) {
        if (randomGenerator == null) {
            throw new SensorDataGenerationException("randomGenerator는 null일 수 없습니다.");
        }

        this.randomGenerator = randomGenerator;
    }

    public Number generateNextValue(SensorChannelKey sensorChannelKey,
            MeasurementConfiguration measurementConfiguration,
            double actuatorEffectAmount
    ) {
        validateSensorChannelKey(sensorChannelKey);
        validateMeasurementConfiguration(measurementConfiguration);
        validateActuatorEffectAmount(actuatorEffectAmount);

        double generatedValue = sensorChannelPreviousValues.compute(
                sensorChannelKey, (stateKey, previousValue) ->
                        calculateNextValue(previousValue, measurementConfiguration, actuatorEffectAmount)
                );

        return convertGeneratedValue(generatedValue, measurementConfiguration);
    }

    // 지정한 센서 채널 하나의 Random Walk 상태만 제거합니다.
    public void removeState(SensorChannelKey sensorChannelKey) {
        validateSensorChannelKey(sensorChannelKey);

        sensorChannelPreviousValues.remove(sensorChannelKey);
    }

    // 지정한 장치에 속한 모든 센서 채널 상태를 제거합니다.
    public void removeStatesByDeviceEui(String deviceEui) {
        validateDeviceEui(deviceEui);

        sensorChannelPreviousValues.keySet().removeIf(sensorChannelKey
                -> deviceEui.equals(sensorChannelKey.deviceEui()));
    }

    private double calculateNextValue(Double previousValue,
            MeasurementConfiguration measurementConfiguration,
            double actuatorEffectAmount
    ) {
        double candidateValue;

        if (previousValue == null) {
            candidateValue = measurementConfiguration.initialValue() + actuatorEffectAmount;
        } else {
            double randomChangeAmount = generateRandomChangeAmount(measurementConfiguration);
            candidateValue = previousValue + randomChangeAmount + actuatorEffectAmount;
        }

        double boundedValue = clampValue(candidateValue, measurementConfiguration.minimumValue(), measurementConfiguration.maximumValue());
        double roundedValue = roundValue(boundedValue, measurementConfiguration.decimalPlaces());

        return clampValue(roundedValue, measurementConfiguration.minimumValue(), measurementConfiguration.maximumValue());
    }

    // 최대 변화량이 0이면 RandomGenerator를 호출하지 않습니다.
    private double generateRandomChangeAmount(MeasurementConfiguration measurementConfiguration) {
        double maximumChange = measurementConfiguration.maximumChange();

        if (maximumChange == 0.0) {
            return 0.0;
        }

        return randomGenerator.nextDouble(-maximumChange, maximumChange);
    }

    private static Number convertGeneratedValue(
            double generatedValue,
            MeasurementConfiguration measurementConfiguration
    ) {
        if (measurementConfiguration.decimalPlaces() == 0) {
            return Math.round(generatedValue);
        }

        return generatedValue;
    }

    private static double clampValue(double value, double minimumValue, double maximumValue) {
        return Math.clamp(value, minimumValue, maximumValue);
    }

    private static double roundValue(double value, int decimalPlaces) {
        return BigDecimal.valueOf(value)
                .setScale(decimalPlaces, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static void validateSensorChannelKey(SensorChannelKey sensorChannelKey) {
        if (sensorChannelKey == null) {
            throw new SensorDataGenerationException("sensorChannelKey는 null일 수 없습니다.");
        }
    }

    private static void validateMeasurementConfiguration(MeasurementConfiguration measurementConfiguration) {
        if (measurementConfiguration == null) {
            throw new SensorDataGenerationException("measurementConfiguration은 null일 수 없습니다.");
        }
    }

    private static void validateActuatorEffectAmount(double actuatorEffectAmount) {
        if (!Double.isFinite(actuatorEffectAmount)) {
            throw new SensorDataGenerationException("actuatorEffectAmount는 유한한 숫자여야 합니다.");
        }
    }

    private static void validateDeviceEui(
            String deviceEui
    ) {
        if (deviceEui == null || deviceEui.isBlank()) {
            throw new SensorDataGenerationException(
                    "deviceEui는 null이거나 공백일 수 없습니다."
            );
        }
    }
}

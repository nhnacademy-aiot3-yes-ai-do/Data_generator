package site.yesaido.data_generator.generator;

import site.yesaido.data_generator.domain.MeasurementConfiguration;
import site.yesaido.data_generator.domain.MeasurementType;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.random.RandomGenerator;

public class RandomWalkGenerator {

    private final RandomGenerator randomGenerator;
    private final Map<MeasurementType, MeasurementConfiguration> measurementConfigurations;

    // 기존 MeasurementType 기반 생성 흐름에서 사용하는 상태 저장소
    private final ConcurrentMap<MeasurementStateKey, Double> previousValues = new ConcurrentHashMap<>();

    // String sensorType과 unit을 포함한 독립 채널별 상태 저장소
    private final ConcurrentMap<SensorChannelKey, Double> sensorChannelPreviousValues = new ConcurrentHashMap<>();

    public RandomWalkGenerator(RandomGenerator randomGenerator) {
        this(randomGenerator, MeasurementConfiguration.getDefaultConfigurations());
    }

    public RandomWalkGenerator(RandomGenerator randomGenerator, Map<MeasurementType, MeasurementConfiguration> measurementConfigurations) {
        if (randomGenerator == null) {
            throw new SensorDataGenerationException("randomGenerator는 null일 수 없습니다.");
        }

        if (measurementConfigurations == null) {
            throw new SensorDataGenerationException("measurementConfigurations는 null일 수 없습니다.");
        }

        for (MeasurementType measurementType : MeasurementType.values()) {
            if (measurementConfigurations.get(measurementType) == null) {
                throw new SensorDataGenerationException("측정값 설정이 없습니다: " + measurementType);
            }
        }

        this.randomGenerator = randomGenerator;
        this.measurementConfigurations = Map.copyOf(measurementConfigurations);
    }

    public Number generateNextValue(String deviceEui, MeasurementType measurementType) {
        return generateNextValue(deviceEui, measurementType, 0.0);
    }

    public Number generateNextValue(String deviceEui, MeasurementType measurementType, double actuatorEffectAmount) {
        validateDeviceEui(deviceEui);
        validateMeasurementType(measurementType);
        validateActuatorEffectAmount(actuatorEffectAmount);

        MeasurementConfiguration measurementConfiguration = measurementConfigurations.get(measurementType);

        MeasurementStateKey measurementStateKey = new MeasurementStateKey(deviceEui, measurementType);

        double generatedValue = previousValues.compute(measurementStateKey,
                (stateKey, previousValue) -> calculateNextValue(
                        previousValue, measurementConfiguration, actuatorEffectAmount)
        );

        return convertGeneratedValue(generatedValue, measurementConfiguration);
    }

    // String 기반 생성기가 정확한 센서 채널의 다음 값을 생성할 때 사용하는 API
    public Number generateNextValue(SensorChannelKey sensorChannelKey,
                                    MeasurementConfiguration measurementConfiguration,
                                    double actuatorEffectAmount) {
        validateSensorChannelKey(sensorChannelKey);
        validateMeasurementConfiguration(measurementConfiguration);
        validateActuatorEffectAmount(actuatorEffectAmount);

        double generatedValue = sensorChannelPreviousValues.compute(
                sensorChannelKey, (stateKey, previousValue)
                        -> calculateNextValue(previousValue, measurementConfiguration, actuatorEffectAmount)
        );

        return convertGeneratedValue(generatedValue, measurementConfiguration);
    }

    // 지정한 센서 채널 하나의 Random Walk 상태만 제거
    public void removeState(SensorChannelKey sensorChannelKey) {
        validateSensorChannelKey(sensorChannelKey);

        sensorChannelPreviousValues.remove(sensorChannelKey);
    }

    public void removeStatesByDeviceEui(String deviceEui) {
        validateDeviceEui(deviceEui);

        previousValues.keySet().removeIf(
                measurementStateKey -> deviceEui.equals(measurementStateKey.deviceEui()));

        sensorChannelPreviousValues.keySet().removeIf(
                sensorChannelKey -> deviceEui.equals(sensorChannelKey.deviceEui()));
    }

    private double calculateNextValue(Double previousValue, MeasurementConfiguration measurementConfiguration, double actuatorEffectAmount) {
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

    // 최대 변화량이 0인 고정값 설정에서는 난수 생성기를 호출하지 않음
    private double generateRandomChangeAmount(MeasurementConfiguration measurementConfiguration) {
        double maximumChange = measurementConfiguration.maximumChange();
        if(maximumChange == 0.0) {
            return 0.0;
        }

        return randomGenerator.nextDouble(-maximumChange, maximumChange);
    }

    private static Number convertGeneratedValue(double generatedValue, MeasurementConfiguration measurementConfiguration) {
        if (measurementConfiguration.decimalPlaces() == 0) {
            return Math.round(generatedValue);
        }

        return generatedValue;
    }

    private static double clampValue(double value, double minimumValue, double maximumValue) {
        return Math.clamp(value, minimumValue, maximumValue);
    }

    private static double roundValue(double value, int decimalPlaces) {
        double scale = Math.pow(10, decimalPlaces);

        return Math.round(value * scale) / scale;
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

    private static void validateMeasurementType(MeasurementType measurementType) {
        if (measurementType == null) {
            throw new SensorDataGenerationException("measurementType은 null일 수 없습니다.");
        }
    }

    private static void validateDeviceEui(String deviceEui) {
        if (deviceEui == null || deviceEui.isBlank()) {
            throw new SensorDataGenerationException("deviceEui는 null이거나 공백일 수 없습니다.");
        }
    }
}

package site.yesaido.data_generator.generator;

import site.yesaido.data_generator.domain.MeasurementConfiguration;
import site.yesaido.data_generator.domain.MeasurementType;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.random.RandomGenerator;


public class RandomWalkGenerator {

    private final RandomGenerator randomGenerator;
    private final Map<MeasurementType, MeasurementConfiguration> measurementConfigurations;

    private final ConcurrentMap<MeasurementStateKey, Double> previousValues = new ConcurrentHashMap<>();

    public RandomWalkGenerator(RandomGenerator randomGenerator){
        this(randomGenerator, MeasurementConfiguration.getDefaultConfigurations());
    }

    public RandomWalkGenerator(RandomGenerator randomGenerator,Map<MeasurementType, MeasurementConfiguration> measurementConfigurations){
        if( randomGenerator == null){
            throw new SensorDataGenerationException("randomGenerator는 null일 수 없습니다.");
        }

        if( measurementConfigurations == null){
            throw new SensorDataGenerationException("measurementConfigurations는 null일 수 없습니다.");
        }

        for( MeasurementType measurementType : MeasurementType.values()){
            if(measurementConfigurations.get(measurementType) == null){
                throw new SensorDataGenerationException("측정값 설정이 없습니다: " + measurementType);
            }
        }

        this.randomGenerator = randomGenerator;
        this.measurementConfigurations = Map.copyOf(measurementConfigurations);

    }

    public Number generateNextValue(String deviceEui, MeasurementType measurementType) {
        return generateNextValue(deviceEui, measurementType, 0.0);
    }

    public Number generateNextValue(String deviceEui, MeasurementType measurementType, double actuatorEffectAmount){
        validateDeviceEui(deviceEui);
        validateMeasurementType(measurementType);
        validateActuatorEffectAmount(actuatorEffectAmount);

        MeasurementConfiguration measurementConfiguration = measurementConfigurations.get(measurementType);
        MeasurementStateKey measurementStateKey = new MeasurementStateKey(deviceEui, measurementType);

        double generatedValue  = previousValues.compute(measurementStateKey,(
                stateKey, previousValue) -> calculateNextValue(
                        previousValue, measurementConfiguration, actuatorEffectAmount));

        if( measurementConfiguration.decimalPlaces() == 0){
            return Math.round(generatedValue);
        }

        return generatedValue;

    }

    public void removeStatesByDeviceEui(String deviceEui){
        validateDeviceEui(deviceEui);

        previousValues.keySet().removeIf(
                measurementStateKey -> deviceEui.equals(measurementStateKey.deviceEui()));
    }

    private double calculateNextValue(Double previousValue, MeasurementConfiguration measurementConfiguration, double actuatorEffectAmount) {
        double candidateValue;

        if( previousValue == null ){
            candidateValue = measurementConfiguration.initialValue() + actuatorEffectAmount;
        }else {
            double randomChangeAmount = randomGenerator.nextDouble(-measurementConfiguration.maximumChange(), measurementConfiguration.maximumChange());
            candidateValue = previousValue + randomChangeAmount + actuatorEffectAmount;
        }

        double boundedValue = clampValue(candidateValue, measurementConfiguration.minimumValue(), measurementConfiguration.maximumValue());
        double roundedValue = roundValue(boundedValue, measurementConfiguration.decimalPlaces());

        return clampValue(roundedValue, measurementConfiguration.minimumValue(), measurementConfiguration.maximumValue());

    }

    private static double clampValue(double value, double minimumValue, double maximumValue) {
        return Math.clamp(value, minimumValue, maximumValue);
    }


    private static double roundValue(double value, int decimalPlaces){
        double scale = Math.pow(10, decimalPlaces);
        return Math.round(value * scale) / scale;
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

    private static void validateDeviceEui(String deviceEui){
        if( deviceEui == null || deviceEui.isBlank()){
            throw new SensorDataGenerationException("deviceEui는 null이거나 공백일 수 없습니다.");
        }
    }


}

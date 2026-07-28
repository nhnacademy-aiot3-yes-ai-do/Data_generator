package site.yesaido.data_generator.generator;

import site.yesaido.data_generator.domain.MeasurementConfiguration;
import site.yesaido.data_generator.domain.MeasurementType;

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
            throw new IllegalArgumentException("randomGenerator는 null일 수 없습니다.");
        }

        if( measurementConfigurations == null){
            throw new IllegalArgumentException("measurementConfigurations는 null일 수 없습니다.");
        }

        for( MeasurementType measurementType : MeasurementType.values()){
            if(measurementConfigurations.get(measurementType) == null){
                throw new IllegalArgumentException("측정값 설정이 없습니다: " + measurementType);
            }
        }

        this.randomGenerator = randomGenerator;
        this.measurementConfigurations = Map.copyOf(measurementConfigurations);

    }

    public Number generateNextValue(String deviceEui, MeasurementType measurementType){
        validateDeviceEui(deviceEui);
        if( measurementType == null) {
            throw new IllegalArgumentException("measurementType은 null일 수 없습니다.");
        }

        MeasurementConfiguration measurementConfiguration = measurementConfigurations.get(measurementType);
        MeasurementStateKey measurementStateKey = new MeasurementStateKey(deviceEui, measurementType);

        double generatedValue  = previousValues.compute(measurementStateKey,(stateKey, previousValue) -> calculateNextValue(previousValue, measurementConfiguration));

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

    private double calculateNextValue(Double previousValue, MeasurementConfiguration measurementConfiguration) {
        double candidateValue;

        if( previousValue == null ){
            candidateValue = measurementConfiguration.initialValue();
        }else {
            double changeAmount = randomGenerator.nextDouble(-measurementConfiguration.maximumChange(), measurementConfiguration.maximumChange());
            candidateValue = previousValue + changeAmount;
        }

        double boundedValue = clampValue(candidateValue, measurementConfiguration.minimumValue(), measurementConfiguration.maximumValue());
        double roundedValue = roundValue(boundedValue, measurementConfiguration.decimalPlaces());

        return clampValue(roundedValue, measurementConfiguration.minimumValue(), measurementConfiguration.maximumValue());

    }

    private double clampValue(double value, double minimumValue, double maximumValue) {
        return Math.clamp(value, minimumValue, maximumValue);
    }


    private double roundValue(double value, int decimalPlaces){
        double scale = Math.pow(10, decimalPlaces);
        return Math.round(value * scale) / scale;
    }

    private void validateDeviceEui(String deviceEui){
        if( deviceEui == null || deviceEui.isBlank()){
            throw new IllegalArgumentException("deviceEui는 null이거나 공백일 수 없습니다.");
        }
    }


}

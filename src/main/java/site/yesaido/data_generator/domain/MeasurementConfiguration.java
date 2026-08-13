package site.yesaido.data_generator.domain;

import java.util.Map;

public record MeasurementConfiguration(
        double initialValue,
        double minimumValue,
        double maximumValue,
        /*
         * 0보다 크면 Random Walk의 무작위 변화량으로 사용하고,
         * 0이면 무작위 변화 없이 고정값을 유지합니다.
         */
        double maximumChange,

        int decimalPlaces // 소숫점 몇 자리 까지 사용할지
) {
    private static final Map<MeasurementType, MeasurementConfiguration> DEFAULT_CONFIGURATIONS =
            Map.of(MeasurementType.TEMPERATURE , new MeasurementConfiguration(
                    16.0,
                    10.0,
                    30.0,
                    0.3,
                            1
            ),
                    MeasurementType.HUMIDITY,
                    new MeasurementConfiguration(
                            80.0,
                            40.0,
                            120.0,
                            1.0,
                            1
                    ),
                    MeasurementType.CO2,
                    new MeasurementConfiguration(
                            1500.0,
                            500.0,
                            4000.0,
                            30,
                            0
                    ),
                    MeasurementType.LIGHT,
                    new MeasurementConfiguration(
                            100.0,
                            0.0,
                            1000.0,
                            20.0,
                            0
                    )
        );

    public MeasurementConfiguration{
        validateFinite(initialValue, "initialValue");
        validateFinite(minimumValue,  "minimumValue");
        validateFinite(maximumValue, "maximumValue");
        validateFinite(maximumChange, "maximumChange");

        if (minimumValue > maximumValue) {
            throw new IllegalArgumentException("minimumValue는 maximumValue보다 클 수 없습니다.");
        }

        if (initialValue < minimumValue || initialValue > maximumValue) {
            throw new IllegalArgumentException("initialValue는 최솟값과 최댓값 사이여야 합니다.");
        }

        if (maximumChange < 0) {
            throw new IllegalArgumentException("maximumChange는 0 이상이어야 합니다.");
        }

        if (decimalPlaces < 0) {
            throw new IllegalArgumentException("decimalPlaces는 0보다 작을 수 없습니다.");
        }
    }

    public static Map<MeasurementType, MeasurementConfiguration> getDefaultConfigurations(){
        return DEFAULT_CONFIGURATIONS;
    }

    private static void validateFinite(double value, String fieldName){
        //isFinite() : 이 숫자가 유한한 숫자인지 검증
        if(!Double.isFinite(value)){
            throw new IllegalArgumentException(fieldName + "는 유한한 숫자여야 합니다.");
        }
    }
}

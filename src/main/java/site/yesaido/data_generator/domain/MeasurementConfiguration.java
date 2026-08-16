package site.yesaido.data_generator.domain;

public record MeasurementConfiguration(
        double initialValue,
        double minimumValue,
        double maximumValue,

        /*
         * 0보다 크면 Random Walk의 무작위 변화량으로 사용하고,
         * 0이면 무작위 변화 없이 고정값을 유지합니다.
         */
        double maximumChange,

        // 생성값에 적용할 소수점 자릿수입니다.
        int decimalPlaces
) {

    // double 기반 생성기가 지원하는 최대 소수점 자릿수입니다.
    public static final int MAX_DECIMAL_PLACES = 15;

    public MeasurementConfiguration {
        validateFinite(initialValue, "initialValue");
        validateFinite(minimumValue, "minimumValue");
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

        if (decimalPlaces > MAX_DECIMAL_PLACES) {
            throw new IllegalArgumentException("decimalPlaces는 " + MAX_DECIMAL_PLACES + " 이하여야 합니다.");
        }
    }

    private static void validateFinite(double value, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + "는 유한한 숫자여야 합니다.");
        }
    }
}

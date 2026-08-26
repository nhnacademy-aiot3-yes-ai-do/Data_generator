package site.yesaido.data_generator.converter;

import org.springframework.stereotype.Component;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.util.Optional;

// 내부 표준값과 °C, °F, %, ppm, lux 전송 단위를 변환하는 상태 없는 Spring Bean
@Component
public final class StandardSensorUnitConverter implements SensorUnitConverter {

    private static final String TEMPERATURE = "TEMPERATURE";
    private static final String HUMIDITY = "HUMIDITY";
    private static final String CO2 = "CO2";
    private static final String LIGHT = "LIGHT";

    private static final String CELSIUS = "°C";
    private static final String FAHRENHEIT = "°F";
    private static final String PERCENT = "%";
    private static final String PPM = "ppm";
    private static final String LUX = "lux";

    @Override
    public Optional<String> findCanonicalUnit(String sensorType) {
        String normalizedSensorType = normalizeRequiredText(sensorType, "sensorType");

        return switch (normalizedSensorType) {
            case TEMPERATURE -> Optional.of(CELSIUS);
            case HUMIDITY -> Optional.of(PERCENT);
            case CO2 -> Optional.of(PPM);
            case LIGHT -> Optional.of(LUX);
            default -> Optional.empty();
        };
    }

    @Override
    public Optional<Number> convertToCanonical(String sensorType, String unit, Number sourceValue) {
        String normalizedSensorType = normalizeRequiredText(sensorType, "sensorType");
        String normalizedUnit = normalizeRequiredText(unit, "unit");

        validateFiniteValue(sourceValue, "sourceValue");

        return switch (normalizedSensorType) {
            case TEMPERATURE -> convertTemperatureToCanonical(normalizedUnit, sourceValue);
            case HUMIDITY -> returnValueWhenUnitMatches(normalizedUnit, PERCENT, sourceValue);
            case CO2 -> returnValueWhenUnitMatches(normalizedUnit, PPM, sourceValue);
            case LIGHT -> returnValueWhenUnitMatches(normalizedUnit, LUX, sourceValue);
            default -> Optional.empty();
        };
    }

    @Override
    public Optional<Number> convertFromCanonical(String sensorType, String unit, Number canonicalValue) {
        String normalizedSensorType = normalizeRequiredText(sensorType, "sensorType");
        String normalizedUnit = normalizeRequiredText(unit, "unit");

        validateFiniteValue(canonicalValue, "canonicalValue");

        return switch (normalizedSensorType) {
            case TEMPERATURE -> convertTemperatureFromCanonical(normalizedUnit, canonicalValue);
            case HUMIDITY -> returnValueWhenUnitMatches(normalizedUnit, PERCENT, canonicalValue);
            case CO2 -> returnValueWhenUnitMatches(normalizedUnit, PPM, canonicalValue);
            case LIGHT -> returnValueWhenUnitMatches(normalizedUnit, LUX, canonicalValue);
            default -> Optional.empty();
        };
    }

    private static Optional<Number> convertTemperatureToCanonical(String unit, Number sourceValue) {
        if (CELSIUS.equals(unit)) {
            return Optional.of(sourceValue);
        }

        if (!FAHRENHEIT.equals(unit)) {
            return Optional.empty();
        }

        double celsius = (sourceValue.doubleValue() - 32.0) * 5.0 / 9.0;

        return Optional.of(celsius);
    }

    private static Optional<Number> convertTemperatureFromCanonical(String unit, Number canonicalValue) {
        if (CELSIUS.equals(unit)) {
            return Optional.of(canonicalValue);
        }

        if (!FAHRENHEIT.equals(unit)) {
            return Optional.empty();
        }

        double fahrenheit = canonicalValue.doubleValue() * 9.0 / 5.0 + 32.0;

        return Optional.of(roundToOneDecimalPlace(fahrenheit));
    }

    private static Optional<Number> returnValueWhenUnitMatches(String requestedUnit, String supportedUnit, Number value) {
        if (!supportedUnit.equals(requestedUnit)) {
            return Optional.empty();
        }

        return Optional.of(value);
    }

    private static double roundToOneDecimalPlace(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static void validateFiniteValue(Number value, String fieldName) {
        if (value == null) {
            throw new SensorDataGenerationException(fieldName + "는 null일 수 없습니다.");
        }

        if (!Double.isFinite(value.doubleValue())) {
            throw new SensorDataGenerationException(fieldName + "는 유한한 숫자여야 합니다.");
        }
    }

    private static String normalizeRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new SensorDataGenerationException(fieldName + "은 null이거나 빈 문자열 또는 공백 문자열일 수 없습니다.");
        }

        return value.strip();
    }
}

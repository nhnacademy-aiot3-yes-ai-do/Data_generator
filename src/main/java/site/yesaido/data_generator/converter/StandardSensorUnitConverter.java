package site.yesaido.data_generator.converter;

import org.springframework.stereotype.Component;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.util.Optional;

// 내부 표준값을 °C, °F, %RH, ppm, lux 전송 단위로 변환하는 상태 없는 Spring Bean
@Component
public final class StandardSensorUnitConverter implements  SensorUnitConverter{

    private static final String TEMPERATURE = "TEMPERATURE";
    private static final String HUMIDITY = "HUMIDITY";
    private static final String CO2 = "CO2";
    private static final String LIGHT = "LIGHT";

    private static final String CELSIUS = "°C";
    private static final String FAHRENHEIT = "°F";
    private static final String PERCENT = "%RH";
    private static final String PPM = "ppm";
    private static final String LUX = "lux";


    @Override
    public Optional<Number> convertFromCanonical(String sensorType, String unit, Number canonicalValue) {
        String normalizedSensorType = normalizedRequiredText(sensorType, "sensorType");
        String normalizedUnit = normalizedRequiredText(unit, "unit");

        validateCanonicalValue(canonicalValue);

        return switch (normalizedSensorType) {
            case TEMPERATURE -> convertTemperature(normalizedUnit, canonicalValue);
            case HUMIDITY -> returnCanonicalWhenUnitMatches(normalizedUnit, PERCENT,canonicalValue);
            case CO2 -> returnCanonicalWhenUnitMatches(normalizedUnit, PPM, canonicalValue);
            case LIGHT -> returnCanonicalWhenUnitMatches(normalizedUnit, LUX, canonicalValue);

            default -> Optional.empty();
        };
    }

    private static Optional<Number> convertTemperature(String unit, Number canonicalValue) {
        if(CELSIUS.equals(unit)) {
            return Optional.of(canonicalValue);
        }

        if(!FAHRENHEIT.equals(unit)) {
            return Optional.empty();
        }

        double fahrenheit = canonicalValue.doubleValue() * 9.0 / 5.0 + 32.0 ;

        return Optional.of(roundToOneDecimalPlace(fahrenheit));
    }

    private static Optional<Number> returnCanonicalWhenUnitMatches(String requestedUnit, String supportedUnit, Number canonicalValue) {
        if(!supportedUnit.equals(requestedUnit)) {
            return Optional.empty();
        }

        return Optional.of(canonicalValue);
    }

    private static double roundToOneDecimalPlace(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static void validateCanonicalValue(Number canonicalValue) {
        if(canonicalValue == null) {
            throw new SensorDataGenerationException("canonicalValue는 null일 수 없습니다.");
        }

        if(!Double.isFinite(canonicalValue.doubleValue())) {
            throw new SensorDataGenerationException("canonicalValue는 유한한 숫자여야 합니다.");
        }
    }

    private static String normalizedRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()){
            throw new SensorDataGenerationException(fieldName + "은 null이거나 빈 문자열 또는 공백 문자열일 수 없습니다.");
        }

        return value.strip();
    }
}

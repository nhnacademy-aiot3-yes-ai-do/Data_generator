package site.yesaido.data_generator.converter;

import java.util.Optional;

// 센서값의 내부 표준 단위 확인과 입출력 단위 변환을 제공하는 인터페이스
public interface SensorUnitConverter {

    Optional<String> findCanonicalUnit(String sensorType);

    Optional<Number> convertToCanonical(String sensorType, String unit, Number sourceValue);

    Optional<Number> convertFromCanonical(String sensorType, String unit, Number canonicalValue);

}

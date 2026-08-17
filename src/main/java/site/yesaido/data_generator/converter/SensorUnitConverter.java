package site.yesaido.data_generator.converter;

import java.util.Optional;

// 내부 표준 단위 값을 센서 채널에 등록된 전송 단위 값으로 변환하는 인터페이스
public interface SensorUnitConverter {

    Optional<Number> convertFromCanonical(String sensorType, String unit, Number canonicalValue);
}

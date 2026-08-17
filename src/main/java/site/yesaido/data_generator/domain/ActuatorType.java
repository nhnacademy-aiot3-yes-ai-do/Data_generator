package site.yesaido.data_generator.domain;

import lombok.Getter;
import site.yesaido.data_generator.exception.ActuatorTypeException;

// 가상 액추에이터 별 대상 센서 타입과 내부 표준 단위 효과량을 정의
@Getter
public enum ActuatorType {
    HEATER("TEMPERATURE", 0.5),
    COOLER("TEMPERATURE", -0.5),

    HUMIDIFIER("HUMIDITY", 2.0),
    DEHUMIDIFIER("HUMIDITY", -2.0),

    CO2_SUPPLIER("CO2", 60.0),
    VENTILATION_FAN("CO2", -60.0),

    LED("LIGHT", 50.0),
    LIGHT_REDUCER("LIGHT", -50.0);

    private final String targetSensorType;
    private final double effectAmount;

    ActuatorType(
            String targetSensorType,
            double effectAmount
    ) {
        if(targetSensorType == null || targetSensorType.isBlank()) {
            throw new ActuatorTypeException("targetSensorType은 null이거나 빈 문자열 또는 공백 문자열일 수 없습니다.");
        }
        if(!Double.isFinite(effectAmount)) {
            throw new ActuatorTypeException("effectAmount는 유한한 숫자여야 합니다.");
        }
        this.targetSensorType = targetSensorType.strip();
        this.effectAmount = effectAmount;
    }
    /**
     *  이후 명령 Service는 다음 흐름으로 충돌을 검사합니다.
     *
     *   HEATER에 ON 요청
     *       ↓
     *   HEATER.getOppositeType()
     *       ↓
     *   COOLER 상태 조회
     *       ↓
     *   COOLER가 ON이면 REJECTED_CONFLICT
     */
    public ActuatorType getOppositeType() {
        return switch (this) {
            case HEATER -> COOLER;
            case COOLER -> HEATER;
            case HUMIDIFIER -> DEHUMIDIFIER;
            case DEHUMIDIFIER -> HUMIDIFIER;
            case CO2_SUPPLIER -> VENTILATION_FAN;
            case VENTILATION_FAN -> CO2_SUPPLIER;
            case LED -> LIGHT_REDUCER;
            case LIGHT_REDUCER -> LED;
        };
    }
}

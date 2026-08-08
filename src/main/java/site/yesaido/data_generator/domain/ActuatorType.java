package site.yesaido.data_generator.domain;

import lombok.Getter;

@Getter
public enum ActuatorType {
    HEATER(MeasurementType.TEMPERATURE, 0.5),
    COOLER(MeasurementType.TEMPERATURE, -0.5),
    HUMIDIFIER(MeasurementType.HUMIDITY, 2.0),
    DEHUMIDIFIER(MeasurementType.HUMIDITY, -2.0),
    CO2_SUPPLIER(MeasurementType.CO2, 60.0),
    VENTILATION_FAN(MeasurementType.CO2, -60.0),
    LED(MeasurementType.LIGHT, 50.0),
    LIGHT_REDUCER(MeasurementType.LIGHT, -50.0);

    private final MeasurementType measurementType;
    private final double effectAmount;

    ActuatorType(
            MeasurementType measurementType,
            double effectAmount
    ) {
        this.measurementType = measurementType;
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

package site.yesaido.data_generator.domain;

import lombok.Getter;

@Getter
public enum MeasurementType {
    TEMPERATURE("temperature"),
    HUMIDITY("humidity"),
    CO2("co2"),
    LIGHT("light");

    private final String topicValue;

    MeasurementType(String topicValue){
        this.topicValue = topicValue;
    }
}

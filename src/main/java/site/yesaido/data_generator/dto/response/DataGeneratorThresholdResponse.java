package site.yesaido.data_generator.dto.response;

import java.math.BigDecimal;

public record DataGeneratorThresholdResponse(
        long cultivationId,
        String sensorType,
        String unit,
        BigDecimal minValue,
        BigDecimal maxValue
) {
}
package site.yesaido.data_generator.dto.response;

import java.util.List;

public record DataGeneratorSensorResponse(
        long cultivationId,
        String deviceEui,
        String deviceName,
        String location,
        String locationDetail,
        String deviceModel,
        List<CultivationSensorTypeResponse> sensorTypes
) {
}
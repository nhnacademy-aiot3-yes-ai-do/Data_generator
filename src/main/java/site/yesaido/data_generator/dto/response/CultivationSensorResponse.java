package site.yesaido.data_generator.dto.response;

import site.yesaido.data_generator.domain.MeasurementType;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.exception.SensorSynchronizationException;

import java.util.Set;

public record CultivationSensorResponse (
        long cultivationId,
        String deviceEui,
        String deviceName,
        String location,
        String locationDetail,
        String deviceModel,
        Set<MeasurementType> measurementTypes
){

    public CultivationSensorResponse {
        if (cultivationId <= 0) {
            throw new SensorSynchronizationException("cultivationId는 0보다 커야 합니다.");
        }

        requireText(deviceEui, "deviceEui");
        requireText(deviceName, "deviceName");
        requireText(location, "location");
        requireText(locationDetail, "locationDetail");
        requireText(deviceModel, "deviceModel");

        if (measurementTypes == null || measurementTypes.isEmpty()) {
            throw new SensorSynchronizationException("measurementTypes는 null이거나 비어 있을 수 없습니다.");
        }
        for(MeasurementType measurementType : measurementTypes) {
            if(measurementType == null) {
                throw new SensorSynchronizationException("measurementTypes에 null이 포함될 수 없습니다.");
            }
        }
        measurementTypes = Set.copyOf(measurementTypes);

    }

    public SensorCacheEntry convertToSensorCacheEntry() {
        return new SensorCacheEntry(cultivationId, deviceEui, deviceName, location,
                locationDetail, deviceModel, measurementTypes);
    }

    private static void requireText(String value, String fieldName) {
        if( value == null || value.isBlank()) {
            throw new SensorSynchronizationException(fieldName + "은 null이거나 공백일 수 없습니다.");
        }
    }
}

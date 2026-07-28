package site.yesaido.data_generator.domain;

import java.util.Set;

public record SensorCacheEntry (
    long cultivationId,
    String deviceEui,
    String deviceName,
    String location,
    String locationDetail,
    String deviceModel,
    Set<MeasurementType> measurementTypes
){
    public SensorCacheEntry {
        if (cultivationId <= 0) {
            throw new IllegalArgumentException("cultivationId는 0보다 커야 합니다.");
        }
        requireText(deviceEui, "deviceEui");
        requireText(deviceName, "deviceName");
        requireText(location, "location");
        requireText(locationDetail, "locationDetail");
        requireText(deviceModel, "deviceModel");

        if (measurementTypes == null || measurementTypes.isEmpty()) {
            throw new IllegalArgumentException("측정 유형은 null이거나 비어 있지 않아야 합니다");
        }
        measurementTypes = Set.copyOf(measurementTypes);
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " null 또는 공백이 없어야 합니다");
        }
    }


}


package site.yesaido.data_generator.domain;

import site.yesaido.data_generator.exception.SensorCacheException;

import java.util.Set;

// 센서 장치 한 대의 정보와 지원하는 타입·단위별 측정 채널을 저장하는 캐시 모델
public record SensorCacheEntry (
    long cultivationId,
    String deviceEui,
    String deviceName,
    String location,
    String locationDetail,
    String deviceModel,
    Set<SensorTypeSpec> sensorTypes
){
    public SensorCacheEntry {
        if (cultivationId <= 0) {
            throw new SensorCacheException("cultivationId는 0보다 커야 합니다.");
        }
        deviceEui = normalizeRequiredText(deviceEui, "deviceEui");
        deviceName = normalizeRequiredText(deviceName, "deviceName");
        location = normalizeRequiredText(location, "location");
        locationDetail = normalizeRequiredText(locationDetail, "locationDetail");
        deviceModel = normalizeRequiredText(deviceModel, "deviceModel");

        if (sensorTypes == null || sensorTypes.isEmpty()) {
            throw new SensorCacheException("sensorTypes은 null이거나 비어 있지 않아야 합니다");
        }

        for ( SensorTypeSpec sensorTypeSpec : sensorTypes) {
            if( sensorTypeSpec == null ) {
                throw new SensorCacheException("sensorTypes에 null이 포함될 수 없습니다.");
            }
        }
        sensorTypes = Set.copyOf(sensorTypes);
    }

    private static String normalizeRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new SensorCacheException(fieldName + "은 null이거나 빈 문자열 또는 공백 문자열일 수 없습니다");
        }

        return value.strip();
    }


}


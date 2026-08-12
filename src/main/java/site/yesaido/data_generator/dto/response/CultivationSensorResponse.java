package site.yesaido.data_generator.dto.response;

import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.domain.SensorTypeSpec;
import site.yesaido.data_generator.exception.SensorSynchronizationException;

import java.util.Set;
import java.util.stream.Collectors;

// Cultivation Server가 반환하는 센서 장치와 타입·단위 채널 목록을 표현하는 Feign 응답 DTO
public record CultivationSensorResponse (
        long cultivationId,
        String deviceEui,
        String deviceName,
        String location,
        String locationDetail,
        String deviceModel,
        Set<CultivationSensorTypeResponse> sensorTypes
){

    public CultivationSensorResponse {
        if (cultivationId <= 0) {
            throw new SensorSynchronizationException("cultivationId는 0보다 커야 합니다.");
        }
        deviceEui = normalizeRequiredText(deviceEui, "deviceEui");
        deviceName = normalizeRequiredText(deviceName, "deviceName");
        location = normalizeRequiredText(location, "location");
        locationDetail = normalizeRequiredText(locationDetail, "locationDetail");
        deviceModel = normalizeRequiredText(deviceModel, "deviceModel");

        if (sensorTypes == null || sensorTypes.isEmpty()) {
            throw new SensorSynchronizationException("sensorTypes은 null이거나 비어 있지 않아야 합니다");
        }

        for ( CultivationSensorTypeResponse sensorTypeResponse : sensorTypes) {
            if( sensorTypeResponse == null ) {
                throw new SensorSynchronizationException("sensorTypes에 null이 포함될 수 없습니다.");
            }
        }
        sensorTypes = Set.copyOf(sensorTypes);

    }

    public SensorCacheEntry convertToSensorCacheEntry() {
       Set<SensorTypeSpec> sensorTypeSpecs = sensorTypes.stream()
               .map(CultivationSensorTypeResponse::convertToSensorTypeSpec)
               .collect(Collectors.toUnmodifiableSet());

       return new SensorCacheEntry(cultivationId, deviceEui, deviceName, location, locationDetail, deviceModel, sensorTypeSpecs);
    }

    private static String normalizeRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new SensorSynchronizationException(fieldName + "은 null이거나 빈 문자열 또는 공백 문자열일 수 없습니다");
        }

        return value.strip();
    }
}

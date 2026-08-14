package site.yesaido.data_generator.dto.response;

import site.yesaido.data_generator.exception.SensorSynchronizationException;

import java.time.OffsetDateTime;
import java.util.List;

// Cultivation Server가 반환하는 센서와 임계값 전체 snapshot을 표현하는 Feign 응답 DTO
public record CultivationSnapshotResponse(
        OffsetDateTime snapshotAt,
        List<CultivationSensorResponse> sensors,
        List<CultivationThresholdResponse> thresholds
) {

    public CultivationSnapshotResponse {
        if (snapshotAt == null) {
            throw new SensorSynchronizationException("snapshotAt은 null일 수 없습니다.");
        }

        if (sensors == null) {
            throw new SensorSynchronizationException("sensors는 null일 수 없습니다.");
        }

        for (CultivationSensorResponse sensor : sensors) {
            if (sensor == null) {
                throw new SensorSynchronizationException("sensors에 null이 포함될 수 없습니다.");
            }
        }

        if (thresholds == null) {
            throw new SensorSynchronizationException("thresholds는 null일 수 없습니다.");
        }

        for (CultivationThresholdResponse threshold : thresholds) {
            if (threshold == null) {
                throw new SensorSynchronizationException("thresholds에 null이 포함될 수 없습니다.");
            }
        }

        sensors = List.copyOf(sensors);
        thresholds = List.copyOf(thresholds);
    }
}

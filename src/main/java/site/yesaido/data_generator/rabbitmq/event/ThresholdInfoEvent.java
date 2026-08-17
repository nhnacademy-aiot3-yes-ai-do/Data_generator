package site.yesaido.data_generator.rabbitmq.event;

import site.yesaido.data_generator.exception.SensorSynchronizationException;

import java.time.OffsetDateTime;
import java.util.List;

// Cultivation Server가 전달하는 재배별 센서 임계값 변경 이벤트
public record ThresholdInfoEvent(
        Long cultivationId,
        List<SensorRange> sensorRangeList,
        OffsetDateTime occurredAt
) {

    public ThresholdInfoEvent {
        if (cultivationId == null || cultivationId <= 0) {
            throw new SensorSynchronizationException("cultivationId는 null일 수 없고 0보다 커야 합니다.");
        }

        if (sensorRangeList == null) {
            throw new SensorSynchronizationException("sensorRangeList는 null일 수 없습니다.");
        }

        for (SensorRange sensorRange : sensorRangeList) {
            if (sensorRange == null) {
                throw new SensorSynchronizationException("sensorRangeList에 null이 포함될 수 없습니다.");
            }
        }

        sensorRangeList = List.copyOf(sensorRangeList);

        if (occurredAt == null) {
            throw new SensorSynchronizationException("occurredAt은 null일 수 없습니다.");
        }
    }
}

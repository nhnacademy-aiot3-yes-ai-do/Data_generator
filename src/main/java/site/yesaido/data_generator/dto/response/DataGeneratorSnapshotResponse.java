package site.yesaido.data_generator.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record DataGeneratorSnapshotResponse(
        OffsetDateTime snapshotAt,
        List<DataGeneratorSensorResponse> sensors,
        List<DataGeneratorThresholdResponse> thresholds
) {
}

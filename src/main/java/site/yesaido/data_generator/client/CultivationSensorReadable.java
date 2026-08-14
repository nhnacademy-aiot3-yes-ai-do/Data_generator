package site.yesaido.data_generator.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import site.yesaido.data_generator.dto.response.CultivationSnapshotResponse;

@FeignClient(name = "cultivation-server")
public interface CultivationSensorReadable {

    // Data Generator 초기화에 필요한 센서와 임계값 전체 snapshot을 조회합니다.
    @GetMapping("/internal/data-generator/snapshot")
    CultivationSnapshotResponse getCultivationSnapshot();
}

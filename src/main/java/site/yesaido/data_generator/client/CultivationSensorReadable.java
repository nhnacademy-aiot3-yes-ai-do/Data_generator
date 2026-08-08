package site.yesaido.data_generator.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import site.yesaido.data_generator.dto.response.CultivationSensorResponse;

import java.util.List;

@FeignClient(name = "cultivation-server")
public interface CultivationSensorReadable {
    @GetMapping("/api/v1/cultivations/sensors")
    List<CultivationSensorResponse> getCultivationSensors();
}

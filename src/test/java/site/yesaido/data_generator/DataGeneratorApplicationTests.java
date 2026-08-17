package site.yesaido.data_generator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import site.yesaido.data_generator.mqtt.PahoMqttPublisher;
import site.yesaido.data_generator.service.CultivationTaskCoordinator;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false"
})
@ActiveProfiles("local")
class DataGeneratorApplicationTests {

    // 스켈레톤 테스트에서는 실제 MQTT Broker 연결을 시도하지 않습니다.
    @MockitoBean
    private PahoMqttPublisher pahoMqttPublisher;

    // 로컬 fixture를 유지하되 스케줄러가 비동기 생성 작업을 시작하지 않게 합니다.
    @MockitoBean
    private CultivationTaskCoordinator cultivationTaskCoordinator;

    @Test
    void contextLoads() {
        // ApplicationContext가 예외 없이 로드되는 것 자체가 이 테스트의 검증입니다.
    }

}

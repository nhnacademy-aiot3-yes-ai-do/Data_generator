package site.yesaido.data_generator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import site.yesaido.data_generator.mqtt.PahoMqttPublisher;

@SpringBootTest
@ActiveProfiles("local")
class DataGeneratorApplicationTests {

    // 스켈레톤 테스트에서는 실제 MQTT Broker 연결을 시도하지 않습니다.
    @MockitoBean
    private PahoMqttPublisher pahoMqttPublisher;

    @Test
    void contextLoads() {
    }

}

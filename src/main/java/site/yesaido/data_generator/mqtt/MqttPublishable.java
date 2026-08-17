package site.yesaido.data_generator.mqtt;

import java.util.concurrent.CompletionStage;

//함수형 인터페이스 선언
@FunctionalInterface
public interface MqttPublishable {
    CompletionStage<Void> publishMessage(String topic, byte[] payload);
}

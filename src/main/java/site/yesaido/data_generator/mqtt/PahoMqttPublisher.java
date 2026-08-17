package site.yesaido.data_generator.mqtt;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import site.yesaido.data_generator.config.MqttProperties;
import site.yesaido.data_generator.exception.InvalidMqttPayloadException;
import site.yesaido.data_generator.exception.InvalidMqttTopicException;
import site.yesaido.data_generator.exception.MqttOperationException;


import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@RequiredArgsConstructor
@Component
@Slf4j
public class PahoMqttPublisher implements  MqttPublishable, MqttCallbackExtended {

    private static final long DISCONNECT_QUIESCE_TIMEOUT_MILLISECONDS = 5_000L;
    private final MqttProperties mqttProperties;
    private volatile MqttAsyncClient mqttClient;

    @PostConstruct
    public void connectClient(){
        MqttAsyncClient createdClient = null;

        try{
            createdClient = new MqttAsyncClient(
                    mqttProperties.getBrokerUrl(),
                    mqttProperties.getClientId(),
                    new MemoryPersistence()
            );
            createdClient.setCallback(this);

            IMqttToken connectToken = createdClient.connect(createConnectOptions());

            connectToken.waitForCompletion();
            mqttClient = createdClient;

            log.info("MQTT broker 연결 완료. brokerUrl={}, clientId={}", mqttProperties.getBrokerUrl(), mqttProperties.getClientId());
        }catch (MqttException exception){
            closeClientAfterConnectionFailure(createdClient);
            throw new MqttOperationException("MQTT broker 초기 연결에 실패했습니다. brokerUrl=" + mqttProperties.getBrokerUrl(), exception);
        }
    }

    @Override
    public CompletionStage<Void> publishMessage(String topic, byte[] payload){
        validatePublishArguments(topic,payload);
        MqttAsyncClient activeClient = mqttClient;

        if(activeClient == null || !activeClient.isConnected()){
            MqttOperationException operationException =
                    new MqttOperationException("MQTT client가 연결되지 않았습니다. topic=" + topic);

            log.error("MQTT 메시지를 발행할 수 없습니다. 연결되지 않은 상태입니다. topic={}",topic);
            return CompletableFuture.failedFuture(operationException);
        }
        MqttMessage mqttMessage = new MqttMessage(Arrays.copyOf(payload, payload.length));

        mqttMessage.setQos(mqttProperties.getQos());
        mqttMessage.setRetained(mqttProperties.isRetained());

        CompletableFuture<Void> publishResult = new CompletableFuture<>();

        try{
            activeClient.publish(topic,mqttMessage, null, createPublishActionListener(topic, publishResult));
        }catch (MqttException exception){
            MqttOperationException operationException = new MqttOperationException("MQTT 메시지 발행에 실패했습니다. topic=" + topic, exception);
            log.error("MQTT 메시지 발행에 실패했습니다. topic={}", topic, exception);
            publishResult.completeExceptionally(operationException);
        }

        return publishResult;
    }


    @PreDestroy
    public void disconnectClient() {
        MqttAsyncClient activeClient = mqttClient;
        mqttClient = null;

        if (activeClient == null) {
            return;
        }

        try {
            if (activeClient.isConnected()) {
                IMqttToken disconnectToken = activeClient.disconnect(DISCONNECT_QUIESCE_TIMEOUT_MILLISECONDS);
                disconnectToken.waitForCompletion(DISCONNECT_QUIESCE_TIMEOUT_MILLISECONDS);
            }
        } catch (MqttException exception) {
            log.warn("MQTT client 연결 종료 중 오류가 발생했습니다.", exception);
        }

        try {
            activeClient.close();
            log.info("MQTT client가 종료되었습니다.");
        } catch (MqttException exception) {
            log.warn("MQTT client 리소스 해제 중 오류가 발생했습니다.", exception);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT broker 연결이 끊겼습니다. clientId={}", mqttProperties.getClientId(), cause);
    }

    @Override
    public void connectComplete(boolean reconnect, String serverUri) {
        if (reconnect) {
            log.info("MQTT broker 재연결 완료. serverUri={}, clientId={}", serverUri, mqttProperties.getClientId());
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) {
        log.warn("발행 전용 MQTT client가 메시지를 수신했습니다. topic={}, payloadSize={}", topic, mqttMessage.getPayload().length);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken deliveryToken) {
        log.debug("MQTT 메시지 전달 완료. messageId={}", deliveryToken.getMessageId());
    }

    private IMqttActionListener createPublishActionListener(String topic, CompletableFuture<Void> publishResult) {
        return new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken actionToken) {
                log.debug("MQTT 비동기 발행 완료. topic={}, messageID={}", topic, actionToken.getMessageId());

                publishResult.complete(null);
            }

            @Override
            public void onFailure(IMqttToken actionToken, Throwable cause) {
                MqttOperationException operationException = new MqttOperationException("MQTT 비동기 발행에 실패 했습니다. topic=" + topic, cause);
                log.error("MQTT 비동기 발행에 실패 했습니다. topic={}",topic, cause);
                publishResult.completeExceptionally(operationException);
            }
        };
    }


    private MqttConnectOptions createConnectOptions() {
        MqttConnectOptions connectOptions = new MqttConnectOptions();

        connectOptions.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        connectOptions.setAutomaticReconnect(mqttProperties.isAutomaticReconnect());
        connectOptions.setMaxReconnectDelay(mqttProperties.getMaximumReconnectDelayMilliseconds());
        connectOptions.setConnectionTimeout(mqttProperties.getConnectionTimeoutSeconds());
        connectOptions.setKeepAliveInterval(mqttProperties.getKeepAliveSeconds());
        connectOptions.setMaxInflight(mqttProperties.getMaxInflight());
        connectOptions.setCleanSession(mqttProperties.isCleanSession());

        String username = mqttProperties.getUsername();

        if(StringUtils.hasText(username)){
            connectOptions.setUserName(username);
        }

        String password = mqttProperties.getPassword();

        if (StringUtils.hasText(password)) {
            connectOptions.setPassword(password.toCharArray());
        }

        return connectOptions;
    }


    private void validatePublishArguments(String topic, byte[] payload){
        if(topic == null || topic.isBlank()){
            throw new InvalidMqttTopicException("발행 topic은 null이거나 공백일 수 없습니다.");
        }
        if(topic.contains("+") || topic.contains("#")){
            throw new InvalidMqttTopicException("발행 topic에는 MQTT 와일드카드 '+', '#'를 포함할 수 없습니다.");
        }
        if(payload == null || payload.length == 0){
            throw new InvalidMqttPayloadException("발행 payload는 null이거나 비어 있을 수 없습니다.");
        }

    }



    private void closeClientAfterConnectionFailure(MqttAsyncClient createdClient){
        if(createdClient == null){
            return;
        }

        try{
            createdClient.close(true);
        }catch (MqttException exception){
            log.warn("초기 연결 실패 후 MQTT client 리소스 해제 중 오류가 발생했습니다.", exception);
        }
    }



}

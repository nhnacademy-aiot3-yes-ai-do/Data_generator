package site.yesaido.data_generator.rabbitmq;

// RabbitMQ 토폴로지, 이벤트 TypeId, Listener 식별자를 관리합니다.
public final class RabbitMqConstants {

    // 처리 재시도가 모두 실패한 메시지를 전달하는 Dead Letter Exchange입니다.
    public static final String DEAD_LETTER_EXCHANGE =
            "yes-nhn.dlx";

    // 최대 재시도 후에도 처리하지 못한 메시지를 보관하는 공용 실패 큐입니다.
    public static final String DEAD_LETTER_QUEUE =
            "yes-nhn.dlq";

    public static final String DEAD_LETTER_EXCHANGE_ARGUMENT =
            "x-dead-letter-exchange";

    // Cultivation Server의 센서와 임계값 이벤트가 발행되는 Topic Exchange입니다.
    public static final String SENSOR_EXCHANGE =
            "yes-nhn.sensor.exchange";

    // 센서 채널 등록·수정·삭제 이벤트를 받아 SensorCache에 반영하는 큐입니다.
    public static final String SENSOR_INFO_QUEUE =
            "yes-nhn.data-source.sensor-info.queue";

    // 임계값 등록·수정·전체 삭제 이벤트를 받아 SensorThresholdCache에 반영하는 큐입니다.
    public static final String THRESHOLD_INFO_QUEUE =
            "yes-nhn.data-source.threshold-info.queue";

    // 센서 정보 이벤트의 producer별 routing key를 모두 수신하는 패턴입니다.
    public static final String SENSOR_INFO_BINDING_KEY_PATTERN =
            "yes-nhn.#.sensor-info.queue";

    // 임계값 정보 이벤트의 producer별 routing key를 모두 수신하는 패턴입니다.
    public static final String THRESHOLD_INFO_BINDING_KEY_PATTERN =
            "yes-nhn.#.threshold-info.queue";

    public static final String SENSOR_UPSERT_TYPE_ID =
            "sensor.upsert";

    public static final String SENSOR_DELETE_TYPE_ID =
            "sensor.delete";

    public static final String THRESHOLD_CRUD_TYPE_ID =
            "threshold.crud";

    public static final String SENSOR_INFO_LISTENER_ID =
            "sensorInfoEventListener";

    public static final String THRESHOLD_INFO_LISTENER_ID =
            "thresholdInfoEventListener";

    private RabbitMqConstants() {
    }
}

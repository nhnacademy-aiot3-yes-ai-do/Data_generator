package site.yesaido.data_generator.generator;

import site.yesaido.data_generator.domain.SensorChannelKey;

// String sensorType별 측정값 생성기를 Registry에서 조회하기 위한 공통 인터페이스
public interface SensorValueGenerator {

    // 이 생성기가 처리할 String 센서 타입 코드를 반환
    String supportedSensorType();
    // 정확한 센서 채널의 다음 측정값을 표준 단위 기준으로 생성
    Number generateNextValue(SensorChannelKey sensorChannelKey, double actuatorEffectAmount);
    // 다른 채널에는 영향을 주지 않고 지정한 채널의 이전 상태만 제거
    void removeState(SensorChannelKey sensorChannelKey);
}

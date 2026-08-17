package site.yesaido.data_generator.generator;

import site.yesaido.data_generator.domain.MeasurementConfiguration;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

// 타입별 생성기가 공통으로 사용하는 sensorType 검증과 Random Walk 위임 로직
public abstract class AbstractRandomWalkSensorValueGenerator implements SensorValueGenerator {

    private final String supportedSensorType;
    private final MeasurementConfiguration measurementConfiguration;
    private final RandomWalkGenerator randomWalkGenerator;

    protected AbstractRandomWalkSensorValueGenerator(String supportedSensorType,
            MeasurementConfiguration measurementConfiguration, RandomWalkGenerator randomWalkGenerator) {
        this.supportedSensorType = normalizeRequiredText(supportedSensorType);

        if (measurementConfiguration == null) {
            throw new SensorDataGenerationException("measurementConfiguration은 null일 수 없습니다.");
        }

        if (randomWalkGenerator == null) {
            throw new SensorDataGenerationException("randomWalkGenerator는 null일 수 없습니다.");
        }

        this.measurementConfiguration = measurementConfiguration;
        this.randomWalkGenerator = randomWalkGenerator;
    }

    @Override
    public final String supportedSensorType() {
        return supportedSensorType;
    }

    @Override
    public final Number generateNextValue(SensorChannelKey sensorChannelKey, double actuatorEffectAmount) {
        validateSupportedSensorChannel(sensorChannelKey);

        return randomWalkGenerator.generateNextValue(sensorChannelKey,
                measurementConfiguration, actuatorEffectAmount);
    }

    @Override
    public final void removeState(SensorChannelKey sensorChannelKey) {
        validateSupportedSensorChannel(sensorChannelKey);

        randomWalkGenerator.removeState(sensorChannelKey);
    }

    private void validateSupportedSensorChannel(SensorChannelKey sensorChannelKey) {
        if (sensorChannelKey == null) {
            throw new SensorDataGenerationException("sensorChannelKey는 null일 수 없습니다.");
        }

        if (!supportedSensorType.equals(sensorChannelKey.sensorType())) {
            throw new SensorDataGenerationException(
                    "생성기가 지원하지 않는 sensorType입니다. supportedSensorType=" + supportedSensorType
                            + ", requestedSensorType=" + sensorChannelKey.sensorType());
        }
    }

    private static String normalizeRequiredText(String value) {
        if (value == null || value.isBlank()) {
            throw new SensorDataGenerationException("supportedSensorType" + "은 null이거나 빈 문자열 또는 공백 문자열일 수 없습니다.");
        }

        return value.strip();
    }
}

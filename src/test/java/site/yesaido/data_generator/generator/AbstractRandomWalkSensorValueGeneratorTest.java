package site.yesaido.data_generator.generator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.data_generator.domain.MeasurementConfiguration;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractRandomWalkSensorValueGeneratorTest {

    private static final MeasurementConfiguration CONFIGURATION =
            new MeasurementConfiguration(20.0, 10.0, 30.0, 0.5, 1);

    private TrackingRandomWalkGenerator randomWalkGenerator;

    private TestSensorValueGenerator generator;

    @BeforeEach
    void setUp() {
        randomWalkGenerator = new TrackingRandomWalkGenerator();
        generator = new TestSensorValueGenerator(
                "  TEMPERATURE  ", CONFIGURATION, randomWalkGenerator);
    }

    @Test
    @DisplayName("지원 센서 타입을 정규화한다")
    void normalizeSupportedSensorType() {
        assertThat(generator.supportedSensorType()).isEqualTo("TEMPERATURE");
    }

    @Test
    @DisplayName("필수 생성자 값이 null 또는 공백이면 생성기를 만들 수 없다")
    void rejectInvalidConstructorArguments() {
        assertThatThrownBy(() -> new TestSensorValueGenerator(
                null, CONFIGURATION, randomWalkGenerator))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("supportedSensorType");

        assertThatThrownBy(() -> new TestSensorValueGenerator(
                "   ", CONFIGURATION, randomWalkGenerator))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("supportedSensorType");

        assertThatThrownBy(() -> new TestSensorValueGenerator(
                "TEMPERATURE", null, randomWalkGenerator))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("measurementConfiguration");

        assertThatThrownBy(() -> new TestSensorValueGenerator(
                "TEMPERATURE", CONFIGURATION, null))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("randomWalkGenerator");
    }

    @Test
    @DisplayName("지원 센서 채널의 값 생성을 Random Walk 생성기에 위임한다")
    void delegateValueGeneration() {
        SensorChannelKey channelKey = new SensorChannelKey(
                "device-A", "TEMPERATURE", "°C");

        Number generatedValue = generator.generateNextValue(channelKey, 0.5);

        assertThat(generatedValue.doubleValue()).isEqualTo(20.5);
        assertThat(randomWalkGenerator.generatedChannelKey).isEqualTo(channelKey);
        assertThat(randomWalkGenerator.measurementConfiguration).isEqualTo(CONFIGURATION);
        assertThat(randomWalkGenerator.actuatorEffectAmount).isEqualTo(0.5);
    }

    @Test
    @DisplayName("지원 센서 채널 상태 제거를 Random Walk 생성기에 위임한다")
    void delegateStateRemoval() {
        SensorChannelKey channelKey = new SensorChannelKey(
                "device-A", "TEMPERATURE", "°C");

        generator.removeState(channelKey);

        assertThat(randomWalkGenerator.removedChannelKey).isEqualTo(channelKey);
    }

    @Test
    @DisplayName("null 또는 다른 타입의 센서 채널은 거절한다")
    void rejectUnsupportedSensorChannel() {
        SensorChannelKey unsupportedChannelKey = new SensorChannelKey(
                "device-A", "HUMIDITY", "%RH");

        assertThatThrownBy(() -> generator.generateNextValue(null, 0.0))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("sensorChannelKey");

        assertThatThrownBy(() -> generator.generateNextValue(
                unsupportedChannelKey, 0.0))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("지원하지 않는 sensorType");

        assertThatThrownBy(() -> generator.removeState(null))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("sensorChannelKey");

        assertThatThrownBy(() -> generator.removeState(unsupportedChannelKey))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("지원하지 않는 sensorType");

        assertThat(randomWalkGenerator.generatedChannelKey).isNull();
        assertThat(randomWalkGenerator.removedChannelKey).isNull();
    }

    private static final class TestSensorValueGenerator
            extends AbstractRandomWalkSensorValueGenerator {

        private TestSensorValueGenerator(
                String supportedSensorType,
                MeasurementConfiguration measurementConfiguration,
                RandomWalkGenerator randomWalkGenerator
        ) {
            super(supportedSensorType, measurementConfiguration, randomWalkGenerator);
        }
    }

    private static final class TrackingRandomWalkGenerator extends RandomWalkGenerator {

        private SensorChannelKey generatedChannelKey;
        private MeasurementConfiguration measurementConfiguration;
        private double actuatorEffectAmount;
        private SensorChannelKey removedChannelKey;

        private TrackingRandomWalkGenerator() {
            super(new Random(0L));
        }

        @Override
        public Number generateNextValue(
                SensorChannelKey sensorChannelKey,
                MeasurementConfiguration configuration,
                double effectAmount
        ) {
            generatedChannelKey = sensorChannelKey;
            measurementConfiguration = configuration;
            actuatorEffectAmount = effectAmount;
            return 20.5;
        }

        @Override
        public void removeState(SensorChannelKey sensorChannelKey) {
            removedChannelKey = sensorChannelKey;
        }
    }
}

package site.yesaido.data_generator.generator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.data_generator.domain.DynamicSensorGenerationPolicy;
import site.yesaido.data_generator.domain.MeasurementConfiguration;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.domain.SensorThresholdRange;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.math.BigDecimal;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThresholdBasedSensorValueGeneratorTest {

    private static final DynamicSensorGenerationPolicy POLICY =
            new DynamicSensorGenerationPolicy(
                    new BigDecimal("0.2"), new BigDecimal("0.02"), 2);

    private TrackingRandomWalkGenerator randomWalkGenerator;
    private DynamicSensorConfigurationFactory configurationFactory;

    private ThresholdBasedSensorValueGenerator generator;

    @BeforeEach
    void setUp() {
        randomWalkGenerator = new TrackingRandomWalkGenerator();
        configurationFactory = new DynamicSensorConfigurationFactory();
        generator = new ThresholdBasedSensorValueGenerator(
                randomWalkGenerator, configurationFactory, POLICY);
    }

    @Test
    @DisplayName("필수 의존성이 null이면 임계값 기반 생성기를 만들 수 없다")
    void rejectNullDependencies() {
        assertThatThrownBy(() -> new ThresholdBasedSensorValueGenerator(
                null, configurationFactory, POLICY))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("randomWalkGenerator");

        assertThatThrownBy(() -> new ThresholdBasedSensorValueGenerator(
                randomWalkGenerator, null, POLICY))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("dynamicSensorConfigurationFactory");

        assertThatThrownBy(() -> new ThresholdBasedSensorValueGenerator(
                randomWalkGenerator, configurationFactory, null))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("dynamicSensorGenerationPolicy");
    }

    @Test
    @DisplayName("임계값을 측정 설정으로 변환해 액추에이터 효과 없이 값을 생성한다")
    void generateValueFromThresholdConfiguration() {
        SensorChannelKey channelKey = new SensorChannelKey(
                "device-A", "SOIL_MOISTURE", "%");
        SensorThresholdRange thresholdRange = new SensorThresholdRange(
                new BigDecimal("30"), new BigDecimal("70"));
        Number generatedValue = generator.generateNextValue(channelKey, thresholdRange);

        assertThat(generatedValue.doubleValue()).isEqualTo(50.5);
        assertThat(randomWalkGenerator.generatedChannelKey).isEqualTo(channelKey);
        assertThat(randomWalkGenerator.actuatorEffectAmount).isZero();
        assertThat(randomWalkGenerator.measurementConfiguration)
                .isEqualTo(new MeasurementConfiguration(50.0, 22.0, 78.0, 0.8, 2));
    }

    @Test
    @DisplayName("동적 센서 채널 상태 제거를 Random Walk 생성기에 위임한다")
    void removeDynamicSensorState() {
        SensorChannelKey channelKey = new SensorChannelKey(
                "device-A", "SOIL_MOISTURE", "%");

        generator.removeState(channelKey);

        assertThat(randomWalkGenerator.removedChannelKey).isEqualTo(channelKey);
    }

    @Test
    @DisplayName("생성 입력이 null이면 의존 객체 호출 전에 거절한다")
    void rejectNullGenerationInputs() {
        SensorChannelKey channelKey = new SensorChannelKey(
                "device-A", "SOIL_MOISTURE", "%");
        SensorThresholdRange thresholdRange = new SensorThresholdRange(
                new BigDecimal("30"), new BigDecimal("70"));

        assertThatThrownBy(() -> generator.generateNextValue(null, thresholdRange))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("sensorChannelKey");

        assertThatThrownBy(() -> generator.generateNextValue(channelKey, null))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("thresholdRange");

        assertThatThrownBy(() -> generator.removeState(null))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("sensorChannelKey");

        assertThat(randomWalkGenerator.generatedChannelKey).isNull();
        assertThat(randomWalkGenerator.removedChannelKey).isNull();
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
            return 50.5;
        }

        @Override
        public void removeState(SensorChannelKey sensorChannelKey) {
            removedChannelKey = sensorChannelKey;
        }
    }
}

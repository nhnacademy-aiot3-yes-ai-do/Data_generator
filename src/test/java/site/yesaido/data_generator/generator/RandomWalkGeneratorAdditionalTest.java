package site.yesaido.data_generator.generator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import site.yesaido.data_generator.domain.MeasurementConfiguration;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.util.Random;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RandomWalkGeneratorAdditionalTest {

    private RandomGenerator randomGenerator;
    private RandomWalkGenerator randomWalkGenerator;

    @BeforeEach
    void setUp() {
        randomGenerator = new Random(0L);
        randomWalkGenerator = new RandomWalkGenerator(randomGenerator);
    }

    @Test
    @DisplayName("난수 생성기가 null이면 Random Walk 생성기를 만들 수 없다")
    void rejectNullRandomGenerator() {
        assertThatThrownBy(() -> new RandomWalkGenerator(null))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("randomGenerator");
    }

    @Test
    @DisplayName("소수점 자릿수가 0이면 생성값을 정수형으로 반환한다")
    void returnLongWhenDecimalPlacesIsZero() {
        SensorChannelKey channelKey = new SensorChannelKey(
                "device-A", "CO2", "ppm");
        MeasurementConfiguration configuration = new MeasurementConfiguration(
                10.6, 0.0, 20.0, 0.0, 0);

        Number generatedValue = randomWalkGenerator.generateNextValue(
                channelKey, configuration, 0.0);

        assertThat(generatedValue).isInstanceOf(Long.class).isEqualTo(11L);
    }

    @Test
    @DisplayName("장치 상태 제거는 해당 장치의 모든 채널만 초기화한다")
    void removeAllStatesForOnlyRequestedDevice() {
        MeasurementConfiguration configuration = new MeasurementConfiguration(
                50.0, 0.0, 100.0, 0.0, 1);
        SensorChannelKey deviceATemperature = new SensorChannelKey(
                "device-A", "TEMPERATURE", "°C");
        SensorChannelKey deviceAHumidity = new SensorChannelKey(
                "device-A", "HUMIDITY", "%RH");
        SensorChannelKey deviceBTemperature = new SensorChannelKey(
                "device-B", "TEMPERATURE", "°C");

        assertThat(randomWalkGenerator.generateNextValue(
                deviceATemperature, configuration, 2.0).doubleValue()).isEqualTo(52.0);
        assertThat(randomWalkGenerator.generateNextValue(
                deviceAHumidity, configuration, 4.0).doubleValue()).isEqualTo(54.0);
        assertThat(randomWalkGenerator.generateNextValue(
                deviceBTemperature, configuration, 3.0).doubleValue()).isEqualTo(53.0);

        randomWalkGenerator.removeStatesByDeviceEui("device-A");

        assertThat(randomWalkGenerator.generateNextValue(
                deviceATemperature, configuration, 0.0).doubleValue()).isEqualTo(50.0);
        assertThat(randomWalkGenerator.generateNextValue(
                deviceAHumidity, configuration, 0.0).doubleValue()).isEqualTo(50.0);
        assertThat(randomWalkGenerator.generateNextValue(
                deviceBTemperature, configuration, 0.0).doubleValue()).isEqualTo(53.0);
    }

    @Test
    @DisplayName("센서 채널 또는 측정 설정이 null이면 생성 요청을 거절한다")
    void rejectNullGenerationArguments() {
        SensorChannelKey channelKey = new SensorChannelKey(
                "device-A", "TEMPERATURE", "°C");
        MeasurementConfiguration configuration = new MeasurementConfiguration(
                20.0, 10.0, 30.0, 0.5, 1);

        assertThatThrownBy(() -> randomWalkGenerator.generateNextValue(
                null, configuration, 0.0))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("sensorChannelKey");

        assertThatThrownBy(() -> randomWalkGenerator.generateNextValue(
                channelKey, null, 0.0))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("measurementConfiguration");

        assertThatThrownBy(() -> randomWalkGenerator.removeState(null))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("sensorChannelKey");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("상태를 제거할 장치 EUI가 null 또는 공백이면 예외가 발생한다")
    void rejectMissingDeviceEui(String deviceEui) {
        assertThatThrownBy(() -> randomWalkGenerator.removeStatesByDeviceEui(deviceEui))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("deviceEui");
    }
}

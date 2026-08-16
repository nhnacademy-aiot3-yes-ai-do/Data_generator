package site.yesaido.data_generator.generator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.data_generator.domain.MeasurementConfiguration;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.util.random.RandomGenerator;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;


class RandomWalkGeneratorTest {
    private RandomGenerator randomGenerator;
    private RandomWalkGenerator randomWalkGenerator;

    @BeforeEach
    void setUp() {
        randomGenerator = mock(RandomGenerator.class);
        randomWalkGenerator = new RandomWalkGenerator(randomGenerator);
    }

    @Test
    @DisplayName("최대 변화량이 0이면 난수 생성 없이 고정값을 유지한다")
    void keepFixedValueWithoutRandomChange() {
        SensorChannelKey sensorChannelKey = new SensorChannelKey("device-A", "CUSTOM_SENSOR", "unit");

        MeasurementConfiguration measurementConfiguration =
                new MeasurementConfiguration(
                        15.0,
                        15.0,
                        15.0,
                        0.0,
                        2
                );

        Number firstValue = randomWalkGenerator.generateNextValue(
                        sensorChannelKey, measurementConfiguration,0.0);

        Number secondValue = randomWalkGenerator.generateNextValue(
                sensorChannelKey, measurementConfiguration, 0.0);

        assertThat(firstValue.doubleValue()).isEqualTo(15.0);
        assertThat(secondValue.doubleValue()).isEqualTo(15.0);

        verifyNoInteractions(randomGenerator);
    }

    @Test
    @DisplayName("같은 장치와 센서 타입이라도 단위가 다르면 상태를 독립적으로 관리한다")
    void isolateStateByUnit() {
        SensorChannelKey celsiusChannelKey =
                new SensorChannelKey("device-A", "TEMPERATURE", "°C");

        SensorChannelKey fahrenheitChannelKey =
                new SensorChannelKey("device-A", "TEMPERATURE", "°F");

        MeasurementConfiguration measurementConfiguration = createMovableConfiguration();

        when(randomGenerator.nextDouble(-1.0, 1.0)).thenReturn(0.5, -0.5);

        Number firstCelsiusValue = randomWalkGenerator.generateNextValue(
                        celsiusChannelKey, measurementConfiguration, 0.0);

        Number firstFahrenheitValue = randomWalkGenerator.generateNextValue(
                        fahrenheitChannelKey, measurementConfiguration, 0.0);

        Number secondCelsiusValue = randomWalkGenerator.generateNextValue(
                        celsiusChannelKey, measurementConfiguration, 0.0);

        Number secondFahrenheitValue = randomWalkGenerator.generateNextValue(
                        fahrenheitChannelKey, measurementConfiguration, 0.0);

        assertThat(firstCelsiusValue.doubleValue()).isEqualTo(50.0);
        assertThat(firstFahrenheitValue.doubleValue()).isEqualTo(50.0);
        assertThat(secondCelsiusValue.doubleValue()).isEqualTo(50.5);
        assertThat(secondFahrenheitValue.doubleValue()).isEqualTo(49.5);

        verify(randomGenerator, times(2)).nextDouble(-1.0, 1.0);
    }

    @Test
    @DisplayName("정확한 채널 하나만 삭제하고 다른 단위 채널의 상태는 유지한다")
    void removeOnlyExactChannelState() {
        SensorChannelKey celsiusChannelKey =
                new SensorChannelKey("device-A", "TEMPERATURE", "°C");

        SensorChannelKey fahrenheitChannelKey =
                new SensorChannelKey("device-A", "TEMPERATURE", "°F");

        MeasurementConfiguration measurementConfiguration = createMovableConfiguration();

        when(randomGenerator.nextDouble(-1.0, 1.0)).thenReturn(0.5, -0.5, 0.25);

        randomWalkGenerator.generateNextValue(
                celsiusChannelKey, measurementConfiguration, 0.0);
        randomWalkGenerator.generateNextValue(
                fahrenheitChannelKey, measurementConfiguration, 0.0);

        Number secondCelsiusValue = randomWalkGenerator.generateNextValue(
                        celsiusChannelKey, measurementConfiguration, 0.0);

        Number secondFahrenheitValue = randomWalkGenerator.generateNextValue(
                        fahrenheitChannelKey, measurementConfiguration, 0.0);

        randomWalkGenerator.removeState(celsiusChannelKey);

        Number resetCelsiusValue = randomWalkGenerator.generateNextValue(
                        celsiusChannelKey, measurementConfiguration, 0.0);

        Number continuedFahrenheitValue = randomWalkGenerator.generateNextValue(
                        fahrenheitChannelKey, measurementConfiguration, 0.0);

        assertThat(secondCelsiusValue.doubleValue()).isEqualTo(50.5);
        assertThat(secondFahrenheitValue.doubleValue()).isEqualTo(49.5);
        assertThat(resetCelsiusValue.doubleValue()).isEqualTo(50.0);
        assertThat(continuedFahrenheitValue.doubleValue()).isEqualTo(49.75);

        verify(randomGenerator, times(3)).nextDouble(-1.0, 1.0);
    }

    @Test
    @DisplayName("생성값을 설정 범위로 제한하고 지정한 자릿수로 반올림한다")
    void clampAndRoundGeneratedValue() {
        SensorChannelKey sensorChannelKey =
                new SensorChannelKey("device-A", "CUSTOM_SENSOR", "unit");

        MeasurementConfiguration measurementConfiguration =
                new MeasurementConfiguration(
                        9.8,
                        0.0,
                        10.0,
                        1.0,
                        1
                );

        when(randomGenerator.nextDouble(-1.0, 1.0)).thenReturn(0.37);

        Number firstValue = randomWalkGenerator.generateNextValue(
                        sensorChannelKey, measurementConfiguration, 0.0);

        Number secondValue = randomWalkGenerator.generateNextValue(
                        sensorChannelKey, measurementConfiguration, 0.0);

        assertThat(firstValue.doubleValue()).isEqualTo(9.8);
        assertThat(secondValue.doubleValue()).isEqualTo(10.0);

        verify(randomGenerator).nextDouble(-1.0, 1.0);
    }

    @Test
    @DisplayName("Random Walk 값에 액추에이터 효과를 적용한다")
    void applyActuatorEffectAmount() {
        SensorChannelKey sensorChannelKey =
                new SensorChannelKey("device-A", "CUSTOM_SENSOR", "unit");

        MeasurementConfiguration measurementConfiguration = createMovableConfiguration();

        when(randomGenerator.nextDouble(-1.0, 1.0)).thenReturn(0.5);

        Number firstValue = randomWalkGenerator.generateNextValue(
                        sensorChannelKey, measurementConfiguration, 2.0);

        Number secondValue = randomWalkGenerator.generateNextValue(
                        sensorChannelKey, measurementConfiguration, 2.0);

        assertThat(firstValue.doubleValue()).isEqualTo(52.0);
        assertThat(secondValue.doubleValue()).isEqualTo(54.5);

        verify(randomGenerator).nextDouble(-1.0, 1.0);
    }

    @Test
    @DisplayName("액추에이터 효과가 NaN이면 예외가 발생한다")
    void throwExceptionWhenActuatorEffectIsNan() {
        SensorChannelKey sensorChannelKey =
                new SensorChannelKey("device-A", "CUSTOM_SENSOR", "unit");

        MeasurementConfiguration measurementConfiguration = createMovableConfiguration();

        assertThatThrownBy(() -> randomWalkGenerator.generateNextValue(
                        sensorChannelKey, measurementConfiguration, Double.NaN))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("actuatorEffectAmount는 유한한 숫자여야 합니다.");

        verifyNoInteractions(randomGenerator);
    }

    private static MeasurementConfiguration createMovableConfiguration() {
        return new MeasurementConfiguration(
                50.0,
                0.0,
                100.0,
                1.0,
                2
        );
    }

    @Test
    @DisplayName("양수와 음수의 절반값을 HALF_UP 규칙으로 반올림한다")
    void roundHalfValueUsingHalfUp() {
        SensorChannelKey positiveChannelKey = new SensorChannelKey(
                "device-positive",
                "CUSTOM_SENSOR",
                "unit"
        );

        SensorChannelKey negativeChannelKey = new SensorChannelKey(
                "device-negative",
                "CUSTOM_SENSOR",
                "unit"
        );

        MeasurementConfiguration positiveConfiguration = new MeasurementConfiguration(
                1.25,
                -10.0,
                10.0,
                0.0,
                1
        );

        MeasurementConfiguration negativeConfiguration =
                new MeasurementConfiguration(
                        -1.25,
                        -10.0,
                        10.0,
                        0.0,
                        1
                );

        Number positiveValue = randomWalkGenerator.generateNextValue(
                positiveChannelKey, positiveConfiguration, 0.0);

        Number negativeValue = randomWalkGenerator.generateNextValue(
                negativeChannelKey, negativeConfiguration, 0.0);

        assertThat(positiveValue.doubleValue()).isEqualTo(1.3);
        assertThat(negativeValue.doubleValue()).isEqualTo(-1.3);

        verifyNoInteractions(randomGenerator);
    }

    @Test
    @DisplayName("Double 최댓값을 반올림해도 다른 값으로 변경되지 않는다")
    void preserveLargeFiniteValueWhenRounding() {
        SensorChannelKey sensorChannelKey = new SensorChannelKey(
                "device-large-value",
                "CUSTOM_SENSOR",
                "unit"
        );

        MeasurementConfiguration measurementConfiguration = new MeasurementConfiguration(
                Double.MAX_VALUE,
                0.0,
                Double.MAX_VALUE,
                0.0,
                1
        );

        Number generatedValue = randomWalkGenerator.generateNextValue(
                sensorChannelKey, measurementConfiguration, 0.0);

        assertThat(generatedValue.doubleValue())
                .isFinite()
                .isEqualTo(Double.MAX_VALUE);

        verifyNoInteractions(randomGenerator);
    }

    @Test
    @DisplayName("지원하는 최대 정밀도를 초과하면 설정 생성을 거절한다")
    void rejectDecimalPlacesAboveMaximum() {
        assertThatThrownBy(() -> new MeasurementConfiguration(
                0.0,
                0.0,
                1.0,
                0.0,
                MeasurementConfiguration.MAX_DECIMAL_PLACES + 1)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decimalPlaces는 "
                        + MeasurementConfiguration.MAX_DECIMAL_PLACES + " 이하여야 합니다."
                );

        verifyNoInteractions(randomGenerator);
    }
}

package site.yesaido.data_generator.generator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.data_generator.cache.SensorThresholdCache;
import site.yesaido.data_generator.converter.SensorUnitConverter;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.domain.SensorThresholdKey;
import site.yesaido.data_generator.domain.SensorThresholdRange;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 고정 생성기와 동적 임계값 생성기의 선택 규칙을 검증합니다.
@ExtendWith(MockitoExtension.class)
class SensorValueGenerationResolverTest {

    @Mock
    private SensorValueGeneratorRegistry sensorValueGeneratorRegistry;

    @Mock
    private SensorThresholdCache sensorThresholdCache;

    @Mock
    private ThresholdBasedSensorValueGenerator thresholdBasedSensorValueGenerator;

    @Mock
    private SensorUnitConverter sensorUnitConverter;

    @Mock
    private SensorValueGenerator sensorValueGenerator;

    private SensorValueGenerationResolver sensorValueGenerationResolver;

    @BeforeEach
    void setUp() {
        sensorValueGenerationResolver = new SensorValueGenerationResolver(
                        sensorValueGeneratorRegistry,
                        sensorThresholdCache,
                        thresholdBasedSensorValueGenerator,
                        sensorUnitConverter
                );
    }

    @Test
    @DisplayName("고정 생성기를 우선 사용하고 생성값을 등록 단위로 변환한다")
    void useFixedGeneratorAndConvertGeneratedValue() {
        SensorChannelKey sensorChannelKey = new SensorChannelKey("device-A", "TEMPERATURE", "°F");

        when(sensorValueGeneratorRegistry.findBySensorType("TEMPERATURE")).thenReturn(Optional.of(sensorValueGenerator));
        when(sensorValueGenerator.generateNextValue(sensorChannelKey, 0.5)).thenReturn(20.0);
        when(sensorUnitConverter.convertFromCanonical("TEMPERATURE", "°F", 20.0)).thenReturn(Optional.of(68.0));

        Optional<Number> generatedValue = sensorValueGenerationResolver
                .generateNextValue(1L, sensorChannelKey, 0.5);

        assertThat(generatedValue).hasValueSatisfying(value -> assertThat(value.doubleValue())
                                .isEqualTo(68.0));

        verify(sensorValueGenerator).generateNextValue(sensorChannelKey, 0.5);
        verify(sensorUnitConverter).convertFromCanonical("TEMPERATURE", "°F", 20.0);
        verifyNoInteractions(sensorThresholdCache, thresholdBasedSensorValueGenerator);
    }

    @Test
    @DisplayName("고정 생성기의 단위 변환에 실패해도 동적 생성기로 전환하지 않는다")
    void doNotFallbackWhenFixedSensorUnitConversionFails() {
        SensorChannelKey sensorChannelKey = new SensorChannelKey("device-A", "TEMPERATURE", "K");

        when(sensorValueGeneratorRegistry.findBySensorType("TEMPERATURE")).thenReturn(Optional.of(sensorValueGenerator));
        when(sensorValueGenerator.generateNextValue(sensorChannelKey, 0.0)).thenReturn(20.0);
        when(sensorUnitConverter.convertFromCanonical("TEMPERATURE", "K", 20.0))
                .thenReturn(Optional.empty());

        Optional<Number> generatedValue = sensorValueGenerationResolver
                .generateNextValue(1L, sensorChannelKey, 0.0);

        assertThat(generatedValue).isEmpty();

        verify(sensorValueGenerator).generateNextValue(sensorChannelKey, 0.0);
        verify(sensorUnitConverter).convertFromCanonical("TEMPERATURE", "K", 20.0);

        verifyNoInteractions(sensorThresholdCache, thresholdBasedSensorValueGenerator);
    }

    @Test
    @DisplayName("고정 생성기가 없는 센서는 등록된 임계값으로 생성한다")
    void generateDynamicSensorValueFromThreshold() {
        SensorChannelKey sensorChannelKey = new SensorChannelKey("device-A", "SOIL_MOISTURE", "%");
        SensorThresholdKey sensorThresholdKey = new SensorThresholdKey(1L, "SOIL_MOISTURE", "%");

        SensorThresholdRange sensorThresholdRange = new SensorThresholdRange(new BigDecimal("30"), new BigDecimal("70"));

        when(sensorValueGeneratorRegistry.findBySensorType("SOIL_MOISTURE")).thenReturn(Optional.empty());
        when(sensorThresholdCache.find(sensorThresholdKey)).thenReturn(Optional.of(sensorThresholdRange));
        when(thresholdBasedSensorValueGenerator.generateNextValue(sensorChannelKey, sensorThresholdRange)).thenReturn(50.0);

        Optional<Number> generatedValue = sensorValueGenerationResolver
                .generateNextValue(1L, sensorChannelKey, 10.0);

        assertThat(generatedValue).hasValueSatisfying(
                        value -> assertThat(value.doubleValue()).isEqualTo(50.0));

        verify(sensorThresholdCache).find(sensorThresholdKey);

        verify(thresholdBasedSensorValueGenerator)
                .generateNextValue(sensorChannelKey, sensorThresholdRange);

        verifyNoInteractions(sensorUnitConverter);
    }

    @Test
    @DisplayName("고정 생성기와 임계값이 모두 없으면 빈 결과를 반환한다")
    void returnEmptyWhenDynamicSensorThresholdDoesNotExist() {
        SensorChannelKey sensorChannelKey = new SensorChannelKey("device-A", "SOIL_MOISTURE", "%");
        SensorThresholdKey sensorThresholdKey = new SensorThresholdKey(1L, "SOIL_MOISTURE", "%");

        when(sensorValueGeneratorRegistry.findBySensorType("SOIL_MOISTURE")).thenReturn(Optional.empty());
        when(sensorThresholdCache.find(sensorThresholdKey)).thenReturn(Optional.empty());

        Optional<Number> generatedValue = sensorValueGenerationResolver
                .generateNextValue(1L, sensorChannelKey, 0.0);

        assertThat(generatedValue).isEmpty();

        verify(sensorThresholdCache).find(sensorThresholdKey);

        verifyNoInteractions(thresholdBasedSensorValueGenerator, sensorUnitConverter);
    }

    @Test
    @DisplayName("고정 센서 채널 상태 삭제를 고정 생성기에 위임한다")
    void removeFixedSensorStateUsingFixedGenerator() {
        SensorChannelKey sensorChannelKey = new SensorChannelKey("device-A", "TEMPERATURE", "°C");

        when(sensorValueGeneratorRegistry.findBySensorType("TEMPERATURE")).thenReturn(Optional.of(sensorValueGenerator));

        sensorValueGenerationResolver.removeState(sensorChannelKey);

        verify(sensorValueGenerator).removeState(sensorChannelKey);

        verifyNoInteractions(thresholdBasedSensorValueGenerator);
    }

    @Test
    @DisplayName("동적 센서 채널 상태 삭제를 임계값 기반 생성기에 위임한다")
    void removeDynamicSensorStateUsingThresholdBasedGenerator() {
        SensorChannelKey sensorChannelKey = new SensorChannelKey("device-A", "SOIL_MOISTURE", "%");

        when(sensorValueGeneratorRegistry.findBySensorType("SOIL_MOISTURE")).thenReturn(Optional.empty());

        sensorValueGenerationResolver.removeState(sensorChannelKey);

        verify(thresholdBasedSensorValueGenerator).removeState(sensorChannelKey);

        verifyNoInteractions(sensorValueGenerator);
    }

    @Test
    @DisplayName("잘못된 생성 요청은 Registry 조회 전에 거절한다")
    void rejectInvalidGenerationRequestBeforeRegistryLookup() {
        SensorChannelKey sensorChannelKey = new SensorChannelKey("device-A", "TEMPERATURE", "°C");

        assertThatThrownBy(() -> sensorValueGenerationResolver
                .generateNextValue(0L, sensorChannelKey, 0.0))
                .isInstanceOf(SensorDataGenerationException.class);

        assertThatThrownBy(() -> sensorValueGenerationResolver
                .generateNextValue(1L, null, 0.0))
                .isInstanceOf(SensorDataGenerationException.class);

        assertThatThrownBy(() -> sensorValueGenerationResolver
                .generateNextValue(1L, sensorChannelKey, Double.NaN))
                .isInstanceOf(SensorDataGenerationException.class);

        verifyNoInteractions(sensorValueGeneratorRegistry);
    }
}

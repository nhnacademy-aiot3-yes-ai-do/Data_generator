package site.yesaido.data_generator.generator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.data_generator.cache.SensorThresholdCache;
import site.yesaido.data_generator.converter.SensorUnitConverter;
import site.yesaido.data_generator.converter.StandardSensorUnitConverter;
import site.yesaido.data_generator.domain.DynamicSensorGenerationPolicy;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensorValueGenerationResolverConstructionTest {

    private final SensorValueGeneratorRegistry registry =
            new SensorValueGeneratorRegistry(List.of(new StubSensorValueGenerator()));
    private final SensorThresholdCache thresholdCache =
            new SensorThresholdCache();
    private final ThresholdBasedSensorValueGenerator thresholdBasedGenerator =
            new ThresholdBasedSensorValueGenerator(
                    new RandomWalkGenerator(new Random(0L)),
                    new DynamicSensorConfigurationFactory(),
                    new DynamicSensorGenerationPolicy(
                            new BigDecimal("0.2"), new BigDecimal("0.02"), 2));
    private final SensorUnitConverter unitConverter =
            new StandardSensorUnitConverter();

    @Test
    @DisplayName("Registry가 null이면 Resolver를 만들 수 없다")
    void rejectNullRegistry() {
        assertThatThrownBy(() -> new SensorValueGenerationResolver(
                null, thresholdCache, thresholdBasedGenerator, unitConverter))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("sensorValueGeneratorRegistry");
    }

    @Test
    @DisplayName("임계값 캐시가 null이면 Resolver를 만들 수 없다")
    void rejectNullThresholdCache() {
        assertThatThrownBy(() -> new SensorValueGenerationResolver(
                registry, null, thresholdBasedGenerator, unitConverter))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("sensorThresholdCache");
    }

    @Test
    @DisplayName("임계값 기반 생성기가 null이면 Resolver를 만들 수 없다")
    void rejectNullThresholdBasedGenerator() {
        assertThatThrownBy(() -> new SensorValueGenerationResolver(
                registry, thresholdCache, null, unitConverter))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("thresholdBasedSensorValueGenerator");
    }

    @Test
    @DisplayName("단위 변환기가 null이면 Resolver를 만들 수 없다")
    void rejectNullUnitConverter() {
        assertThatThrownBy(() -> new SensorValueGenerationResolver(
                registry, thresholdCache, thresholdBasedGenerator, null))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("sensorUnitConverter");
    }

    private static final class StubSensorValueGenerator implements SensorValueGenerator {

        @Override
        public String supportedSensorType() {
            return "TEMPERATURE";
        }

        @Override
        public Number generateNextValue(
                SensorChannelKey sensorChannelKey,
                double actuatorEffectAmount
        ) {
            throw new UnsupportedOperationException("생성자 테스트에서는 호출하지 않습니다.");
        }

        @Override
        public void removeState(SensorChannelKey sensorChannelKey) {
            throw new UnsupportedOperationException("생성자 테스트에서는 호출하지 않습니다.");
        }
    }
}

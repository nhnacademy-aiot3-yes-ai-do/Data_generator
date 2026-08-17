package site.yesaido.data_generator.generator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import site.yesaido.data_generator.domain.SensorChannelKey;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensorValueGeneratorRegistryTest {

    @Test
    @DisplayName("생성기 타입을 정규화해 등록하고 조회한다")
    void registerAndFindGeneratorByNormalizedSensorType() {
        SensorValueGenerator firstGenerator =
                new StubSensorValueGenerator("  TEMPERATURE  ");

        SensorValueGeneratorRegistry registry =
                new SensorValueGeneratorRegistry(List.of(firstGenerator));

        assertThat(registry.findBySensorType("  TEMPERATURE  "))
                .contains(firstGenerator);
        assertThat(registry.findBySensorType("HUMIDITY")).isEmpty();
    }

    @Test
    @DisplayName("생성기 목록이 null 또는 비어 있으면 Registry를 만들 수 없다")
    void rejectNullOrEmptyGeneratorList() {
        List<SensorValueGenerator> emptyGenerators = List.of();

        assertThatThrownBy(() -> new SensorValueGeneratorRegistry(null))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("null");

        assertThatThrownBy(() -> new SensorValueGeneratorRegistry(emptyGenerators))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("비어");
    }

    @Test
    @DisplayName("생성기 목록에 null이 포함되면 Registry를 만들 수 없다")
    void rejectNullGeneratorElement() {
        List<SensorValueGenerator> generatorsWithNull = Collections.singletonList(null);

        assertThatThrownBy(() -> new SensorValueGeneratorRegistry(generatorsWithNull))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("null");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("생성기가 지원하는 센서 타입이 null 또는 공백이면 등록할 수 없다")
    void rejectMissingSupportedSensorType(String supportedSensorType) {
        SensorValueGenerator firstGenerator =
                new StubSensorValueGenerator(supportedSensorType);
        List<SensorValueGenerator> generators = List.of(firstGenerator);

        assertThatThrownBy(() -> new SensorValueGeneratorRegistry(generators))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("supportedSensorType");
    }

    @Test
    @DisplayName("같은 센서 타입의 생성기가 중복되면 Registry를 만들 수 없다")
    void rejectDuplicateSupportedSensorType() {
        SensorValueGenerator firstGenerator =
                new StubSensorValueGenerator("TEMPERATURE");
        SensorValueGenerator secondGenerator =
                new StubSensorValueGenerator("  TEMPERATURE  ");
        List<SensorValueGenerator> generators = List.of(firstGenerator, secondGenerator);

        assertThatThrownBy(() -> new SensorValueGeneratorRegistry(generators))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("중복");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("조회할 센서 타입이 null 또는 공백이면 예외가 발생한다")
    void rejectMissingLookupSensorType(String sensorType) {
        SensorValueGenerator firstGenerator =
                new StubSensorValueGenerator("TEMPERATURE");
        SensorValueGeneratorRegistry registry =
                new SensorValueGeneratorRegistry(List.of(firstGenerator));

        assertThatThrownBy(() -> registry.findBySensorType(sensorType))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("sensorType");
    }

    private record StubSensorValueGenerator(String supportedSensorType)
            implements SensorValueGenerator {

        @Override
        public Number generateNextValue(
                SensorChannelKey sensorChannelKey,
                double actuatorEffectAmount
        ) {
            throw new UnsupportedOperationException("Registry 테스트에서는 호출하지 않습니다.");
        }

        @Override
        public void removeState(SensorChannelKey sensorChannelKey) {
            throw new UnsupportedOperationException("Registry 테스트에서는 호출하지 않습니다.");
        }
    }
}

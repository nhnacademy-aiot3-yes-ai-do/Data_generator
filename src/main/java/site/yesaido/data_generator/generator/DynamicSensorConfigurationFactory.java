package site.yesaido.data_generator.generator;

import org.springframework.stereotype.Component;
import site.yesaido.data_generator.domain.DynamicSensorGenerationPolicy;
import site.yesaido.data_generator.domain.MeasurementConfiguration;
import site.yesaido.data_generator.domain.SensorThresholdRange;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.math.BigDecimal;

// 임계값 범위와 생성 정책을 Random Walk 설정으로 변환하는 상태 없는 Factory
@Component
public final class DynamicSensorConfigurationFactory {
    public MeasurementConfiguration create(
            SensorThresholdRange thresholdRange,
            DynamicSensorGenerationPolicy generationPolicy
    ) {
        if ( thresholdRange == null) {
            throw new SensorDataGenerationException("thresholdRange는 null일 수 없습니다.");
        }

        if( generationPolicy == null) {
            throw new SensorDataGenerationException("generationPolicy는 null일 수 없습니다.");
        }

        BigDecimal rangeWidth = thresholdRange.rangeWidth();
        BigDecimal expansionAmount = rangeWidth.multiply(generationPolicy.rangeExpansionRatio());

        BigDecimal generatedMinimum = thresholdRange.thresholdMin().subtract(expansionAmount);
        BigDecimal generatedMaximum = thresholdRange.thresholdMax().add(expansionAmount);

        BigDecimal initialValue = thresholdRange.midpoint();
        BigDecimal maximumChange = rangeWidth.multiply(generationPolicy.maximumChangeRatio());

        return new MeasurementConfiguration(toFiniteDouble(initialValue,"initialValue"),
                toFiniteDouble(generatedMinimum,"generatedMinimum"),
                toFiniteDouble(generatedMaximum,"generatedMaximum"),
                toFiniteDouble(maximumChange, "maximumChange"),
                generationPolicy.decimalPlaces());
    }

    private static double toFiniteDouble(BigDecimal value, String fieldName) {
        double convertedValue = value.doubleValue();

        if(!Double.isFinite(convertedValue)) {
            throw new SensorDataGenerationException(fieldName + "을 유한한 double 값으로 변환할 수 없습니다.");
        }
        if( value.signum() != 0 && convertedValue == 0.0) {
            throw new SensorDataGenerationException(fieldName + "이 너무 작아 double로 변환하면 0이 됩니다.");
        }

        return convertedValue;
    }
}

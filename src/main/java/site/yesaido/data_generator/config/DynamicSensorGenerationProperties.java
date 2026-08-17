package site.yesaido.data_generator.config;


import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import site.yesaido.data_generator.domain.MeasurementConfiguration;

import java.math.BigDecimal;

// 미지원 숫자형 센서의 임계값 기반 생성 정책을 외부 설정으로 받는 클래스
@Setter
@Getter
@Validated
@Component
@ConfigurationProperties(prefix = "generator.dynamic-sensor")
public class DynamicSensorGenerationProperties {

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private BigDecimal rangeExpansionRatio = new BigDecimal("0.20"); // 임계값 범위 폭의 20%씩 하한·상한 양쪽으로 확장

    @NotNull
    @DecimalMin(
            value = "0.0",
            inclusive = false
    )
    @DecimalMax("1.0")
    private BigDecimal maximumChangeRatio = new BigDecimal("0.02"); // 초당 임계값 폭의 2% 씩 변화

    @Min(0)
    @Max(MeasurementConfiguration.MAX_DECIMAL_PLACES)
    private int decimalPlaces = 2;
}

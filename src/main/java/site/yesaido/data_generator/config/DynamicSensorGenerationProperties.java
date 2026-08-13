package site.yesaido.data_generator.config;


import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

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
    private BigDecimal rangeExpansionRatio = new BigDecimal("0.10");

    @NotNull
    @DecimalMin(
            value = "0.0",
            inclusive = false
    )
    @DecimalMax("1.0")
    private BigDecimal maximumChangeRatio = new BigDecimal("0.02");

    @Min(0)
    private int decimalPlaces = 2;
}

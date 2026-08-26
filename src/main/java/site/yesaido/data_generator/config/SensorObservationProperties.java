package site.yesaido.data_generator.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

// EUI별 센서 관측값 생성 정책을 외부 설정으로 받는 클래스
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "generator.sensor-observation")
public class SensorObservationProperties {

    // 측정 가능 범위 폭의 최대 2%를 EUI별 고정 편향으로 사용
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private BigDecimal maximumBiasRatio =
            new BigDecimal("0.02");

    // 측정 가능 범위 폭의 최대 1%를 EUI별 동적 편차로 사용
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private BigDecimal maximumDeviationRatio =
            new BigDecimal("0.01");

    // 직전 주기 동적 편차의 80%를 다음 주기에 유지
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax(
            value = "1.0",
            inclusive = false
    )
    private BigDecimal deviationRetentionRatio = new BigDecimal("0.80");
}

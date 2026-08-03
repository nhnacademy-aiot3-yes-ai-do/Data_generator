package site.yesaido.data_generator.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties{

    @NotBlank
    private String brokerUrl;

    @NotBlank
    private String clientId;

    private String username;
    private String password;


    @Min(0)
    @Max(2)
    private int qos = 0;

    private boolean retained = false;

    @Min(1)
    private int maxInflight = 100;

    private boolean automaticReconnect = true;

    @Min(1)
    private int connectionTimeoutSeconds = 10;

    @Min(1)
    private int keepAliveSeconds = 30;

    @Min(1_000)
    private int maximumReconnectDelayMilliseconds = 30_000;

    private boolean cleanSession = true;


}

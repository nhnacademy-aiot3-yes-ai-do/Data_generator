package site.yesaido.data_generator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import site.yesaido.data_generator.client.CultivationSensorReadable;

@SpringBootApplication
@EnableFeignClients(basePackageClasses = CultivationSensorReadable.class)
public class DataGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataGeneratorApplication.class, args);
    }

}

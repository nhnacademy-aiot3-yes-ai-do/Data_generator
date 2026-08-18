package site.yesaido.data_generator.fixture;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import site.yesaido.data_generator.cache.SensorCache;
import site.yesaido.data_generator.cache.SensorThresholdCache;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.domain.SensorThresholdKey;
import site.yesaido.data_generator.domain.SensorThresholdRange;
import site.yesaido.data_generator.domain.SensorTypeSpec;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalSensorFixtureInitializer implements ApplicationRunner { // 로컬 테스트용 센서 클래스

    private static final long FIXTURE_CULTIVATION_ID = 1L;
    private static final String FIXTURE_DEVICE_EUI = "43a123123c777999";
    private static final String FIXTURE_DEVICE_NAME = "TEST123-DEVICE";
    private static final String FIXTURE_LOCATION = "송이버섯집";
    private static final String FIXTURE_LOCATION_DETAIL = "중앙 오른쪽";
    private static final String FIXTURE_DEVICE_MODEL = "TEST123";

    private static final String FIXTURE_DYNAMIC_SENSOR_TYPE = "SOIL_MOISTURE";
    private static final String FIXTURE_DYNAMIC_SENSOR_UNIT = "%";

    // 로컬 장치가 제공하는 타입·단위별 독립 측정 채널
    private static final Set<SensorTypeSpec> FIXTURE_SENSOR_TYPES = Set.of(
            new SensorTypeSpec("TEMPERATURE", "°C"),
            new SensorTypeSpec("TEMPERATURE", "°F"),
            new SensorTypeSpec("HUMIDITY", "%"),
            new SensorTypeSpec("CO2", "ppm"),
            new SensorTypeSpec("LIGHT", "lux"),
            new SensorTypeSpec(FIXTURE_DYNAMIC_SENSOR_TYPE,FIXTURE_DYNAMIC_SENSOR_UNIT)
    );

    private final SensorCache sensorCache;
    private final SensorThresholdCache sensorThresholdCache;

    @Override
    public void run(ApplicationArguments applicationArguments) {
        SensorThresholdKey sensorThresholdKey = new SensorThresholdKey(FIXTURE_CULTIVATION_ID,FIXTURE_DYNAMIC_SENSOR_TYPE,FIXTURE_DYNAMIC_SENSOR_UNIT);
        SensorThresholdRange sensorThresholdRange = new SensorThresholdRange(new BigDecimal("30"), new BigDecimal("70"));
        SensorCacheEntry fixtureSensorCacheEntry = createFixtureSensorCacheEntry();

        sensorThresholdCache.replaceAll(Map.of(sensorThresholdKey,sensorThresholdRange));
        sensorCache.replaceAll(List.of(fixtureSensorCacheEntry));
        log.info("local fixture 센서를 캐시에 등록했습니다. cultivationId={}, deviceEui={}, sensorChannelCount={}, thresholdCount={}", fixtureSensorCacheEntry.cultivationId(), fixtureSensorCacheEntry.deviceEui(), fixtureSensorCacheEntry.sensorTypes().size(), sensorThresholdCache.getThresholdCount());
    }

    private static SensorCacheEntry createFixtureSensorCacheEntry() {
        return new SensorCacheEntry(FIXTURE_CULTIVATION_ID,FIXTURE_DEVICE_EUI,FIXTURE_DEVICE_NAME,FIXTURE_LOCATION,FIXTURE_LOCATION_DETAIL,FIXTURE_DEVICE_MODEL,FIXTURE_SENSOR_TYPES);
    }
}

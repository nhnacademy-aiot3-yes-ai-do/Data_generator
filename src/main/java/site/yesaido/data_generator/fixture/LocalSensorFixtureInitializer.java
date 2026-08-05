package site.yesaido.data_generator.fixture;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import site.yesaido.data_generator.cache.SensorCache;
import site.yesaido.data_generator.domain.MeasurementType;
import site.yesaido.data_generator.domain.SensorCacheEntry;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalSensorFixtureInitializer implements ApplicationRunner { // 로컬 테스트용 센서 클래스

    private static final long FIXTURE_CULTIVATION_ID = 1L;
    private static final String FIXTURE_DEVICE_EUI = "43ㅁ123123c777999";
    private static final String FIXTURE_DEVICE_NAME = "TEST123-DEVICE";
    private static final String FIXTURE_LOCATION = "송이버섯집";
    private static final String FIXTURE_LOCATION_DETAIL = "중앙 오른쪽";
    private static final String FIXTURE_DEVICE_MODEL = "TEST123";
    private static final Set<MeasurementType> FIXTURE_MEASUREMENT_TYPES = Set.of(MeasurementType.TEMPERATURE, MeasurementType.HUMIDITY, MeasurementType.CO2, MeasurementType.LIGHT);
    private final SensorCache sensorCache;

    @Override
    public void run(ApplicationArguments applicationArguments) {
        SensorCacheEntry fixtureSensorCacheEntry = createFixtureSensorCacheEntry();

        sensorCache.replaceAll(List.of(fixtureSensorCacheEntry));
        log.info("local fixture 센서를 캐시에 등록했습니다. cultivationId={}, deviceEui={}, measurementTypeCount={}", fixtureSensorCacheEntry.cultivationId(), fixtureSensorCacheEntry.deviceEui(), fixtureSensorCacheEntry.measurementTypes().size());
    }

    private static SensorCacheEntry createFixtureSensorCacheEntry() {
        return new SensorCacheEntry(FIXTURE_CULTIVATION_ID,FIXTURE_DEVICE_EUI,FIXTURE_DEVICE_NAME,FIXTURE_LOCATION,FIXTURE_LOCATION_DETAIL,FIXTURE_DEVICE_MODEL,FIXTURE_MEASUREMENT_TYPES);
    }
}

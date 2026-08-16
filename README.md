# Data Generator

실제 센서가 없는 개발 환경에서 가상 센서값을 생성하여 MQTT Broker로 발행하고, 가상 액추에이터 상태를 시뮬레이션하는 Spring Boot 서비스입니다.

## 주요 기능

- 센서 장치와 측정 채널의 인메모리 캐시 관리
- 채널별 자연스러운 Random Walk 센서값 생성
- MQTT를 통한 센서값 비동기 발행
- Cultivation Server snapshot 기반 초기 데이터 복구
- RabbitMQ 센서·임계값 변경 이벤트 반영
- 임계값 기반 동적 센서 타입 지원
- Rule Engine의 내부 API 요청을 통한 가상 액추에이터 제어
- 액추에이터 상태에 따른 센서값 방향성 효과 적용
- cultivation 단위 비동기 작업 실행

## 기술 스택

- Java 21
- Spring Boot 4.0.7
- Spring Cloud 2025.1.2
- Maven
- OpenFeign
- RabbitMQ
- Eclipse Paho MQTT
- Jackson 3
- Lombok

## 데이터 흐름

### 애플리케이션 시작

```text
Cultivation Server snapshot 조회
→ snapshot 전체 검증 및 변환
→ 임계값 캐시 초기화
→ 센서 캐시 초기화
→ RabbitMQ Listener 시작
→ 센서 데이터 생성 시작
```

초기 snapshot 동기화가 완료되기 전에는 MQTT 센서 데이터를 발행하지 않습니다.

초기 동기화에 실패하면 설정된 횟수와 backoff 정책에 따라 재시도하며, 최종 실패하면 애플리케이션 시작도 실패합니다.

### 센서 데이터 생성

```text
전역 1초 스케줄러
→ 센서 캐시 snapshot 조회
→ cultivation별 작업 제출
→ 센서 채널별 값 생성
→ 등록 단위로 변환
→ MQTT payload 직렬화
→ MQTT Broker 비동기 발행
```

장치마다 별도의 스레드를 만들지 않습니다. 고정 크기 작업 스레드 풀을 사용하며, 같은 cultivation의 작업이 이전 주기에서 아직 실행 중이면 다음 주기를 건너뜁니다.

## 센서 채널 식별

독립적인 센서 채널은 다음 값으로 식별합니다.

```text
(deviceEui, sensorType, unit)
```

같은 장치와 같은 센서 타입이라도 단위가 다르면 서로 다른 채널이며, 각각 독립적인 Random Walk 상태를 가집니다.

예:

```text
(device-A, TEMPERATURE, °C)
(device-A, TEMPERATURE, °F)
```

임계값은 다음 값으로 식별합니다.

```text
(cultivationId, sensorType, unit)
```

같은 cultivation에 같은 `sensorType`과 `unit`을 사용하는 장치가 여러 대 있으면 임계값을 공유하지만, 측정 상태는 장치별로 분리됩니다.

## 지원 센서 타입과 단위

고정 생성기를 제공하는 센서 타입은 다음과 같습니다.

| sensorType | 지원 단위 | 내부 표준 단위 |
|---|---|---|
| `TEMPERATURE` | `°C`, `°F` | 섭씨 |
| `HUMIDITY` | `%RH` | 상대습도 |
| `CO2` | `ppm` | ppm |
| `LIGHT` | `lux` | lux |

등록된 고정 생성기가 없는 숫자형 `sensorType`도 임계값이 존재하면 동적 센서로 생성할 수 있습니다.

동적 센서는 등록된 `unit`과 임계값을 그대로 사용하며, 임의의 단위 변환이나 액추에이터 효과를 적용하지 않습니다.

## MQTT 계약

### 토픽

```text
mushroom/{location}/{locationDetail}/{deviceModel}/{deviceEui}/{sensorType}
```

예:

```text
mushroom/송이버섯집/중앙 오른쪽/TEST123/43a123123c777999/TEMPERATURE
```

`unit`은 MQTT 토픽에 포함하지 않고 payload에 포함합니다.

같은 장치의 `TEMPERATURE/°C`와 `TEMPERATURE/°F`는 같은 토픽으로 발행되며, payload의 `unit`으로 구분합니다.

모든 하위 토픽을 확인할 때 사용할 구독 와일드카드:

```text
mushroom/#
```

토픽 구성요소에는 `/`, `+`, `#`, null 문자를 사용할 수 없습니다. 한글과 일반 공백은 허용합니다.

### Payload

```json
{
  "value": 20.4,
  "unit": "°C",
  "time": "2026-08-14T15:00:00+09:00",
  "device_name": "TEST123-DEVICE",
  "device_eui": "43a123123c777999"
}
```

- `value`는 JSON 숫자입니다.
- `time`은 동일한 순간을 서울 오프셋 `+09:00`으로 표현합니다.
- JSON 필드명은 snake_case를 사용합니다.
- 지원할 수 없는 타입·단위 또는 임계값이 없는 동적 센서는 발행하지 않습니다.

## Cultivation snapshot

Data Generator는 non-local 환경에서 OpenFeign으로 다음 내부 API를 호출합니다.

```http
GET /api/internal/data-generator/snapshot
```

Feign 서비스 이름:

```text
cultivation-server
```

snapshot에는 다음 데이터가 포함됩니다.

- 활성 cultivation의 센서 장치
- 장치별 `sensorType`과 `unit`
- cultivation별 센서 임계값
- snapshot 생성 시각

응답 전체를 먼저 검증하고 변환한 뒤 캐시를 초기화합니다.

다음 값은 정상적인 응답입니다.

- 빈 센서 목록
- 빈 임계값 목록

다음 값은 동기화 실패로 처리합니다.

- null 목록 또는 null 원소
- 채널이 없는 센서 장치
- 중복 `deviceEui`
- 중복 `(deviceEui, sensorType, unit)`
- 중복 `(cultivationId, sensorType, unit)`
- 잘못된 임계값 범위

## RabbitMQ 계약

### 토폴로지

| 구분 | 값 |
|---|---|
| Exchange | `yes-nhn.sensor.exchange` |
| 센서 큐 | `yes-nhn.data-source.sensor-info.queue` |
| 임계값 큐 | `yes-nhn.data-source.threshold-info.queue` |
| Dead Letter Exchange | `yes-nhn.dlx` |
| Dead Letter Queue | `yes-nhn.dlq` |

### 이벤트 TypeId

| TypeId | 역할 |
|---|---|
| `sensor.upsert` | 센서 채널 등록 또는 변경 |
| `sensor.delete` | 정확한 센서 채널 하나 삭제 |
| `threshold.crud` | cultivation 임계값 변경 또는 전체 삭제 |

센서 채널 이벤트 한 건은 다음 채널 하나를 의미합니다.

```text
(deviceEui, sensorType, unit)
```

`threshold.crud` 이벤트는 다음과 같이 처리합니다.

- `sensorRangeList`가 비어 있으면 해당 cultivation의 생성 상태와 캐시를 정리합니다.
- 한 개 이상이면 목록 개수와 관계없이 모든 임계값을 Upsert합니다.
- 목록 개수로 전체 교체 여부를 추론하지 않습니다.

Listener는 snapshot 초기화가 성공한 후 시작합니다. 이벤트 처리 실패 시 최초 처리 이후 최대 3회 재시도하며, 최종 실패한 메시지는 `yes-nhn.dlq`로 전달합니다.

## 가상 액추에이터

Rule Engine은 다음 내부 API로 가상 액추에이터 상태를 변경합니다.

```http
PUT /internal/cultivations/{cultivationId}/actuators/{actuatorType}/state
```

요청 예:

```json
{
  "controlId": "64238d8b-d623-45c5-a677-4576149e3187",
  "commandId": "e3f99170-b89b-4ec5-980c-1342e55eb704",
  "desiredState": "ON",
  "requestedAt": "2026-08-14T06:00:00Z",
  "expiresAt": "2026-08-14T06:01:00Z"
}
```

응답 예:

```json
{
  "controlId": "64238d8b-d623-45c5-a677-4576149e3187",
  "commandId": "e3f99170-b89b-4ec5-980c-1342e55eb704",
  "status": "APPLIED",
  "actualState": "ON",
  "appliedAt": "2026-08-14T06:00:00.100Z"
}
```

지원 액추에이터:

| ActuatorType | 센서 효과 |
|---|---|
| `HEATER` | `TEMPERATURE` 상승 |
| `COOLER` | `TEMPERATURE` 하강 |
| `HUMIDIFIER` | `HUMIDITY` 상승 |
| `DEHUMIDIFIER` | `HUMIDITY` 하강 |
| `CO2_SUPPLIER` | `CO2` 상승 |
| `VENTILATION_FAN` | `CO2` 하강 |
| `LED` | `LIGHT` 상승 |
| `LIGHT_REDUCER` | `LIGHT` 하강 |

명령 처리 시 다음을 검증합니다.

- `commandId` 멱등성
- 명령 만료 여부
- 이전 명령보다 오래된 stale 명령
- 서로 반대되는 액추에이터의 동시 ON 충돌

액추에이터 상태가 변경되면 다음 1초 센서값 생성부터 효과가 반영됩니다.

## 환경변수

프로젝트 루트의 `.env` 파일 또는 실행 환경의 환경변수를 사용합니다. `.env`는 Git에 커밋하지 않습니다.

필수 환경변수:

| 환경변수 | 설명 |
|---|---|
| `RABBITMQ_HOST` | RabbitMQ 호스트 |
| `RABBITMQ_PORT` | RabbitMQ 포트 |
| `RABBITMQ_USERNAME` | RabbitMQ 사용자명 |
| `RABBITMQ_PASSWORD` | RabbitMQ 비밀번호 |
| `MQTT_BROKER_URL` | MQTT Broker URL |
| `MQTT_CLIENT_ID` | MQTT Client ID |

MQTT 선택 설정:

| 환경변수 | 기본값 |
|---|---:|
| `MQTT_USERNAME` | 빈 문자열 |
| `MQTT_PASSWORD` | 빈 문자열 |
| `MQTT_QOS` | `0` |
| `MQTT_RETAINED` | `false` |
| `MQTT_MAX_INFLIGHT` | `100` |
| `MQTT_AUTOMATIC_RECONNECT` | `true` |
| `MQTT_CONNECTION_TIMEOUT_SECONDS` | `10` |
| `MQTT_KEEP_ALIVE_SECONDS` | `30` |
| `MQTT_MAXIMUM_RECONNECT_DELAY_MILLISECONDS` | `30000` |
| `MQTT_CLEAN_SESSION` | `true` |

생성 작업 설정:

| 환경변수 | 기본값 |
|---|---:|
| `GENERATOR_WORKER_POOL_SIZE` | `4` |
| `GENERATOR_WORKER_QUEUE_CAPACITY` | `200` |
| `GENERATOR_WORKER_AWAIT_TERMINATION_SECONDS` | `10` |

동적 센서 설정:

| 환경변수 | 기본값 |
|---|---:|
| `GENERATOR_DYNAMIC_SENSOR_RANGE_EXPANSION_RATIO` | `0.20` |
| `GENERATOR_DYNAMIC_SENSOR_MAXIMUM_CHANGE_RATIO` | `0.02` |
| `GENERATOR_DYNAMIC_SENSOR_DECIMAL_PLACES` | `2` |

snapshot 동기화 설정:

| 환경변수 | 기본값 |
|---|---:|
| `SENSOR_SYNCHRONIZATION_MAX_ATTEMPTS` | `5` |
| `SENSOR_SYNCHRONIZATION_INITIAL_BACKOFF_MILLISECONDS` | `1000` |
| `SENSOR_SYNCHRONIZATION_BACKOFF_MULTIPLIER` | `2.0` |
| `SENSOR_SYNCHRONIZATION_MAXIMUM_BACKOFF_MILLISECONDS` | `30000` |

로컬 `.env` 예:

```properties
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest

MQTT_BROKER_URL=tcp://localhost:1883
MQTT_CLIENT_ID=data-generator-local
MQTT_USERNAME=
MQTT_PASSWORD=
```

## 로컬 실행

로컬 프로필에서는 Cultivation snapshot을 호출하지 않고 fixture 센서를 인메모리 캐시에 등록합니다.

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

fixture 정보:

| 항목 | 값 |
|---|---|
| cultivationId | `1` |
| deviceEui | `43a123123c777999` |
| deviceName | `TEST123-DEVICE` |
| location | `송이버섯집` |
| locationDetail | `중앙 오른쪽` |
| deviceModel | `TEST123` |

fixture 채널:

- `TEMPERATURE/°C`
- `TEMPERATURE/°F`
- `HUMIDITY/%RH`
- `CO2/ppm`
- `LIGHT/lux`
- `SOIL_MOISTURE/%`

`SOIL_MOISTURE/%` 임계값은 `30~70`입니다.

local 프로필에서는 RabbitMQ Listener를 시작하지 않습니다. MQTT Broker는 애플리케이션 시작 전에 연결 가능한 상태여야 합니다.

## 빌드와 테스트

컴파일:

```bash
./mvnw -DskipTests compile
```

테스트:

```bash
./mvnw clean test
```

패키징:

```bash
./mvnw clean package
```

## Docker

이미지 빌드:

```bash
docker build -t data-generator .
```

## 현재 제약사항

- Data Generator는 thread-safe 인메모리 캐시를 사용하므로 현재 replica는 `1`로 운영합니다.
- 센서값과 액추에이터 상태는 영구 저장하지 않으며 재시작 시 snapshot과 이벤트로 복구합니다.
- MQTT 기본 QoS는 `0`이므로 연결 단절 중 메시지 유실이 발생할 수 있습니다.
- RabbitMQ 이벤트에는 단조 증가 revision이 없어 완전한 역순 이벤트 차단은 지원하지 않습니다.
- custom readiness는 아직 snapshot, RabbitMQ Listener, MQTT 연결 상태를 함께 반영하지 않습니다.
- 내부 API의 최종 인증·인가 정책은 Kubernetes 네트워크 정책 및 팀 보안 계약과 함께 확정해야 합니다.
- Rule Engine의 액추에이터 OpenFeign Client는 Rule Engine 저장소에서 관리합니다.
- DLQ 메시지의 자동 재처리 정책은 제공하지 않습니다.

# Data Generator 근거 원장

검토일: 2026-09-14. 대상: 이 `Data_generator` 저장소. 조사 당시 로컬 HEAD: `b4f1b88`. 코드 작업 트리는 변경 없음이며 `.DS_Store` 기존 변경은 무시했다. 원본 소스·환경변수·운영 상태는 변경하지 않았다. 원시 접속 주소·비밀번호·토큰은 이 문서에 옮기지 않았다.

대상 본문: [DEVELOPMENT_HISTORY.md](DEVELOPMENT_HISTORY.md).

## 확인 상태 읽는 법

- **구현 확인:** 현재 로컬 코드에서 해당 동작이 존재함. 운영 배포·전체 성공을 뜻하지 않음.
- **이력 확인:** 커밋 author date와 코드 변경이 있음. 최초 발견·배포 날짜는 별도 확인.
- **기존 검증 기록:** 남아 있는 테스트 보고서. 이번 실행 결과가 아님.
- **사용자 설명:** 사용자 대화에서 제공한 제작 배경·담당 범위. 코드만으로 동기·개인 역할 전부를 확정하지 않음.
- **해석/미확인:** 코드로 설명 가능한 제약 또는 후속 검토 항목. 실제 장애 발생 기록과 구분.

## E-DG-01. 목적과 스택

- [저장소 루트 README](../../../README.md): 첫 문장과 주요 기능은 실제 센서 없는 개발 환경의 가상 값 발행·가상 액추에이터 시뮬레이션 목적을 명시.
- 기술 스택은 Java 21, Spring Boot 4.0.7, OpenFeign, RabbitMQ, Paho MQTT, Jackson 등으로 기재.
- 사용자 설명: 강의실의 장비·실제 재배 환경 제약 때문에 필요했다. 모든 품종의 재배 조건을 일반화하는 문장은 증거에서 제외.
- 주의: README의 채널별 독립 Random Walk 설명은 E-DG-05와 불일치. 생성 구조 설명은 현재 코드 우선.

## E-DG-02. 채널·캐시·단위

- [SensorCache](../../../src/main/java/site/yesaido/data_generator/cache/SensorCache.java): 불변 Map을 `AtomicReference`로 교체, upsert 채널 합집합, 정확한 채널 제거, 재배 소속 검증, 초기화 완료 플래그.
- [SensorChannelKey](../../../src/main/java/site/yesaido/data_generator/domain/SensorChannelKey.java): EUI·타입·단위 키.
- [StandardSensorUnitConverter](../../../src/main/java/site/yesaido/data_generator/converter/StandardSensorUnitConverter.java): `°C`, `°F`, `%`, `ppm`, `lux`, 유한값 검사, 미지원 조합은 empty. `℃` 자동 별칭 변환은 현 코드에서 지원하지 않음.
- [StandardSensorUnitConverterTest](../../../src/test/java/site/yesaido/data_generator/converter/StandardSensorUnitConverterTest.java): `convertSupportedCanonicalValue`, `convertSupportedValueToCanonical`, `returnEmptyForUnsupportedConversion`.
- 교차 이력: `ba8d1c2`, `53148cf`(2026-08-12), `335b225`(2026-08-18), `2d5911c`, `b4f1b88`(2026-08-31).

## E-DG-03. MQTT 계약·비동기 경계

- [MqttTopicGenerator](../../../src/main/java/site/yesaido/data_generator/mqtt/MqttTopicGenerator.java): 등록 채널 확인, 토픽 구성요소 검증, unit은 토픽 제외.
- [MqttPayloadSerializer](../../../src/main/java/site/yesaido/data_generator/mqtt/MqttPayloadSerializer.java): 등록 unit과 `+09:00` 시간 직렬화.
- [PahoMqttPublisher](../../../src/main/java/site/yesaido/data_generator/mqtt/PahoMqttPublisher.java): `MqttAsyncClient`, `MemoryPersistence`, payload 복사, `CompletionStage` 반환, 미연결/실패/종료 처리.
- [CultivationDataGenerationService](../../../src/main/java/site/yesaido/data_generator/service/CultivationDataGenerationService.java): 채널별 발행 결과 callback, 예외 격리, 액추에이터 snapshot.
- 테스트: [토픽](../../../src/test/java/site/yesaido/data_generator/mqtt/MqttTopicGeneratorTest.java), [payload](../../../src/test/java/site/yesaido/data_generator/mqtt/MqttPayloadSerializerTest.java), [생성 서비스](../../../src/test/java/site/yesaido/data_generator/service/CultivationDataGenerationServiceTest.java).
- 결과 해석: MQTT callback 성공을 소비자 처리 완료로 해석하면 안 됨. README 기본 QoS 0의 유실 가능성 명시. 수치 개선/무손실 미확인.

## E-DG-04. snapshot 초기화·이벤트 계약

- [CultivationSensorSynchronizationService](../../../src/main/java/site/yesaido/data_generator/service/CultivationSensorSynchronizationService.java): snapshot 전체 검증 후 threshold, sensor 캐시 순서로 교체. null·중복·채널 없는 장치 등 거부.
- [Initializer](../../../src/main/java/site/yesaido/data_generator/initializer/CultivationSensorSynchronizationInitializer.java): non-local 프로필 snapshot 재시도/backoff, 성공 후 listener 시작.
- [Scheduler](../../../src/main/java/site/yesaido/data_generator/scheduler/SensorDataGenerationScheduler.java): 두 캐시 초기화 완료 전 return.
- [ThresholdInfoEventService](../../../src/main/java/site/yesaido/data_generator/service/ThresholdInfoEventService.java): 빈 목록은 종료/상태 정리, 나머지는 전체 항목 upsert, 중복 키 검증 후 적용.
- [SensorInfoEventService](../../../src/main/java/site/yesaido/data_generator/service/SensorInfoEventService.java): 이벤트 대상 채널 upsert/delete와 삭제 소속 검증.
- [RabbitListenerConfiguration](../../../src/main/java/site/yesaido/data_generator/config/RabbitListenerConfiguration.java): listener 지연 시작, concurrency 1, 최대 재시도와 recoverer 설정. 이것만으로 분산 이벤트 역순을 완전히 방지하지 않음.
- 테스트: [snapshot](../../../src/test/java/site/yesaido/data_generator/service/CultivationSensorSynchronizationServiceTest.java)의 `preserveExistingCachesWhenSnapshotIsInvalid`, [임계값 이벤트](../../../src/test/java/site/yesaido/data_generator/service/ThresholdInfoEventServiceTest.java)의 `upsertEveryThresholdInEvent`, `rejectDuplicateThresholdKey`, `stopOnlyTargetCultivationForEmptyEvent`.
- 이력: `d8b5385`, `c5a66cb`(08-14), `e712209`(08-16). 후자의 실제 diff에서 “목록 1개/4개 이상” 분기 및 “2~3개 오류” 검사가 제거됨.

## E-DG-05. 공용 환경·관측 모델·cycleId

- [SharedEnvironmentSensorValueGenerator](../../../src/main/java/site/yesaido/data_generator/generator/SharedEnvironmentSensorValueGenerator.java): plan → 환경 advance → EUI 관측 project → 등록 단위 convert.
- [EnvironmentRandomWalkGenerator](../../../src/main/java/site/yesaido/data_generator/generator/EnvironmentRandomWalkGenerator.java): 환경 키별 `ConcurrentHashMap.compute`; 같은 cycle 재사용, 이전 cycle 거부.
- [RandomWalkStepCalculator](../../../src/main/java/site/yesaido/data_generator/generator/RandomWalkStepCalculator.java): 초기값/이전값 + 난수 변화 + 액추에이터 효과, 범위 제한·반올림.
- [SensorObservationProjector](../../../src/main/java/site/yesaido/data_generator/generator/SensorObservationProjector.java): 초기 생성 bias를 유지, 이전 deviation과 새 target 혼합, 환경 + bias + deviation, 동일 cycle 재사용.
- [GenerationCycleSequence](../../../src/main/java/site/yesaido/data_generator/generator/GenerationCycleSequence.java), [Scheduler](../../../src/main/java/site/yesaido/data_generator/scheduler/SensorDataGenerationScheduler.java): 주기 ID 발급·전달.
- [EnvironmentRandomWalkGeneratorTest](../../../src/test/java/site/yesaido/data_generator/generator/EnvironmentRandomWalkGeneratorTest.java): `reuseStateForSameCycle`, `rejectStaleCycle`, `advanceSameEnvironmentAndCycleOnlyOnceConcurrently`.
- [SensorObservationProjectorTest](../../../src/test/java/site/yesaido/data_generator/generator/SensorObservationProjectorTest.java): `projectSameKeyAndCycleOnlyOnceConcurrently`, 키·주기·범위 테스트.
- [SharedEnvironmentSensorValueGeneratorTest](../../../src/test/java/site/yesaido/data_generator/generator/SharedEnvironmentSensorValueGeneratorTest.java): `convertFixedSensorObservationToRequestedUnit`, 생성 계획 부재·다른 재배/타입·잘못된 변환 거부.
- 이력: `c7abeaa`(08-27)는 기존 개별 생성기 제거 및 위 구조·관련 테스트 추가를 포함. 실제 센서 보정/현실성 정량 검증 아님.

## E-DG-06. 동적 센서 확장

- [SensorGenerationPlanResolver](../../../src/main/java/site/yesaido/data_generator/generator/SensorGenerationPlanResolver.java): 고정 타입은 표준 단위, 동적 타입은 임계값 키로 계획 조회.
- [DynamicSensorConfigurationFactory](../../../src/main/java/site/yesaido/data_generator/generator/DynamicSensorConfigurationFactory.java): 범위 폭, 중앙값, 정책 비율 기반 생성 설정. 유한 double 변환 검증.
- [SensorGenerationPlanResolverTest](../../../src/test/java/site/yesaido/data_generator/generator/SensorGenerationPlanResolverTest.java): `resolveDynamicSensorFromCultivationThreshold`, `returnEmptyWhenDynamicSensorThresholdDoesNotExist`, 미지원 고정 단위 거부.
- 이력: `30fc6c9`(08-13), `3e42ab7`(08-14).

## E-DG-07. 재배별 실행 제한

- [CultivationTaskCoordinator](../../../src/main/java/site/yesaido/data_generator/service/CultivationTaskCoordinator.java): 재배 ID Set 예약, `List.copyOf` 캡처, 실행 거절/예외/완료에서 예약 해제.
- [GeneratorExecutorConfiguration](../../../src/main/java/site/yesaido/data_generator/config/GeneratorExecutorConfiguration.java): 고정 크기 풀, 유한 큐, `AbortPolicy`.
- [CultivationTaskCoordinatorTest](../../../src/test/java/site/yesaido/data_generator/service/CultivationTaskCoordinatorTest.java): `preventDuplicateTaskUntilPreviousTaskCompletes`, `reserveOnlyOneOfConcurrentDuplicateSubmissions`, `submitDefensiveSnapshotWithCycleId`, `releaseReservationWhenExecutorRejectsTask`, `isolateGenerationFailureAndReleaseReservation`.
- 예약 키에는 cycleId를 넣지 않음. 다른 주기여도 같은 재배의 이전 작업이 남으면 제출하지 않는다. MQTT 전송 완료 대기나 분산 락은 아님.

## E-DG-08. 공유 상태 삭제

- [SharedGenerationStateLifecycle](../../../src/main/java/site/yesaido/data_generator/service/SharedGenerationStateLifecycle.java): 남은 EUI·단위 별칭 참조 확인 후 관측/환경 상태 제거.
- [SharedGenerationStateLifecycleTest](../../../src/test/java/site/yesaido/data_generator/service/SharedGenerationStateLifecycleTest.java): `keepStatesWhenSameDeviceStillHasUnitAlias`, `removeOnlyDeletedDeviceObservationWhenAnotherDeviceUsesEnvironment`, `removeObservationAndEnvironmentWhenLastFixedChannelIsDeleted`.
- 현재 삭제 순서는 SensorCache 제거 → lifecycle. 다른 참조가 쓰는 공유 상태를 조기에 지우지 않는 의도.

## E-DG-09. 가상 액추에이터

- [VirtualActuatorService](../../../src/main/java/site/yesaido/data_generator/service/VirtualActuatorService.java): 초기화 → 기존 명령 → 만료 → stale → 충돌 → 적용. exact replay는 저장된 응답 반환. 메서드 `synchronized`.
- [ActuatorCache](../../../src/main/java/site/yesaido/data_generator/cache/ActuatorCache.java): 상태·명령 이력은 인메모리 Map. 기본 상태 OFF. 재배 제거 시 명령 이력도 제거. 시간 기반 자동 정리 경로는 이 파일에서 확인되지 않음.
- [ActuatorType](../../../src/main/java/site/yesaido/data_generator/domain/ActuatorType.java): 대상 센서와 방향성 효과량, 반대 장치 정의.
- [VirtualActuatorServiceTest](../../../src/test/java/site/yesaido/data_generator/service/VirtualActuatorServiceTest.java): valid ON, replay, 동일 ID 다른 요청, expired, stale, 반대 장치 conflict, 초기화 전 요청, 상태 정리.
- 이력: `7c9dbc5`(08-08), `0b2feab`, `55f7e76`(08-25). 후자의 로그는 APPLIED/REJECTED/REPLAYED 등을 구분.
- 역할 주의: Rule Engine의 판단·제어 요청 발행 및 Notification의 외부 전달은 별도 저장소와 담당 영역이다.

## E-DG-10. 기존 테스트 보고서 읽기 결과

대상은 `Data_generator/target/surefire-reports`의 기존 `.txt` 파일이다. 새 테스트는 실행하지 않았다. 아래 숫자는 suite별 기록이며, 동일 실행 묶음·해당 실행 SHA·최신 전체 통과로 확정하지 않는다.

| suite | Tests run | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `EnvironmentRandomWalkGeneratorTest` | 19 | 0 | 0 | 0 |
| `SensorObservationProjectorTest` | 15 | 0 | 0 | 0 |
| `SharedEnvironmentSensorValueGeneratorTest` | 18 | 0 | 0 | 0 |
| `StandardSensorUnitConverterTest` | 55 | 0 | 0 | 0 |
| `CultivationTaskCoordinatorTest` | 9 | 0 | 0 | 0 |
| `VirtualActuatorServiceTest` | 10 | 0 | 0 | 0 |

이 표를 더해 “현재 전체 테스트 N개 통과”나 성능 점수로 사용하지 않는다. 본문에서는 테스트 사례를 설명하는 것이 우선이다.

## 개인 기여 확인과 경계

- 사용자 본인이 Data Generator를 담당했다고 설명했고, 주요 생성·MQTT·가상 제어·채널·snapshot·단위·공용 환경 변경에 `kim75503` 계열 author 기록이 있다.
- 해당 저장소에도 다른 팀원의 DTO·연동·CI/인프라 수정 이력이 있다. 저장소 전체를 본인 단독 개발로 표시하지 않는다.
- 커밋·라인·파일 수로 기여 퍼센트를 계산하지 않았다. author 기록은 담당 영역과 변경 시기를 교차 확인하는 근거일 뿐이다.
- AI 개발 도구 사용 여부와 사람이 직접 판단·검토·실행한 범위는 git만으로 분리할 수 없다.

## 미확인·후속 확보 목록

1. 당시 처음 문제가 드러난 실제 센서 그래프·로그와 정확한 날짜.
2. 단위 계약이 8월 31일 변경됐다가 되돌아온 팀 논의 맥락.
3. 변경 후 Rule Engine·저장·알림까지 이어진 통합 검증 기록과 운영 SHA.
4. 다중 replica와 장기 명령 이력 크기, readiness, 이벤트 순서의 후속 구현 여부.
5. MQTT QoS 등 실제 배포 설정. 문서의 기본값과 운영 설정을 동일하다고 가정하지 않는다.
6. 8월 31일 이후 다른 저장소에서 진행된 actuator/event 변경의 Data Generator 적용 여부. 현재 로컬 HEAD만으로 확인되지 않는다.

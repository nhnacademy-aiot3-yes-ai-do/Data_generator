# 김민서 개발 경험 기록 — Data Generator

> 기준일: 2026-09-14 / 조사한 로컬 코드: `b4f1b88`(2026-08-31). 이 문서는 개인 경험을 포트폴리오·자기소개서·면접 자료로 다시 활용하기 위한 기록이다. 코드 존재, 과거 변경 이력, 사용자 설명, 운영 검증을 구분한다.

## 프로젝트와 서비스 개요

MushMush는 센서 데이터, 환경 제어, 이미지 분석, 일일 피드백을 연결하는 팀 프로젝트다. 그중 Data Generator는 **실제 센서가 충분하지 않은 개발 환경에 가상 센서값을 공급하고, 가상 제어 요청의 효과를 다음 값에 반영하는 Spring Boot 서비스**다.

제작 배경은 강의실에서 충분한 장비와 실제 재배 조건을 갖추기 어려웠다는 사용자 설명에 근거한다. 이 서비스를 통해 팀의 MQTT 수집·판단·제어 연동 경로에 데이터를 공급할 수 있도록 구성했다. 실제 농장을 정밀하게 재현하거나 버섯의 생장 자체를 가속하는 모델은 아니다.

### 이 문서에서 강조하는 개인 역할

| 구분 | 내용 |
|---|---|
| 김민서 담당으로 교차 확인되는 범위 | 센서 생성, MQTT 발행, 타입·단위별 채널, snapshot 초기화, 센서·임계값 이벤트 반영, 가상 액추에이터 상태 처리, 공용 환경·관측값·cycleId 리팩터링 및 관련 테스트 |
| 팀과 함께 연결한 범위 | Cultivation의 등록 정보·임계값 계약, Rule Engine의 가상 제어 요청 계약 |
| 전체 개인 구현으로 주장하지 않을 범위 | Cultivation 전체, Rule Engine의 임계값 판단·DB 저장 전체, Notification의 사용자 알림 발송, 실제 하드웨어 검증, 인프라·CI/CD 전체 |

사용자 진술과 관련 코드의 `kim75503` 계열 author 이력은 담당 범위를 뒷받침한다. 그러나 팀원의 DTO·연동·인프라 수정도 있으므로 저장소 전체가 개인 단독·수작업 구현이라는 뜻은 아니다. 커밋 수·라인 수를 기여율로 환산하지 않는다.

## 읽는 순서

| 문서 | 담긴 내용 | 사용할 때 |
|---|---|---|
| [DEVELOPMENT_HISTORY.md](DEVELOPMENT_HISTORY.md) | 개요·흐름·시기별 작업·사건 D01~D13, 문제→선택→조치→결과→한계 | 프로젝트를 처음부터 복기할 때 |
| [RETROSPECTIVE.md](RETROSPECTIVE.md) | 설계 판단·트레이드오프·시행착오·향후 개선, 자소서 문장과 면접 질문 | 경험을 채용 자료로 압축할 때 |
| [EVIDENCE.md](EVIDENCE.md) | 코드·커밋·테스트 근거 및 미확인 사항 | 숫자·완료 여부·개인 기여를 검증할 때 |

## 현재 구현의 핵심 흐름

```text
Cultivation snapshot → 전체 응답 검증 → 센서·임계값 캐시 준비
                                        ↓
                               RabbitMQ 변경 이벤트 반영
                                        ↓
1초 생성 스케줄 → 재배별 작업 예약 → 공용 환경값(cycleId별 갱신)
                                        ↓
                              장치별 관측 편향·편차
                                        ↓
                              등록 단위 변환 → MQTT
                                                   ↓
                                         Rule Engine [팀]
                                                   ↓
                                     가상 액추에이터 제어 API
                                                   ↓
                                       다음 환경값에 효과 반영
```

**생성 주기와 전달 완료는 다르다.** 1초는 스케줄 호출 주기다. MQTT 비동기 발행이 성공했다고 Rule Engine·DB·사용자 알림까지 처리됐다는 뜻은 아니다. 같은 재배의 이전 생성 작업이 남아 있으면 다음 주기를 건너뛴다.

## 기술을 사용한 맥락

| 기술·구조 | 실제 사용 |
|---|---|
| Java / Spring Boot | 생성 스케줄, 가상 제어 API, 서비스·캐시 구성 |
| OpenFeign | 시작 시 Cultivation에서 센서·임계값 snapshot 조회 |
| RabbitMQ | 센서·임계값 등록/변경/삭제 이벤트 반영 |
| Paho MQTT | 등록 채널에 맞는 토픽·payload의 비동기 발행 |
| 인메모리 상태 / 동시성 제어 | 불변 캐시 snapshot, 재배 단위 예약, 키별 환경 상태 갱신 |
| JUnit / Mockito | 단위·주기·동시 요청·삭제·명령 재처리 등의 경계 조건 검증 |

## 코드로 들어가는 출발점

- [SensorDataGenerationScheduler](../../../src/main/java/site/yesaido/data_generator/scheduler/SensorDataGenerationScheduler.java): 초기화 여부 확인, cycleId 발급, 재배별 제출.
- [SharedEnvironmentSensorValueGenerator](../../../src/main/java/site/yesaido/data_generator/generator/SharedEnvironmentSensorValueGenerator.java): 공용 환경 → 관측값 → 등록 단위.
- [EnvironmentRandomWalkGenerator](../../../src/main/java/site/yesaido/data_generator/generator/EnvironmentRandomWalkGenerator.java): 같은 cycle의 상태 재사용.
- [CultivationTaskCoordinator](../../../src/main/java/site/yesaido/data_generator/service/CultivationTaskCoordinator.java): 중복 실행 제한과 실패 후 예약 해제.
- [CultivationSensorSynchronizationService](../../../src/main/java/site/yesaido/data_generator/service/CultivationSensorSynchronizationService.java): snapshot 전체 검증·초기화.
- [ThresholdInfoEventService](../../../src/main/java/site/yesaido/data_generator/service/ThresholdInfoEventService.java): 목록 개수 추론을 제거한 임계값 이벤트 처리.
- [VirtualActuatorService](../../../src/main/java/site/yesaido/data_generator/service/VirtualActuatorService.java): 명령 replay·만료·stale·충돌 처리.
- [CultivationTaskCoordinatorTest](../../../src/test/java/site/yesaido/data_generator/service/CultivationTaskCoordinatorTest.java): 실행 예약의 경계 사례.

## 사실 확인 등급

| 표기 | 의미 | 오해하지 않을 점 |
|---|---|---|
| 구현 확인 | 기준 코드에 동작이 존재함 | 최신 운영 배포 완료와 다름 |
| 이력 확인 | 해당 변경의 코드 diff·author date를 확인함 | 최초 발생일·회의일·배포일과 다름 |
| 기존 검증 기록 | 로컬에 남아 있는 테스트 보고서를 읽음 | 이번에 재실행한 결과나 최신 전체 통과 수가 아님 |
| 사용자 설명 | 제작 배경·담당 내용 등 대화에서 제공한 사실 | 구현 세부 전체의 독립 증거는 아님 |
| 미확인 / 해석 | 추가 증거나 본인 기억이 필요한 항목 | 실제 장애가 있었다고 꾸며 쓰지 않음 |

## 사용 전 주의사항

1. 조사 시점의 루트 README에는 이전 “채널별 독립 Random Walk” 설명이 남아 있었다. 현 코드는 공용 환경과 장치 관측값을 분리한다.
2. 기존 테스트 기록은 suite별 결과만 확인됐다. 합산해 최신 통과 건수나 성능 수치로 쓰지 않는다.
3. 로컬 HEAD만으로 8월 31일 이후 다른 서비스의 actuator/event 변경이 이 서버까지 반영됐는지 확인할 수 없다.
4. 실제 운영 주소·계정·토큰·장치 식별값을 취업 자료에 붙이지 않는다. 사례 캡처를 추가할 때 마스킹한다.
5. 내용을 외부에 제출하기 전 본인이 설명할 수 있는 판단·검증 범위, 팀 공동 기여, 실제 결과를 확인한다.


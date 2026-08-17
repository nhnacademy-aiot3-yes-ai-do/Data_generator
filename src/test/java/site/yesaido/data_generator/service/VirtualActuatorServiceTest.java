package site.yesaido.data_generator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.data_generator.cache.ActuatorCache;
import site.yesaido.data_generator.cache.SensorCache;
import site.yesaido.data_generator.domain.ActuatorCommandStatus;
import site.yesaido.data_generator.domain.ActuatorState;
import site.yesaido.data_generator.domain.ActuatorStateEntry;
import site.yesaido.data_generator.domain.ActuatorStateKey;
import site.yesaido.data_generator.domain.ActuatorType;
import site.yesaido.data_generator.dto.request.ActuatorCommandRequest;
import site.yesaido.data_generator.dto.response.ActuatorCommandResponse;
import site.yesaido.data_generator.exception.ActuatorCommandUnavailableException;
import site.yesaido.data_generator.exception.InvalidActuatorCommandException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VirtualActuatorServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-17T05:00:00Z");

    private SensorCache sensorCache;

    private ActuatorCache actuatorCache;
    private VirtualActuatorService virtualActuatorService;

    @BeforeEach
    void setUp() {
        actuatorCache = new ActuatorCache();
        sensorCache = new SensorCache();
        virtualActuatorService = new VirtualActuatorService(
                actuatorCache,
                sensorCache,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("유효한 ON 명령을 적용하고 상태와 명령 결과를 캐시에 기록한다")
    void applyValidOnCommandAndRecordResult() {
        completeInitialSynchronization();
        ActuatorCommandRequest request = createRequest(
                UUID.randomUUID(), UUID.randomUUID(), ActuatorState.ON,
                NOW.minusSeconds(10), NOW.plusSeconds(10));

        ActuatorCommandResponse response = virtualActuatorService.applyActuatorCommand(
                1L, ActuatorType.HEATER, request);

        assertThat(response.controlId()).isEqualTo(request.controlId());
        assertThat(response.commandId()).isEqualTo(request.commandId());
        assertThat(response.status()).isEqualTo(ActuatorCommandStatus.APPLIED);
        assertThat(response.actualState()).isEqualTo(ActuatorState.ON);
        assertThat(response.appliedAt()).isEqualTo(NOW);
        assertThat(actuatorCache.getActualState(
                new ActuatorStateKey(1L, ActuatorType.HEATER))).isEqualTo(ActuatorState.ON);
        assertThat(actuatorCache.findCommandRecord(request.commandId())).isPresent();
        assertThat(virtualActuatorService.getActiveActuatorTypesSnapshot(1L))
                .containsExactly(ActuatorType.HEATER);
    }

    @Test
    @DisplayName("동일한 명령을 다시 받으면 상태를 다시 적용하지 않고 기존 응답을 반환한다")
    void returnExistingResponseForExactCommandReplay() {
        completeInitialSynchronization();
        ActuatorCommandRequest request = createRequest(
                UUID.randomUUID(), UUID.randomUUID(), ActuatorState.ON,
                NOW.minusSeconds(10), NOW.plusSeconds(10));

        ActuatorCommandResponse firstResponse = virtualActuatorService.applyActuatorCommand(
                1L, ActuatorType.HEATER, request);
        ActuatorCommandResponse replayedResponse = virtualActuatorService.applyActuatorCommand(
                1L, ActuatorType.HEATER, request);

        assertThat(replayedResponse).isSameAs(firstResponse);
        assertThat(actuatorCache.getStateEntryCount()).isEqualTo(1);
        assertThat(actuatorCache.getCommandRecordCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 commandId를 다른 요청에 재사용하면 현재 상태와 함께 충돌 응답을 반환한다")
    void rejectReusedCommandIdWithDifferentRequest() {
        completeInitialSynchronization();
        UUID commandId = UUID.randomUUID();
        UUID controlId = UUID.randomUUID();
        ActuatorCommandRequest firstRequest = createRequest(
                controlId, commandId, ActuatorState.ON,
                NOW.minusSeconds(10), NOW.plusSeconds(10));
        ActuatorCommandRequest conflictingRequest = createRequest(
                controlId, commandId, ActuatorState.OFF,
                NOW.minusSeconds(5), NOW.plusSeconds(15));

        virtualActuatorService.applyActuatorCommand(1L, ActuatorType.HEATER, firstRequest);
        ActuatorCommandResponse response = virtualActuatorService.applyActuatorCommand(
                1L, ActuatorType.HEATER, conflictingRequest);

        assertThat(response.status()).isEqualTo(ActuatorCommandStatus.REJECTED_CONFLICT);
        assertThat(response.actualState()).isEqualTo(ActuatorState.ON);
        assertThat(response.appliedAt()).isNull();
        assertThat(actuatorCache.getActualState(
                new ActuatorStateKey(1L, ActuatorType.HEATER))).isEqualTo(ActuatorState.ON);
        assertThat(actuatorCache.getCommandRecordCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("현재 시각에 만료된 명령을 거절하고 실제 상태는 변경하지 않는다")
    void rejectExpiredCommand() {
        completeInitialSynchronization();
        ActuatorCommandRequest request = createRequest(
                UUID.randomUUID(), UUID.randomUUID(), ActuatorState.ON,
                NOW.minusSeconds(10), NOW);

        ActuatorCommandResponse response = virtualActuatorService.applyActuatorCommand(
                1L, ActuatorType.HEATER, request);

        assertThat(response.status()).isEqualTo(ActuatorCommandStatus.REJECTED_EXPIRED);
        assertThat(response.actualState()).isEqualTo(ActuatorState.OFF);
        assertThat(response.appliedAt()).isNull();
        assertThat(actuatorCache.getStateEntryCount()).isZero();
        assertThat(actuatorCache.findCommandRecord(request.commandId())).isPresent();
    }

    @Test
    @DisplayName("마지막 요청 시각과 같거나 오래된 명령은 stale로 거절한다")
    void rejectStaleCommand() {
        completeInitialSynchronization();
        ActuatorStateKey stateKey = new ActuatorStateKey(1L, ActuatorType.HEATER);
        Instant lastRequestedAt = NOW.minusSeconds(5);
        actuatorCache.putStateEntry(
                stateKey, new ActuatorStateEntry(ActuatorState.ON, lastRequestedAt));
        ActuatorCommandRequest request = createRequest(
                UUID.randomUUID(), UUID.randomUUID(), ActuatorState.OFF,
                lastRequestedAt, NOW.plusSeconds(10));

        ActuatorCommandResponse response = virtualActuatorService.applyActuatorCommand(
                1L, ActuatorType.HEATER, request);

        assertThat(response.status()).isEqualTo(ActuatorCommandStatus.REJECTED_STALE);
        assertThat(response.actualState()).isEqualTo(ActuatorState.ON);
        assertThat(actuatorCache.findStateEntry(stateKey))
                .contains(new ActuatorStateEntry(ActuatorState.ON, lastRequestedAt));
        assertThat(actuatorCache.findCommandRecord(request.commandId())).isPresent();
    }

    @Test
    @DisplayName("더 최신인 OFF 명령은 반대 액추에이터 상태 검사 없이 적용한다")
    void applyFreshOffCommandWithoutOppositeConflict() {
        completeInitialSynchronization();
        ActuatorStateKey heaterKey = new ActuatorStateKey(1L, ActuatorType.HEATER);
        ActuatorStateKey coolerKey = new ActuatorStateKey(1L, ActuatorType.COOLER);
        actuatorCache.putStateEntry(
                heaterKey,
                new ActuatorStateEntry(ActuatorState.ON, NOW.minusSeconds(20))
        );
        actuatorCache.putStateEntry(
                coolerKey,
                new ActuatorStateEntry(ActuatorState.ON, NOW.minusSeconds(20))
        );
        ActuatorCommandRequest request = createRequest(
                UUID.randomUUID(), UUID.randomUUID(), ActuatorState.OFF,
                NOW.minusSeconds(10), NOW.plusSeconds(10));

        ActuatorCommandResponse response = virtualActuatorService.applyActuatorCommand(
                1L, ActuatorType.HEATER, request);

        assertThat(response.status()).isEqualTo(ActuatorCommandStatus.APPLIED);
        assertThat(response.actualState()).isEqualTo(ActuatorState.OFF);
        assertThat(actuatorCache.getActualState(heaterKey)).isEqualTo(ActuatorState.OFF);
        assertThat(actuatorCache.getActualState(coolerKey)).isEqualTo(ActuatorState.ON);
    }

    @Test
    @DisplayName("반대 방향 액추에이터가 ON이면 ON 명령을 충돌로 거절한다")
    void rejectOnCommandWhenOppositeActuatorIsActive() {
        completeInitialSynchronization();
        ActuatorStateKey coolerKey = new ActuatorStateKey(1L, ActuatorType.COOLER);
        actuatorCache.putStateEntry(
                coolerKey,
                new ActuatorStateEntry(ActuatorState.ON, NOW.minusSeconds(20))
        );
        ActuatorCommandRequest request = createRequest(
                UUID.randomUUID(), UUID.randomUUID(), ActuatorState.ON,
                NOW.minusSeconds(10), NOW.plusSeconds(10));

        ActuatorCommandResponse response = virtualActuatorService.applyActuatorCommand(
                1L, ActuatorType.HEATER, request);

        assertThat(response.status()).isEqualTo(ActuatorCommandStatus.REJECTED_CONFLICT);
        assertThat(response.actualState()).isEqualTo(ActuatorState.OFF);
        assertThat(actuatorCache.findStateEntry(
                new ActuatorStateKey(1L, ActuatorType.HEATER))).isEmpty();
        assertThat(actuatorCache.findCommandRecord(request.commandId())).isPresent();
    }

    @Test
    @DisplayName("초기 센서 동기화 전에는 명령을 처리하지 않는다")
    void rejectCommandBeforeInitialSensorSynchronization() {
        ActuatorCommandRequest request = createRequest(
                UUID.randomUUID(), UUID.randomUUID(), ActuatorState.ON,
                NOW.minusSeconds(10), NOW.plusSeconds(10));

        assertThatThrownBy(() -> virtualActuatorService.applyActuatorCommand(
                1L, ActuatorType.HEATER, request))
                .isInstanceOf(ActuatorCommandUnavailableException.class);

        assertThat(actuatorCache.getStateEntryCount()).isZero();
        assertThat(actuatorCache.getCommandRecordCount()).isZero();
    }

    @Test
    @DisplayName("null 명령 요청은 다른 의존성을 사용하기 전에 거부한다")
    void rejectNullCommandRequestBeforeUsingDependencies() {
        assertThatThrownBy(() -> virtualActuatorService.applyActuatorCommand(
                1L, ActuatorType.HEATER, null))
                .isInstanceOf(InvalidActuatorCommandException.class);

        assertThat(sensorCache.isInitialSynchronizationCompleted()).isFalse();
        assertThat(sensorCache.getSensorCount()).isZero();
        assertThat(actuatorCache.getStateEntryCount()).isZero();
        assertThat(actuatorCache.getCommandRecordCount()).isZero();
    }

    @Test
    @DisplayName("재배 상태 제거를 액추에이터 캐시에 위임한다")
    void removeCultivationState() {
        ActuatorStateKey firstCultivationKey = new ActuatorStateKey(1L, ActuatorType.HEATER);
        ActuatorStateKey secondCultivationKey = new ActuatorStateKey(2L, ActuatorType.HEATER);
        actuatorCache.putStateEntry(
                firstCultivationKey,
                new ActuatorStateEntry(ActuatorState.ON, NOW.minusSeconds(10))
        );
        actuatorCache.putStateEntry(
                secondCultivationKey,
                new ActuatorStateEntry(ActuatorState.ON, NOW.minusSeconds(10))
        );

        virtualActuatorService.removeCultivationState(1L);

        assertThat(actuatorCache.findStateEntry(firstCultivationKey)).isEmpty();
        assertThat(actuatorCache.findStateEntry(secondCultivationKey)).isPresent();
    }

    private void completeInitialSynchronization() {
        sensorCache.replaceAll(List.of());
    }

    private static ActuatorCommandRequest createRequest(
            UUID controlId,
            UUID commandId,
            ActuatorState desiredState,
            Instant requestedAt,
            Instant expiresAt
    ) {
        return new ActuatorCommandRequest(
                controlId, commandId, desiredState, requestedAt, expiresAt);
    }
}

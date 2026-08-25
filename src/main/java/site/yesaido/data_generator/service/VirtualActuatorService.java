package site.yesaido.data_generator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.yesaido.data_generator.cache.ActuatorCache;
import site.yesaido.data_generator.cache.ActuatorCommandRecord;
import site.yesaido.data_generator.cache.SensorCache;
import site.yesaido.data_generator.domain.*;
import site.yesaido.data_generator.dto.request.ActuatorCommandRequest;
import site.yesaido.data_generator.dto.response.ActuatorCommandResponse;
import site.yesaido.data_generator.exception.ActuatorCommandUnavailableException;
import site.yesaido.data_generator.exception.InvalidActuatorCommandException;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 *   판정 순서는 반드시 다음과 같습니다.
 *
 *   초기 동기화 확인
 *   → 기존 commandId 확인
 *   → 만료 확인
 *   → stale 확인
 *   → 반대 장치 ON 충돌 확인
 *   → 상태 적용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualActuatorService {

    private final ActuatorCache actuatorCache;
    private final SensorCache sensorCache;
    private final Clock clock;

    public synchronized ActuatorCommandResponse applyActuatorCommand(long cultivationId, ActuatorType actuatorType, ActuatorCommandRequest actuatorCommandRequest){
        validateCommandRequest(actuatorCommandRequest);

        ActuatorStateKey actuatorStateKey = new ActuatorStateKey(cultivationId, actuatorType);

        validateCommandAvailability(actuatorStateKey, actuatorCommandRequest);

        Optional<ActuatorCommandRecord> existingCommandRecord = actuatorCache.findCommandRecord(actuatorCommandRequest.commandId());

        if (existingCommandRecord.isPresent()) {
            return resolveExistingCommand(actuatorStateKey, actuatorCommandRequest, existingCommandRecord.get());
        }

        Instant currentTime = Instant.now(clock);
        ActuatorState currentActualState = actuatorCache.getActualState(actuatorStateKey);

        if(!currentTime.isBefore(actuatorCommandRequest.expiresAt())) {
            return createAndRecordRejectedResponse(
                    actuatorStateKey,
                    actuatorCommandRequest,
                    ActuatorCommandStatus.REJECTED_EXPIRED,
                    currentActualState,
                    RejectionReason.COMMAND_EXPIRED
            );
        }

        if(isStaleCommand(actuatorStateKey, actuatorCommandRequest)) {
            return createAndRecordRejectedResponse(
                    actuatorStateKey,
                    actuatorCommandRequest,
                    ActuatorCommandStatus.REJECTED_STALE,
                    currentActualState,
                    RejectionReason.STALE_COMMAND
            );
        }

        if(hasOppositeActuatorConflict(actuatorStateKey, actuatorCommandRequest.desiredState())) {
            return createAndRecordRejectedResponse(
                    actuatorStateKey,
                    actuatorCommandRequest,
                    ActuatorCommandStatus.REJECTED_CONFLICT,
                    currentActualState,
                    RejectionReason.OPPOSITE_ACTUATOR_ACTIVE
            );
        }

        return applyCommand(actuatorStateKey,actuatorCommandRequest,currentTime, currentActualState);
    }

    public synchronized Set<ActuatorType> getActiveActuatorTypesSnapshot(long cultivationId) {
        return actuatorCache.getActiveActuatorTypesSnapshot(cultivationId);
    }

    public synchronized void removeCultivationState(long cultivationId) {
        Set<ActuatorType> previouslyActiveActuatorTypes = actuatorCache.getActiveActuatorTypesSnapshot(cultivationId);
        actuatorCache.removeByCultivationId(cultivationId);

        log.info(
                "event=ACTUATOR_STATE_CLEARED cultivationId={}, "
                        + "previouslyActiveActuatorTypes={}, reason=CULTIVATION_REMOVED",
                cultivationId,
                previouslyActiveActuatorTypes
        );
    }

    private ActuatorCommandResponse applyCommand(
            ActuatorStateKey actuatorStateKey,
            ActuatorCommandRequest actuatorCommandRequest,
            Instant appliedAt,
            ActuatorState previousCachedState
    ) {
        ActuatorStateEntry actuatorStateEntry = new ActuatorStateEntry(actuatorCommandRequest.desiredState(), actuatorCommandRequest.requestedAt());
        ActuatorCommandResponse actuatorCommandResponse = new ActuatorCommandResponse(actuatorCommandRequest.controlId(), actuatorCommandRequest.commandId(),
                ActuatorCommandStatus.APPLIED, actuatorCommandRequest.desiredState(), appliedAt);
        ActuatorCommandRecord actuatorCommandRecord = new ActuatorCommandRecord(actuatorStateKey, actuatorCommandRequest, actuatorCommandResponse);

        actuatorCache.putCommandRecord(actuatorCommandRecord);
        actuatorCache.putStateEntry(actuatorStateKey, actuatorStateEntry);

        log.info(
                "event=ACTUATOR_STATE_APPLIED cultivationId={}, actuatorType={}, "
                        + "previousCachedState={}, actualState={}, stateValueChanged={}, controlId={}, commandId={}, "
                        + "requestedAt={}, appliedAt={}, stateMutated=true, replay=false, recorded=true",
                actuatorStateKey.cultivationId(),
                actuatorStateKey.actuatorType(),
                previousCachedState,
                actuatorCommandResponse.actualState(),
                previousCachedState != actuatorCommandResponse.actualState(),
                actuatorCommandRequest.controlId(),
                actuatorCommandRequest.commandId(),
                actuatorCommandRequest.requestedAt(),
                appliedAt
        );

        return actuatorCommandResponse;
    }

    private boolean hasOppositeActuatorConflict(ActuatorStateKey actuatorStateKey, ActuatorState desiredState) {
        if( desiredState != ActuatorState.ON) {
            return false;
        }

        ActuatorStateKey oppositeActuatorStateKey = new ActuatorStateKey(actuatorStateKey.cultivationId(), actuatorStateKey.actuatorType().getOppositeType());

        return actuatorCache.getActualState(oppositeActuatorStateKey) == ActuatorState.ON;
    }

    private boolean isStaleCommand(ActuatorStateKey actuatorStateKey, ActuatorCommandRequest actuatorCommandRequest) {
        return actuatorCache.findStateEntry(actuatorStateKey)
                .map(actuatorStateEntry -> !actuatorCommandRequest.requestedAt().isAfter(actuatorStateEntry.lastRequestedAt())).orElse(false);
    }

    private ActuatorCommandResponse createAndRecordRejectedResponse(
            ActuatorStateKey actuatorStateKey,
            ActuatorCommandRequest actuatorCommandRequest,
            ActuatorCommandStatus actuatorCommandStatus,
            ActuatorState currentActualState,
            RejectionReason rejectionReason
    ) {
        if (actuatorCommandStatus == ActuatorCommandStatus.APPLIED) {
            throw new InvalidActuatorCommandException("거절 응답에는 APPLIED 상태를 사용할 수 없습니다.");
        }

        ActuatorCommandResponse actuatorCommandResponse = new ActuatorCommandResponse(actuatorCommandRequest.controlId(), actuatorCommandRequest.commandId(), actuatorCommandStatus, currentActualState, null);
        ActuatorCommandRecord actuatorCommandRecord = new ActuatorCommandRecord(actuatorStateKey, actuatorCommandRequest, actuatorCommandResponse);

        actuatorCache.putCommandRecord(actuatorCommandRecord);

        log.warn(
                "event=ACTUATOR_COMMAND_REJECTED reason={}, cultivationId={}, actuatorType={}, status={}, "
                        + "desiredState={}, actualState={}, controlId={}, commandId={}, requestedAt={}, expiresAt={}, "
                        + "stateMutated=false, recorded=true",
                rejectionReason,
                actuatorStateKey.cultivationId(),
                actuatorStateKey.actuatorType(),
                actuatorCommandStatus,
                actuatorCommandRequest.desiredState(),
                currentActualState,
                actuatorCommandRequest.controlId(),
                actuatorCommandRequest.commandId(),
                actuatorCommandRequest.requestedAt(),
                actuatorCommandRequest.expiresAt()
        );

        return actuatorCommandResponse;
    }

    private ActuatorCommandResponse resolveExistingCommand(
            ActuatorStateKey actuatorStateKey,
            ActuatorCommandRequest actuatorCommandRequest,
            ActuatorCommandRecord existingCommandRecord
    ) {
        if(existingCommandRecord.matchesCommand(actuatorStateKey,actuatorCommandRequest)) {
            ActuatorCommandResponse existingResponse = existingCommandRecord.actuatorCommandResponse();

            log.debug(
                    "event=ACTUATOR_COMMAND_REPLAYED cultivationId={}, actuatorType={}, originalStatus={}, "
                            + "actualState={}, controlId={}, commandId={}, originalAppliedAt={}, "
                            + "stateMutated=false, replay=true",
                    actuatorStateKey.cultivationId(),
                    actuatorStateKey.actuatorType(),
                    existingResponse.status(),
                    existingResponse.actualState(),
                    actuatorCommandRequest.controlId(),
                    actuatorCommandRequest.commandId(),
                    existingResponse.appliedAt()
            );

            return existingResponse;
        }
        ActuatorState currentActualState = actuatorCache.getActualState(actuatorStateKey);

        log.warn(
                "event=ACTUATOR_COMMAND_REJECTED reason=COMMAND_ID_REUSED_WITH_DIFFERENT_PAYLOAD, "
                        + "cultivationId={}, actuatorType={}, desiredState={}, actualState={}, controlId={}, "
                        + "commandId={}, originalCultivationId={}, originalActuatorType={}, originalDesiredState={}, "
                        + "stateMutated=false, recorded=false",
                actuatorStateKey.cultivationId(),
                actuatorStateKey.actuatorType(),
                actuatorCommandRequest.desiredState(),
                currentActualState,
                actuatorCommandRequest.controlId(),
                actuatorCommandRequest.commandId(),
                existingCommandRecord.actuatorStateKey().cultivationId(),
                existingCommandRecord.actuatorStateKey().actuatorType(),
                existingCommandRecord.actuatorCommandRequest().desiredState()
        );

        return new ActuatorCommandResponse(actuatorCommandRequest.controlId(), actuatorCommandRequest.commandId(), ActuatorCommandStatus.REJECTED_CONFLICT, currentActualState, null);
    }

    private void validateCommandAvailability(
            ActuatorStateKey actuatorStateKey,
            ActuatorCommandRequest actuatorCommandRequest
    ) {
        if (!sensorCache.isInitialSynchronizationCompleted()) {
            log.warn(
                    "event=ACTUATOR_COMMAND_REJECTED reason=INITIAL_SYNCHRONIZATION_INCOMPLETE, "
                            + "cultivationId={}, actuatorType={}, desiredState={}, controlId={}, commandId={}, "
                            + "stateMutated=false, recorded=false",
                    actuatorStateKey.cultivationId(),
                    actuatorStateKey.actuatorType(),
                    actuatorCommandRequest.desiredState(),
                    actuatorCommandRequest.controlId(),
                    actuatorCommandRequest.commandId()
            );
            throw new ActuatorCommandUnavailableException("초기 센서 동기화가 완료되기 전에는 액추에이터 명령을 처리할 수 없습니다.");
        }
    }

    private enum RejectionReason {
        COMMAND_EXPIRED,
        STALE_COMMAND,
        OPPOSITE_ACTUATOR_ACTIVE
    }


    private static void validateCommandRequest(ActuatorCommandRequest actuatorCommandRequest){
        if(actuatorCommandRequest == null) {
            throw new InvalidActuatorCommandException("actuatorCommandRequest는 null일 수 없습니다.");
        }
    }
}

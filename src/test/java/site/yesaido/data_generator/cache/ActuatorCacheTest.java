package site.yesaido.data_generator.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.data_generator.domain.ActuatorCommandStatus;
import site.yesaido.data_generator.domain.ActuatorState;
import site.yesaido.data_generator.domain.ActuatorStateEntry;
import site.yesaido.data_generator.domain.ActuatorStateKey;
import site.yesaido.data_generator.domain.ActuatorType;
import site.yesaido.data_generator.dto.request.ActuatorCommandRequest;
import site.yesaido.data_generator.dto.response.ActuatorCommandResponse;
import site.yesaido.data_generator.exception.ActuatorStateException;
import site.yesaido.data_generator.exception.InvalidActuatorCommandException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActuatorCacheTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-08-17T05:00:00Z");
    private static final Instant EXPIRES_AT = REQUESTED_AT.plusSeconds(30);
    private static final Instant APPLIED_AT = REQUESTED_AT.plusSeconds(1);

    private ActuatorCache actuatorCache;

    @BeforeEach
    void setUp() {
        actuatorCache = new ActuatorCache();
    }

    @Test
    @DisplayName("새 캐시는 비어 있으며 저장되지 않은 액추에이터의 실제 상태는 OFF다")
    void initializeEmptyCacheWithDefaultOffState() {
        ActuatorStateKey stateKey = new ActuatorStateKey(1L, ActuatorType.HEATER);
        UUID commandId = UUID.randomUUID();

        assertThat(actuatorCache.getStateEntryCount()).isZero();
        assertThat(actuatorCache.getCommandRecordCount()).isZero();
        assertThat(actuatorCache.findStateEntry(stateKey)).isEmpty();
        assertThat(actuatorCache.getActualState(stateKey)).isEqualTo(ActuatorState.OFF);
        assertThat(actuatorCache.findCommandRecord(commandId)).isEmpty();
    }

    @Test
    @DisplayName("상태 엔트리와 명령 레코드를 저장하고 같은 값의 재저장은 멱등하게 처리한다")
    void storeAndFindStateEntryAndCommandRecordIdempotently() {
        ActuatorStateKey stateKey = new ActuatorStateKey(1L, ActuatorType.HEATER);
        ActuatorStateEntry stateEntry = new ActuatorStateEntry(ActuatorState.ON, REQUESTED_AT);
        ActuatorCommandRecord commandRecord = createAppliedRecord(
                stateKey, UUID.randomUUID(), UUID.randomUUID(), ActuatorState.ON);

        actuatorCache.putStateEntry(stateKey, stateEntry);
        actuatorCache.putCommandRecord(commandRecord);
        actuatorCache.putCommandRecord(commandRecord);

        assertThat(actuatorCache.findStateEntry(stateKey)).contains(stateEntry);
        assertThat(actuatorCache.getActualState(stateKey)).isEqualTo(ActuatorState.ON);
        assertThat(actuatorCache.findCommandRecord(
                commandRecord.actuatorCommandRequest().commandId())).contains(commandRecord);
        assertThat(actuatorCache.getStateEntryCount()).isEqualTo(1);
        assertThat(actuatorCache.getCommandRecordCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 commandId에 다른 명령 레코드를 저장하면 거부한다")
    void rejectDifferentRecordWithExistingCommandId() {
        UUID commandId = UUID.randomUUID();
        ActuatorCommandRecord firstRecord = createAppliedRecord(
                new ActuatorStateKey(1L, ActuatorType.HEATER),
                UUID.randomUUID(),
                commandId,
                ActuatorState.ON
        );
        ActuatorCommandRecord differentRecord = createAppliedRecord(
                new ActuatorStateKey(1L, ActuatorType.COOLER),
                UUID.randomUUID(),
                commandId,
                ActuatorState.OFF
        );

        actuatorCache.putCommandRecord(firstRecord);

        assertThatThrownBy(() -> actuatorCache.putCommandRecord(differentRecord))
                .isInstanceOf(InvalidActuatorCommandException.class)
                .hasMessageContaining(commandId.toString());

        assertThat(actuatorCache.findCommandRecord(commandId)).contains(firstRecord);
        assertThat(actuatorCache.getCommandRecordCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("지정 재배에서 ON인 액추에이터 타입만 스냅샷으로 반환한다")
    void returnOnlyActiveActuatorTypesForCultivation() {
        actuatorCache.putStateEntry(
                new ActuatorStateKey(1L, ActuatorType.HEATER),
                new ActuatorStateEntry(ActuatorState.ON, REQUESTED_AT)
        );
        actuatorCache.putStateEntry(
                new ActuatorStateKey(1L, ActuatorType.COOLER),
                new ActuatorStateEntry(ActuatorState.OFF, REQUESTED_AT)
        );
        actuatorCache.putStateEntry(
                new ActuatorStateKey(2L, ActuatorType.HUMIDIFIER),
                new ActuatorStateEntry(ActuatorState.ON, REQUESTED_AT)
        );

        assertThat(actuatorCache.getActiveActuatorTypesSnapshot(1L))
                .containsExactly(ActuatorType.HEATER);
        assertThat(actuatorCache.getActiveActuatorTypesSnapshot(2L))
                .containsExactly(ActuatorType.HUMIDIFIER);
    }

    @Test
    @DisplayName("재배를 제거하면 해당 상태와 명령만 삭제하고 다른 재배 데이터는 유지한다")
    void removeOnlyStateAndCommandsForCultivation() {
        ActuatorStateKey firstStateKey = new ActuatorStateKey(1L, ActuatorType.HEATER);
        ActuatorStateKey secondStateKey = new ActuatorStateKey(2L, ActuatorType.HUMIDIFIER);
        ActuatorCommandRecord firstRecord = createAppliedRecord(
                firstStateKey, UUID.randomUUID(), UUID.randomUUID(), ActuatorState.ON);
        ActuatorCommandRecord secondRecord = createAppliedRecord(
                secondStateKey, UUID.randomUUID(), UUID.randomUUID(), ActuatorState.ON);

        actuatorCache.putStateEntry(
                firstStateKey, new ActuatorStateEntry(ActuatorState.ON, REQUESTED_AT));
        actuatorCache.putStateEntry(
                secondStateKey, new ActuatorStateEntry(ActuatorState.ON, REQUESTED_AT));
        actuatorCache.putCommandRecord(firstRecord);
        actuatorCache.putCommandRecord(secondRecord);

        actuatorCache.removeByCultivationId(1L);

        assertThat(actuatorCache.findStateEntry(firstStateKey)).isEmpty();
        assertThat(actuatorCache.findCommandRecord(
                firstRecord.actuatorCommandRequest().commandId())).isEmpty();
        assertThat(actuatorCache.findStateEntry(secondStateKey)).contains(
                new ActuatorStateEntry(ActuatorState.ON, REQUESTED_AT));
        assertThat(actuatorCache.findCommandRecord(
                secondRecord.actuatorCommandRequest().commandId())).contains(secondRecord);
        assertThat(actuatorCache.getStateEntryCount()).isEqualTo(1);
        assertThat(actuatorCache.getCommandRecordCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("캐시 작업의 null 입력과 유효하지 않은 cultivationId를 거부한다")
    void rejectNullInputsAndInvalidCultivationId() {
        ActuatorStateKey stateKey = new ActuatorStateKey(1L, ActuatorType.HEATER);
        ActuatorStateEntry stateEntry = new ActuatorStateEntry(ActuatorState.ON, REQUESTED_AT);

        assertThatThrownBy(() -> actuatorCache.findStateEntry(null))
                .isInstanceOf(ActuatorStateException.class);
        assertThatThrownBy(() -> actuatorCache.getActualState(null))
                .isInstanceOf(ActuatorStateException.class);
        assertThatThrownBy(() -> actuatorCache.putStateEntry(null, stateEntry))
                .isInstanceOf(ActuatorStateException.class);
        assertThatThrownBy(() -> actuatorCache.putStateEntry(stateKey, null))
                .isInstanceOf(ActuatorStateException.class);
        assertThatThrownBy(() -> actuatorCache.findCommandRecord(null))
                .isInstanceOf(InvalidActuatorCommandException.class);
        assertThatThrownBy(() -> actuatorCache.putCommandRecord(null))
                .isInstanceOf(InvalidActuatorCommandException.class);
        assertThatThrownBy(() -> actuatorCache.getActiveActuatorTypesSnapshot(0L))
                .isInstanceOf(ActuatorStateException.class);
        assertThatThrownBy(() -> actuatorCache.removeByCultivationId(-1L))
                .isInstanceOf(ActuatorStateException.class);
    }

    private static ActuatorCommandRecord createAppliedRecord(
            ActuatorStateKey stateKey,
            UUID controlId,
            UUID commandId,
            ActuatorState actualState
    ) {
        ActuatorCommandRequest request = new ActuatorCommandRequest(
                controlId, commandId, actualState, REQUESTED_AT, EXPIRES_AT);
        ActuatorCommandResponse response = new ActuatorCommandResponse(
                controlId,
                commandId,
                ActuatorCommandStatus.APPLIED,
                actualState,
                APPLIED_AT
        );

        return new ActuatorCommandRecord(stateKey, request, response);
    }
}

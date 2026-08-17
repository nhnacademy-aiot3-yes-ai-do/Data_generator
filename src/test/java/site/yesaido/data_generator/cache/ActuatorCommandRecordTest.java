package site.yesaido.data_generator.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.data_generator.domain.ActuatorCommandStatus;
import site.yesaido.data_generator.domain.ActuatorState;
import site.yesaido.data_generator.domain.ActuatorStateKey;
import site.yesaido.data_generator.domain.ActuatorType;
import site.yesaido.data_generator.dto.request.ActuatorCommandRequest;
import site.yesaido.data_generator.dto.response.ActuatorCommandResponse;
import site.yesaido.data_generator.exception.InvalidActuatorCommandException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActuatorCommandRecordTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-08-17T05:00:00Z");
    private static final Instant EXPIRES_AT = REQUESTED_AT.plusSeconds(30);
    private static final Instant APPLIED_AT = REQUESTED_AT.plusSeconds(1);

    @Test
    @DisplayName("유효한 명령 레코드를 생성하고 상태 키와 요청이 모두 같을 때만 같은 명령으로 판단한다")
    void createValidRecordAndMatchExactCommand() {
        UUID controlId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        ActuatorStateKey actuatorStateKey = new ActuatorStateKey(1L, ActuatorType.HEATER);
        ActuatorCommandRequest request = createRequest(controlId, commandId, ActuatorState.ON);
        ActuatorCommandResponse response = createAppliedResponse(controlId, commandId, ActuatorState.ON);

        ActuatorCommandRecord commandRecord = new ActuatorCommandRecord(actuatorStateKey, request, response);

        assertThat(commandRecord.actuatorStateKey()).isEqualTo(actuatorStateKey);
        assertThat(commandRecord.actuatorCommandRequest()).isEqualTo(request);
        assertThat(commandRecord.actuatorCommandResponse()).isEqualTo(response);
        assertThat(commandRecord.matchesCommand(actuatorStateKey, request)).isTrue();
        assertThat(commandRecord.matchesCommand(new ActuatorStateKey(1L, ActuatorType.COOLER), request)).isFalse();

        ActuatorCommandRequest differentRequest = createRequest(controlId, commandId, ActuatorState.OFF);

        assertThat(commandRecord.matchesCommand(actuatorStateKey, differentRequest)).isFalse();
    }

    @Test
    @DisplayName("거절 응답은 요청 상태와 실제 상태가 달라도 명령 레코드로 저장할 수 있다")
    void createRejectedRecordWithDifferentActualState() {
        UUID controlId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        ActuatorCommandRequest request = createRequest(controlId, commandId, ActuatorState.ON);
        ActuatorCommandResponse response = new ActuatorCommandResponse(
                controlId,
                commandId,
                ActuatorCommandStatus.REJECTED_CONFLICT,
                ActuatorState.OFF,
                null
        );

        assertThat(new ActuatorCommandRecord(
                new ActuatorStateKey(1L, ActuatorType.HEATER), request, response
        )).isNotNull();
    }

    @Test
    @DisplayName("명령 레코드의 필수 구성 요소가 null이면 거부한다")
    void rejectNullComponents() {
        UUID controlId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        ActuatorStateKey actuatorStateKey = new ActuatorStateKey(1L, ActuatorType.HEATER);
        ActuatorCommandRequest request = createRequest(controlId, commandId, ActuatorState.ON);
        ActuatorCommandResponse response = createAppliedResponse(controlId, commandId, ActuatorState.ON);

        assertThatThrownBy(() -> new ActuatorCommandRecord(null, request, response))
                .isInstanceOf(InvalidActuatorCommandException.class)
                .hasMessageContaining("actuatorStateKey");

        assertThatThrownBy(() -> new ActuatorCommandRecord(actuatorStateKey, null, response))
                .isInstanceOf(InvalidActuatorCommandException.class)
                .hasMessageContaining("actuatorCommandRequest");

        assertThatThrownBy(() -> new ActuatorCommandRecord(actuatorStateKey, request, null))
                .isInstanceOf(InvalidActuatorCommandException.class)
                .hasMessageContaining("actuatorCommandResponse");
    }

    @Test
    @DisplayName("요청과 응답의 controlId 또는 commandId가 다르면 거부한다")
    void rejectMismatchedIdentifiers() {
        UUID controlId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        ActuatorStateKey actuatorStateKey = new ActuatorStateKey(1L, ActuatorType.HEATER);
        ActuatorCommandRequest request = createRequest(controlId, commandId, ActuatorState.ON);

        ActuatorCommandResponse differentControlIdResponse = createAppliedResponse(
                UUID.randomUUID(), commandId, ActuatorState.ON);

        assertThatThrownBy(() -> new ActuatorCommandRecord(
                actuatorStateKey, request, differentControlIdResponse))
                .isInstanceOf(InvalidActuatorCommandException.class)
                .hasMessageContaining("controlId");

        ActuatorCommandResponse differentCommandIdResponse = createAppliedResponse(
                controlId, UUID.randomUUID(), ActuatorState.ON);

        assertThatThrownBy(() -> new ActuatorCommandRecord(
                actuatorStateKey, request, differentCommandIdResponse))
                .isInstanceOf(InvalidActuatorCommandException.class)
                .hasMessageContaining("commandId");
    }

    @Test
    @DisplayName("APPLIED 응답의 실제 상태가 요청 상태와 다르면 거부한다")
    void rejectAppliedResponseWithDifferentActualState() {
        UUID controlId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        ActuatorCommandRequest request = createRequest(controlId, commandId, ActuatorState.ON);
        ActuatorCommandResponse response = createAppliedResponse(controlId, commandId, ActuatorState.OFF);
        ActuatorStateKey actuatorStateKey = new ActuatorStateKey(1L, ActuatorType.HEATER);

        assertThatThrownBy(() -> new ActuatorCommandRecord(
                actuatorStateKey, request, response))
                .isInstanceOf(InvalidActuatorCommandException.class)
                .hasMessageContaining("actualState");
    }

    private static ActuatorCommandRequest createRequest(
            UUID controlId,
            UUID commandId,
            ActuatorState desiredState
    ) {
        return new ActuatorCommandRequest(
                controlId, commandId, desiredState, REQUESTED_AT, EXPIRES_AT);
    }

    private static ActuatorCommandResponse createAppliedResponse(
            UUID controlId,
            UUID commandId,
            ActuatorState actualState
    ) {
        return new ActuatorCommandResponse(
                controlId,
                commandId,
                ActuatorCommandStatus.APPLIED,
                actualState,
                APPLIED_AT
        );
    }
}

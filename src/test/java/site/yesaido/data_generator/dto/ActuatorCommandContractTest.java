package site.yesaido.data_generator.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import site.yesaido.data_generator.domain.ActuatorCommandStatus;
import site.yesaido.data_generator.domain.ActuatorState;
import site.yesaido.data_generator.dto.request.ActuatorCommandRequest;
import site.yesaido.data_generator.dto.response.ActuatorCommandResponse;
import site.yesaido.data_generator.exception.InvalidActuatorCommandException;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActuatorCommandContractTest {

    private static final UUID CONTROL_ID = UUID.fromString(
            "64238d8b-d623-45c5-a677-4576149e3187");
    private static final UUID COMMAND_ID = UUID.fromString(
            "e3f99170-b89b-4ec5-980c-1342e55eb704");
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-17T06:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-17T06:01:00Z");
    private static final Instant APPLIED_AT = Instant.parse("2026-08-17T06:00:00.100Z");

    @Test
    @DisplayName("유효한 액추에이터 명령 요청을 생성한다")
    void createValidRequest() {
        ActuatorCommandRequest request = new ActuatorCommandRequest(
                CONTROL_ID, COMMAND_ID, ActuatorState.ON, REQUESTED_AT, EXPIRES_AT);

        assertThat(request.controlId()).isEqualTo(CONTROL_ID);
        assertThat(request.commandId()).isEqualTo(COMMAND_ID);
        assertThat(request.desiredState()).isEqualTo(ActuatorState.ON);
        assertThat(request.requestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(request.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @ParameterizedTest(name = "누락 필드: {0}")
    @MethodSource("requestsWithMissingRequiredField")
    @DisplayName("명령 요청의 필수 필드가 null이면 예외가 발생한다")
    void rejectRequestWithMissingRequiredField(
            String fieldName,
            UUID controlId,
            UUID commandId,
            ActuatorState desiredState,
            Instant requestedAt,
            Instant expiresAt
    ) {
        assertThatThrownBy(() -> new ActuatorCommandRequest(
                controlId, commandId, desiredState, requestedAt, expiresAt))
                .isInstanceOf(InvalidActuatorCommandException.class)
                .hasMessageContaining(fieldName);
    }

    private static Stream<Arguments> requestsWithMissingRequiredField() {
        return Stream.of(
                Arguments.of("controlId", null, COMMAND_ID, ActuatorState.ON,
                        REQUESTED_AT, EXPIRES_AT),
                Arguments.of("commandId", CONTROL_ID, null, ActuatorState.ON,
                        REQUESTED_AT, EXPIRES_AT),
                Arguments.of("desiredState", CONTROL_ID, COMMAND_ID, null,
                        REQUESTED_AT, EXPIRES_AT),
                Arguments.of("requestedAt", CONTROL_ID, COMMAND_ID, ActuatorState.ON,
                        null, EXPIRES_AT),
                Arguments.of("expiresAt", CONTROL_ID, COMMAND_ID, ActuatorState.ON,
                        REQUESTED_AT, null)
        );
    }

    @ParameterizedTest(name = "expiresAt={0}")
    @MethodSource("nonFutureExpirationTimes")
    @DisplayName("만료 시각이 요청 시각보다 나중이 아니면 예외가 발생한다")
    void rejectNonFutureExpirationTime(Instant expiresAt) {
        assertThatThrownBy(() -> new ActuatorCommandRequest(
                CONTROL_ID, COMMAND_ID, ActuatorState.ON, REQUESTED_AT, expiresAt))
                .isInstanceOf(InvalidActuatorCommandException.class)
                .hasMessageContaining("expiresAt");
    }

    private static Stream<Instant> nonFutureExpirationTimes() {
        return Stream.of(REQUESTED_AT, REQUESTED_AT.minusSeconds(1L));
    }

    @Test
    @DisplayName("적용 응답은 적용 시각을 포함한다")
    void createAppliedResponseWithAppliedAt() {
        ActuatorCommandResponse response = new ActuatorCommandResponse(
                CONTROL_ID, COMMAND_ID, ActuatorCommandStatus.APPLIED,
                ActuatorState.ON, APPLIED_AT);

        assertThat(response.status()).isEqualTo(ActuatorCommandStatus.APPLIED);
        assertThat(response.actualState()).isEqualTo(ActuatorState.ON);
        assertThat(response.appliedAt()).isEqualTo(APPLIED_AT);
    }

    @ParameterizedTest
    @EnumSource(value = ActuatorCommandStatus.class, names = "APPLIED",
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("거절 응답은 적용 시각을 포함하지 않는다")
    void createRejectedResponseWithoutAppliedAt(ActuatorCommandStatus status) {
        ActuatorCommandResponse response = new ActuatorCommandResponse(
                CONTROL_ID, COMMAND_ID, status, ActuatorState.OFF, null);

        assertThat(response.status()).isEqualTo(status);
        assertThat(response.actualState()).isEqualTo(ActuatorState.OFF);
        assertThat(response.appliedAt()).isNull();
    }

    @ParameterizedTest(name = "누락 필드: {0}")
    @MethodSource("responsesWithMissingRequiredField")
    @DisplayName("명령 응답의 필수 필드가 null이면 예외가 발생한다")
    void rejectResponseWithMissingRequiredField(
            String fieldName,
            UUID controlId,
            UUID commandId,
            ActuatorCommandStatus status,
            ActuatorState actualState
    ) {
        assertThatThrownBy(() -> new ActuatorCommandResponse(
                controlId, commandId, status, actualState, APPLIED_AT))
                .isInstanceOf(InvalidActuatorCommandException.class)
                .hasMessageContaining(fieldName);
    }

    private static Stream<Arguments> responsesWithMissingRequiredField() {
        return Stream.of(
                Arguments.of("controlId", null, COMMAND_ID,
                        ActuatorCommandStatus.APPLIED, ActuatorState.ON),
                Arguments.of("commandId", CONTROL_ID, null,
                        ActuatorCommandStatus.APPLIED, ActuatorState.ON),
                Arguments.of("status", CONTROL_ID, COMMAND_ID, null, ActuatorState.ON),
                Arguments.of("actualState", CONTROL_ID, COMMAND_ID,
                        ActuatorCommandStatus.APPLIED, null)
        );
    }

    @Test
    @DisplayName("적용 응답에 적용 시각이 없으면 예외가 발생한다")
    void rejectAppliedResponseWithoutAppliedAt() {
        assertThatThrownBy(() -> new ActuatorCommandResponse(
                CONTROL_ID, COMMAND_ID, ActuatorCommandStatus.APPLIED,
                ActuatorState.ON, null))
                .isInstanceOf(InvalidActuatorCommandException.class)
                .hasMessageContaining("APPLIED");
    }

    @ParameterizedTest
    @EnumSource(value = ActuatorCommandStatus.class, names = "APPLIED",
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("거절 응답에 적용 시각이 있으면 예외가 발생한다")
    void rejectRejectedResponseWithAppliedAt(ActuatorCommandStatus status) {
        assertThatThrownBy(() -> new ActuatorCommandResponse(
                CONTROL_ID, COMMAND_ID, status, ActuatorState.OFF, APPLIED_AT))
                .isInstanceOf(InvalidActuatorCommandException.class)
                .hasMessageContaining("거절 응답");
    }
}

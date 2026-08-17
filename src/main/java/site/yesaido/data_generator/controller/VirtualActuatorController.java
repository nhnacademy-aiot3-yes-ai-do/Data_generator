package site.yesaido.data_generator.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.yesaido.data_generator.domain.ActuatorType;
import site.yesaido.data_generator.dto.request.ActuatorCommandRequest;
import site.yesaido.data_generator.dto.response.ActuatorCommandResponse;
import site.yesaido.data_generator.service.VirtualActuatorService;

@RestController
@RequestMapping(
        "/api/internal/cultivations/{cultivation-id}/actuators"
)
@RequiredArgsConstructor
public class VirtualActuatorController {

    private final VirtualActuatorService virtualActuatorService;

    @PutMapping("/{actuator-type}/state")
    public ResponseEntity<ActuatorCommandResponse> updateActuatorState(
            @PathVariable("cultivation-id") long cultivationId,
            @PathVariable("actuator-type")ActuatorType actuatorType,
            @RequestBody ActuatorCommandRequest actuatorCommandRequest
            ) {
        ActuatorCommandResponse actuatorCommandResponse = virtualActuatorService.applyActuatorCommand(
                cultivationId, actuatorType, actuatorCommandRequest);

        return createResponseEntity(actuatorCommandResponse);
    }

    private static ResponseEntity<ActuatorCommandResponse> createResponseEntity(ActuatorCommandResponse actuatorCommandResponse) {
        return switch (actuatorCommandResponse.status()) {
            case APPLIED -> ResponseEntity.ok(actuatorCommandResponse);
            case REJECTED_EXPIRED -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body(actuatorCommandResponse);
            case REJECTED_STALE, REJECTED_CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(actuatorCommandResponse);
        };
    }
}

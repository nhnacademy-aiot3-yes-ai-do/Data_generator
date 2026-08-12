package site.yesaido.data_generator.cache;

import org.springframework.stereotype.Service;
import site.yesaido.data_generator.domain.ActuatorState;
import site.yesaido.data_generator.domain.ActuatorStateEntry;
import site.yesaido.data_generator.domain.ActuatorStateKey;
import site.yesaido.data_generator.domain.ActuatorType;
import site.yesaido.data_generator.exception.ActuatorStateException;
import site.yesaido.data_generator.exception.InvalidActuatorCommandException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ActuatorCache {

    private final ConcurrentMap<ActuatorStateKey, ActuatorStateEntry> actuatorStateEntries = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ActuatorCommandRecord> actuatorCommandRecords = new ConcurrentHashMap<>();

    public Optional<ActuatorStateEntry> findStateEntry(ActuatorStateKey actuatorStateKey) {
        validateActuatorStateKey(actuatorStateKey);

        return Optional.ofNullable(actuatorStateEntries.get(actuatorStateKey));
    }

    public ActuatorState getActualState(ActuatorStateKey actuatorStateKey) {
        return findStateEntry(actuatorStateKey)
                .map(ActuatorStateEntry::actualState)
                .orElse(ActuatorState.OFF);
    }

    public Optional<ActuatorCommandRecord> findCommandRecord(UUID commandId) {
        if(commandId == null) {
            throw new InvalidActuatorCommandException("commandId는 null일 수 없습니다.");
        }
        return Optional.ofNullable(actuatorCommandRecords.get(commandId));
    }

    public void putStateEntry(ActuatorStateKey actuatorStateKey, ActuatorStateEntry actuatorStateEntry ) {
        validateActuatorStateKey(actuatorStateKey);
        if( actuatorStateEntry == null) {
            throw new ActuatorStateException("actuatorStateEntry는 null일 수 없습니다.");
        }

        actuatorStateEntries.put(actuatorStateKey, actuatorStateEntry);
    }


    public void putCommandRecord(ActuatorCommandRecord actuatorCommandRecord) {
        if( actuatorCommandRecord == null) {
            throw new InvalidActuatorCommandException("actuatorCommandRecord는 null일 수 없습니다.");
        }

        UUID commandId = actuatorCommandRecord.actuatorCommandRequest().commandId();

        ActuatorCommandRecord existingCommandRecord = actuatorCommandRecords.putIfAbsent(commandId, actuatorCommandRecord);

        if(existingCommandRecord != null && !existingCommandRecord.equals(actuatorCommandRecord)) {
            throw new InvalidActuatorCommandException("이미 다른 내용으로 저장된 commandId입니다.: " + commandId);
        }
    }

    public Set<ActuatorType> getActiveActuatorTypesSnapshot(long cultivationId) { //cultivationId를 기준으로 Actuator들의 상태를 조회하는
        validateCultivationId(cultivationId);

        EnumSet<ActuatorType> activeActuatorTypes = EnumSet.noneOf(ActuatorType.class);

        actuatorStateEntries.forEach(((actuatorStateKey, actuatorStateEntry) -> {
            if(actuatorStateKey.cultivationId() == cultivationId && actuatorStateEntry.actualState() == ActuatorState.ON) {
                activeActuatorTypes.add(actuatorStateKey.actuatorType());
            }
        }));

        return Set.copyOf(activeActuatorTypes);
    }




    public void removeByCultivationId(long cultivationId) {
        validateCultivationId(cultivationId);

        actuatorStateEntries.keySet().removeIf(
                actuatorStateKey -> actuatorStateKey.cultivationId() == cultivationId);
        actuatorCommandRecords.values().removeIf(
                actuatorCommandRecord -> actuatorCommandRecord.actuatorStateKey().cultivationId() == cultivationId);
    }

    public int getStateEntryCount() {
        return actuatorStateEntries.size();
    }

    public int getCommandRecordCount() {
        return actuatorCommandRecords.size();
    }

    private static void validateActuatorStateKey(ActuatorStateKey actuatorStateKey) {
        if(actuatorStateKey == null) {
            throw new ActuatorStateException("actuatorStateKey는 null일 수 없습니다.");
        }
    }

    private static void validateCultivationId(long cultivationId) {
        if( cultivationId <= 0) {
            throw new ActuatorStateException("cultivationId는 0보다 커야 합니다.");
        }
    }
}

package site.yesaido.data_generator.generator;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

/*
 * 초기 동기화 완료 후 센서 snapshot이 비어 있지 않은 scheduler tick마다
 * 모든 cultivation이 공유할 단조 증가 cycleId를 하나 발급합니다.
 */
@Component
public final class GenerationCycleSequence {

    private final AtomicLong lastIssuedCycleId;

    public GenerationCycleSequence() {
        this(0L);
    }

    // 경계값과 overflow를 테스트하기 위한 package-private 생성자
    GenerationCycleSequence(long initialCycleId) {
        if (initialCycleId < 0) {
            throw new SensorDataGenerationException("initialCycleId는 0 이상이어야 합니다.");
        }

        this.lastIssuedCycleId = new AtomicLong(initialCycleId);
    }

    public long nextCycleId() {
        while (true) {
            long currentCycleId = lastIssuedCycleId.get();

            if (currentCycleId == Long.MAX_VALUE) {
                throw new SensorDataGenerationException("더 이상 생성 주기 ID를 발급할 수 없습니다.");
            }

            long nextCycleId = currentCycleId + 1L;

            if (lastIssuedCycleId.compareAndSet(currentCycleId, nextCycleId)) {
                return nextCycleId;
            }
        }
    }
}

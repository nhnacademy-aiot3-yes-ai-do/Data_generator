package site.yesaido.data_generator.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

class GenerationCycleSequenceTest {

    @Test
    void startAtOneAndIncreaseMonotonically() {
        GenerationCycleSequence sequence = new GenerationCycleSequence();

        assertThat(sequence.nextCycleId()).isEqualTo(1L);
        assertThat(sequence.nextCycleId()).isEqualTo(2L);
        assertThat(sequence.nextCycleId()).isEqualTo(3L);
    }

    @Test
    void continueFromSpecifiedInitialCycleId() {
        GenerationCycleSequence sequence = new GenerationCycleSequence(41L);

        assertThat(sequence.nextCycleId()).isEqualTo(42L);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, Long.MIN_VALUE})
    void rejectNegativeInitialCycleId(long initialCycleId) {
        assertThatThrownBy(() -> new GenerationCycleSequence(initialCycleId))
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("initialCycleId");
    }

    @Test
    void rejectIssuingCycleIdAfterMaximumValue() {
        GenerationCycleSequence sequence = new GenerationCycleSequence(Long.MAX_VALUE);

        assertThatThrownBy(sequence::nextCycleId)
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("더 이상");

        // 실패 후에도 overflow된 음수 상태로 변경되지 않아야 합니다.
        assertThatThrownBy(sequence::nextCycleId)
                .isInstanceOf(SensorDataGenerationException.class)
                .hasMessageContaining("더 이상");
    }

    @Test
    void issueUniqueCycleIdsUnderConcurrentRequests()
            throws Exception {int requestCount = 100;

        GenerationCycleSequence sequence = new GenerationCycleSequence();
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Long> issuedCycleIds = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            List<Future<Long>> futures = IntStream.range(0, requestCount)
                    .mapToObj(ignored ->
                            executor.submit(() -> {startSignal.await();
                                return sequence.nextCycleId();
                            }))
                    .toList();

            startSignal.countDown();

            for (Future<Long> future : futures) {
                issuedCycleIds.add(future.get(5L, TimeUnit.SECONDS));
            }
        }

        List<Long> expectedCycleIds = LongStream.rangeClosed(1L, requestCount)
                .boxed()
                .toList();

        assertThat(issuedCycleIds).hasSize(requestCount)
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(expectedCycleIds);
    }
}

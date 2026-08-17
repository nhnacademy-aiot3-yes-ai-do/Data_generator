package site.yesaido.data_generator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import site.yesaido.data_generator.domain.SensorCacheEntry;
import site.yesaido.data_generator.domain.SensorTypeSpec;
import site.yesaido.data_generator.exception.SensorDataGenerationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CultivationTaskCoordinatorTest {

    @Mock
    private CultivationDataGenerationService cultivationDataGenerationService;

    @Mock
    private TaskExecutor taskExecutor;

    private CultivationTaskCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new CultivationTaskCoordinator(cultivationDataGenerationService, taskExecutor);
    }

    @Test
    @DisplayName("같은 cultivation 작업은 앞선 작업이 끝날 때까지 중복 제출하지 않는다")
    void preventDuplicateTaskUntilPreviousTaskCompletes() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        SensorCacheEntry entry = cacheEntry(1L, "device-A");

        assertThat(coordinator.submitGenerationTask(1L, List.of(entry))).isTrue();
        assertThat(coordinator.submitGenerationTask(1L, List.of(entry))).isFalse();
        verify(taskExecutor).execute(taskCaptor.capture());

        taskCaptor.getValue().run();

        assertThat(coordinator.submitGenerationTask(1L, List.of(entry))).isTrue();
        verify(taskExecutor, times(2)).execute(any(Runnable.class));
    }

    @Test
    @DisplayName("같은 cultivation을 동시에 제출해도 하나만 예약한다")
    void reserveOnlyOneOfConcurrentDuplicateSubmissions() throws Exception {
        SensorCacheEntry entry = cacheEntry(1L, "device-A");
        CountDownLatch callersReady = new CountDownLatch(2);
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService callers = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> firstResult = callers.submit(
                    () -> submitAfterSignal(callersReady, startSignal, entry)
            );
            Future<Boolean> secondResult = callers.submit(
                    () -> submitAfterSignal(callersReady, startSignal, entry)
            );

            assertThat(callersReady.await(5, TimeUnit.SECONDS)).isTrue();
            startSignal.countDown();

            assertThat(List.of(
                    firstResult.get(5, TimeUnit.SECONDS),
                    secondResult.get(5, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(true, false);
            verify(taskExecutor).execute(any(Runnable.class));
        } finally {
            startSignal.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    @DisplayName("제출할 때 센서 목록을 불변 snapshot으로 복사한다")
    void submitDefensiveSnapshot() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        SensorCacheEntry entry = cacheEntry(1L, "device-A");
        List<SensorCacheEntry> mutableEntries = new ArrayList<>(List.of(entry));

        coordinator.submitGenerationTask(1L, mutableEntries);
        verify(taskExecutor).execute(taskCaptor.capture());
        mutableEntries.clear();

        taskCaptor.getValue().run();

        verify(cultivationDataGenerationService).generateAndPublishSensorData(1L, List.of(entry));
    }

    @Test
    @DisplayName("생성 서비스 실패를 작업 경계에서 격리하고 예약을 해제한다")
    void isolateGenerationFailureAndReleaseReservation() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        SensorCacheEntry entry = cacheEntry(1L, "device-A");
        doThrow(new IllegalStateException("의도적인 생성 실패"))
                .when(cultivationDataGenerationService)
                .generateAndPublishSensorData(1L, List.of(entry));

        assertThat(coordinator.submitGenerationTask(1L, List.of(entry))).isTrue();
        verify(taskExecutor).execute(taskCaptor.capture());

        assertThatCode(() -> taskCaptor.getValue().run()).doesNotThrowAnyException();
        assertThat(coordinator.submitGenerationTask(1L, List.of(entry))).isTrue();
    }

    @Test
    @DisplayName("Executor가 작업을 거절하면 false를 반환하고 예약을 해제한다")
    void releaseReservationWhenExecutorRejectsTask() {
        SensorCacheEntry entry = cacheEntry(1L, "device-A");
        doThrow(new RejectedExecutionException("queue full"))
                .doNothing()
                .when(taskExecutor)
                .execute(any(Runnable.class));

        assertThat(coordinator.submitGenerationTask(1L, List.of(entry))).isFalse();
        assertThat(coordinator.submitGenerationTask(1L, List.of(entry))).isTrue();
    }

    @Test
    @DisplayName("Executor의 일반 실행 예외는 전달하되 예약은 해제한다")
    void rethrowRuntimeExceptionAndReleaseReservation() {
        SensorCacheEntry entry = cacheEntry(1L, "device-A");
        IllegalStateException failure = new IllegalStateException("executor failure");
        doThrow(failure)
                .doNothing()
                .when(taskExecutor)
                .execute(any(Runnable.class));

        assertThatThrownBy(() -> coordinator.submitGenerationTask(1L, List.of(entry)))
                .isSameAs(failure);
        assertThat(coordinator.submitGenerationTask(1L, List.of(entry))).isTrue();
    }

    @Test
    @DisplayName("cultivation 작업 입력의 id, 목록, 요소와 소속을 검증한다")
    void validateTaskArguments() {
        SensorCacheEntry entry = cacheEntry(1L, "device-A");

        assertThatThrownBy(() -> coordinator.submitGenerationTask(0L, List.of(entry)))
                .isInstanceOf(SensorDataGenerationException.class);
        assertThatThrownBy(() -> coordinator.submitGenerationTask(1L, null))
                .isInstanceOf(SensorDataGenerationException.class);
        assertThatThrownBy(() -> coordinator.submitGenerationTask(1L, java.util.Arrays.asList((SensorCacheEntry) null)))
                .isInstanceOf(SensorDataGenerationException.class);
        assertThatThrownBy(() -> coordinator.submitGenerationTask(2L, List.of(entry)))
                .isInstanceOf(SensorDataGenerationException.class);

        verify(taskExecutor, never()).execute(any(Runnable.class));
    }

    @Test
    @DisplayName("빈 센서 목록도 유효한 cultivation 작업으로 제출한다")
    void acceptEmptySensorList() {
        assertThat(coordinator.submitGenerationTask(1L, List.of())).isTrue();

        verify(taskExecutor).execute(any(Runnable.class));
    }

    private static SensorCacheEntry cacheEntry(long cultivationId, String deviceEui) {
        return new SensorCacheEntry(
                cultivationId,
                deviceEui,
                "device-name",
                "location",
                "location-detail",
                "model",
                Set.of(new SensorTypeSpec("TEMPERATURE", "°C"))
        );
    }

    private boolean submitAfterSignal(
            CountDownLatch callersReady,
            CountDownLatch startSignal,
            SensorCacheEntry entry
    ) throws InterruptedException {
        callersReady.countDown();
        startSignal.await();
        return coordinator.submitGenerationTask(1L, List.of(entry));
    }
}

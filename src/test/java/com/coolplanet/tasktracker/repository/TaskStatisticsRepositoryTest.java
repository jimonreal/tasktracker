package com.coolplanet.tasktracker.repository;

import com.coolplanet.tasktracker.model.TaskStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Testcontainers
class TaskStatisticsRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tasktracker_repo_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TaskStatisticsRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void firstRecording_createsRow_withCountOne() {
        repository.recordDuration("task-A", 500.0);

        Optional<TaskStatistics> result = repository.findById("task-A");
        assertThat(result).isPresent();
        assertThat(result.get().getCount()).isEqualTo(1);
    }

    @Test
    void firstRecording_setsInitialMean_equalToFirstValue() {
        repository.recordDuration("task-A", 750.0);

        double mean = repository.findById("task-A").get().getMeanDurationMs();
        assertThat(mean).isEqualTo(750.0);
    }

    @Test
    void firstRecording_withZeroDuration_createsRowWithZeroMean() {
        repository.recordDuration("task-zero", 0.0);

        TaskStatistics stats = repository.findById("task-zero").get();
        assertThat(stats.getCount()).isEqualTo(1);
        assertThat(stats.getMeanDurationMs()).isEqualTo(0.0);
    }

    @Test
    void welford_twoValues_computesCorrectMean() {
        // mean([100, 300]) == 200
        repository.recordDuration("task-B", 100.0);
        repository.recordDuration("task-B", 300.0);

        TaskStatistics stats = repository.findById("task-B").get();
        assertThat(stats.getCount()).isEqualTo(2);
        assertThat(stats.getMeanDurationMs()).isEqualTo(200.0);
    }

    @Test
    void welford_threeValues_computesCorrectMean() {
        // TDD: mean([100, 200, 300]) == 200
        List.of(100.0, 200.0, 300.0).forEach(d -> repository.recordDuration("task-C", d));

        TaskStatistics stats = repository.findById("task-C").get();
        assertThat(stats.getCount()).isEqualTo(3);
        assertThat(stats.getMeanDurationMs()).isEqualTo(200.0);
    }

    @Test
    void welford_identicalValues_meanEqualsEachValue() {
        // mean([42, 42, 42, 42]) == 42
        for (int i = 0; i < 4; i++) {
            repository.recordDuration("task-D", 42.0);
        }

        assertThat(repository.findById("task-D").get().getMeanDurationMs()).isEqualTo(42.0);
    }

    @Test
    void welford_orderIndependence_sameResultForDifferentInsertOrder() {
        // mean is commutative — order of inserts does not matter
        List.of(300.0, 100.0, 200.0).forEach(d -> repository.recordDuration("task-asc", d));
        List.of(100.0, 200.0, 300.0).forEach(d -> repository.recordDuration("task-desc", d));

        double meanAsc  = repository.findById("task-asc").get().getMeanDurationMs();
        double meanDesc = repository.findById("task-desc").get().getMeanDurationMs();
        assertThat(meanAsc).isEqualTo(meanDesc);
    }

    @Test
    void welford_hundredValues_remainsNumericallyAccurate() {
        // running mean stays correct over many updates
        int n = 100;
        for (int i = 1; i <= n; i++) {
            repository.recordDuration("task-large", (double) i * 10);
        }
        double expected = ((n + 1) / 2.0) * 10; // arithmetic mean of 10,20,…,1000

        assertThat(repository.findById("task-large").get().getMeanDurationMs())
                .isCloseTo(expected, within(1e-6));
    }

    @Test
    void differentTaskIds_areStoredIndependently() {
        repository.recordDuration("task-X", 100.0);
        repository.recordDuration("task-Y", 999.0);

        assertThat(repository.findById("task-X").get().getMeanDurationMs()).isEqualTo(100.0);
        assertThat(repository.findById("task-Y").get().getMeanDurationMs()).isEqualTo(999.0);
    }

    @Test
    void findById_returnsEmpty_forUnseenTaskId() {
        assertThat(repository.findById("never-recorded")).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRecordings_doNotCorruptCountOrMean() throws InterruptedException {
        /*
         * Fires 20 threads simultaneously, each recording the same duration (100 ms).
         * The final mean must still be exactly 100 ms and count must be 20,
         * proving the atomic SQL upsert handles concurrent writes correctly.
         */
        int threads = 20;
        double duration = 100.0;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go    = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    repository.recordDuration("concurrent-task", duration);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        go.countDown();
        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        try {
            TaskStatistics stats = repository.findById("concurrent-task").orElseThrow();
            assertThat(stats.getCount()).isEqualTo(threads);
            assertThat(stats.getMeanDurationMs()).isEqualTo(duration);
        } finally {
            repository.deleteById("concurrent-task");
        }
    }
}

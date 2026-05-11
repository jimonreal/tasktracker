package com.coolplanet.tasktracker.integration;

import com.coolplanet.tasktracker.dto.TaskDurationRequest;
import com.coolplanet.tasktracker.repository.TaskStatisticsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class TaskIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tasktracker_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TaskStatisticsRepository repository;

    private static final String BASE = "/api/v1/tasks";

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void singleDuration_averageEqualsItself() throws Exception {
        postDuration("task-A", 1000L);

        mockMvc.perform(get(BASE + "/task-A/average"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value("task-A"))
                .andExpect(jsonPath("$.averageDurationMs").value(1000.0));
    }

    @Test
    void threeDurations_averageIsCorrect() throws Exception {
        postDuration("task-B", 100L);
        postDuration("task-B", 200L);
        postDuration("task-B", 300L);

        mockMvc.perform(get(BASE + "/task-B/average"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageDurationMs").value(200.0));
    }

    @Test
    void hundredDurations_averageIsCorrect() throws Exception {
        long sum = 0;
        for (int i = 1; i <= 100; i++) {
            long d = (long) i * 10;
            postDuration("task-C", d);
            sum += d;
        }
        double expected = sum / 100.0;

        mockMvc.perform(get(BASE + "/task-C/average"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageDurationMs").value(expected));
    }

    @Test
    void tasksDontInterfereWithEachOther() throws Exception {
        postDuration("task-X", 100L);
        postDuration("task-Y", 999L);

        mockMvc.perform(get(BASE + "/task-X/average"))
                .andExpect(jsonPath("$.averageDurationMs").value(100.0));
        mockMvc.perform(get(BASE + "/task-Y/average"))
                .andExpect(jsonPath("$.averageDurationMs").value(999.0));
    }

    @Test
    void recordedData_persistsInDatabase() throws Exception {
        postDuration("task-persist", 500L);
        postDuration("task-persist", 1500L);

        assertThat(repository.findById("task-persist"))
                .isPresent()
                .hasValueSatisfying(stats -> {
                    assertThat(stats.getCount()).isEqualTo(2);
                    assertThat(stats.getMeanDurationMs()).isEqualTo(1000.0);
                });
    }

    @Test
    void getAverage_returns404_forUnknownTask() throws Exception {
        mockMvc.perform(get(BASE + "/nonexistent/average"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void recordDuration_returns400_forNegativeValue() throws Exception {
        mockMvc.perform(post(BASE + "/task-D/durations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationMs\": -5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentHttpWrites_doNotCorruptAverage() throws InterruptedException {
        /*
         * 30 threads each POST the same duration (200 ms) for the same task.
         * After all threads complete the mean must still be 200 ms and
         * count must equal 30 — proving the atomic SQL upsert is safe
         * end-to-end (HTTP → service → repo → DB).
         */
        int threads = 30;
        long durationMs = 200L;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go    = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    mockMvc.perform(post(BASE + "/concurrent/durations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"durationMs\":" + durationMs + "}"));
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        go.countDown();
        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        assertThat(repository.findById("concurrent"))
                .isPresent()
                .hasValueSatisfying(stats -> {
                    assertThat(stats.getCount()).isEqualTo(threads);
                    assertThat(stats.getMeanDurationMs()).isEqualTo(durationMs);
                });
    }

    private void postDuration(String taskId, long durationMs) throws Exception {
        mockMvc.perform(post(BASE + "/" + taskId + "/durations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskDurationRequest(durationMs))))
                .andExpect(status().isOk());
    }
}

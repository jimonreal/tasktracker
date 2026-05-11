package com.coolplanet.tasktracker.service;

import com.coolplanet.tasktracker.dto.TaskAverageResponse;
import com.coolplanet.tasktracker.exception.TaskNotFoundException;
import com.coolplanet.tasktracker.model.TaskStatistics;
import com.coolplanet.tasktracker.repository.TaskStatisticsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskStatisticsRepository repository;

    @InjectMocks
    private TaskServiceImpl taskService;

    private static final String TASK_ID = "task-001";
    private static final Instant NOW = Instant.now();

    @Test
    void recordDuration_delegatesCorrectly_toRepository() {
        taskService.recordDuration(TASK_ID, 500L);

        verify(repository).recordDuration(TASK_ID, 500.0);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void recordDuration_withZeroMs_isForwardedToRepository() {
        taskService.recordDuration(TASK_ID, 0L);

        verify(repository).recordDuration(TASK_ID, 0.0);
    }

    @ParameterizedTest(name = "duration={0}")
    @ValueSource(longs = {1L, 1_000L, 60_000L, 3_600_000L})
    void recordDuration_withAnyPositiveMs_isForwardedToRepository(long durationMs) {
        taskService.recordDuration(TASK_ID, durationMs);

        verify(repository).recordDuration(TASK_ID, (double) durationMs);
    }

    @Test
    void recordDuration_doesNotRead_fromRepository() {
        taskService.recordDuration(TASK_ID, 100L);

        verify(repository, never()).findById(any());
    }

    @Test
    void getAverage_returnsTaskId_fromEntity() {
        when(repository.findById(TASK_ID))
                .thenReturn(Optional.of(stubStats(TASK_ID, 5, 300.0)));

        TaskAverageResponse response = taskService.getAverage(TASK_ID);

        assertThat(response.taskId()).isEqualTo(TASK_ID);
    }

    @Test
    void getAverage_returnsAverageDuration_fromEntity() {
        when(repository.findById(TASK_ID))
                .thenReturn(Optional.of(stubStats(TASK_ID, 3, 200.0)));

        TaskAverageResponse response = taskService.getAverage(TASK_ID);

        assertThat(response.averageDurationMs()).isEqualTo(200.0);
    }

    @Test
    void getAverage_returnsZero_whenMeanIsZero() {
        when(repository.findById(TASK_ID))
                .thenReturn(Optional.of(stubStats(TASK_ID, 1, 0.0)));

        assertThat(taskService.getAverage(TASK_ID).averageDurationMs()).isEqualTo(0.0);
    }

    @Test
    void getAverage_propagatesLargeCount_withoutLoss() {
        long largeCount = 1_000_000L;
        when(repository.findById(TASK_ID))
                .thenReturn(Optional.of(stubStats(TASK_ID, largeCount, 42.5)));

        assertThat(taskService.getAverage(TASK_ID).averageDurationMs()).isEqualTo(42.5);
    }

    @Test
    void getAverage_throwsTaskNotFoundException_whenTaskDoesNotExist() {
        when(repository.findById(TASK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getAverage(TASK_ID))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void getAverage_exceptionMessage_containsTaskId() {
        when(repository.findById(TASK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getAverage(TASK_ID))
                .hasMessageContaining(TASK_ID);
    }

    @Test
    void getAverage_forDifferentUnknownTasks_includesCorrectIdInMessage() {
        String otherId = "another-task";
        when(repository.findById(otherId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getAverage(otherId))
                .hasMessageContaining(otherId);
    }

    private TaskStatistics stubStats(String taskId, long count, double mean) {
        return new TaskStatistics(taskId, count, mean, NOW, NOW);
    }
}

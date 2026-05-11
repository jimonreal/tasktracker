package com.coolplanet.tasktracker.service;

import com.coolplanet.tasktracker.dto.TaskAverageResponse;
import com.coolplanet.tasktracker.exception.TaskNotFoundException;
import com.coolplanet.tasktracker.model.TaskStatistics;
import com.coolplanet.tasktracker.repository.TaskStatisticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaskServiceImpl implements TaskService {

    private final TaskStatisticsRepository repository;

    public TaskServiceImpl(TaskStatisticsRepository repository) {
        this.repository = repository;
    }

    @Override
    public void recordDuration(String taskId, long durationMs) {
        repository.recordDuration(taskId, (double) durationMs);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskAverageResponse getAverage(String taskId) {
        TaskStatistics stats = repository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
        return new TaskAverageResponse(stats.getTaskId(), stats.getMeanDurationMs());
    }
}

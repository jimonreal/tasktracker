package com.coolplanet.tasktracker.service;

import com.coolplanet.tasktracker.dto.TaskAverageResponse;

public interface TaskService {

    void recordDuration(String taskId, long durationMs);

    TaskAverageResponse getAverage(String taskId);
}

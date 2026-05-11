package com.coolplanet.tasktracker.dto;

public record TaskAverageResponse(
        String taskId,
        double averageDurationMs
) {}

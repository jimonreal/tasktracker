package com.coolplanet.tasktracker.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TaskDurationRequest(
        @NotNull(message = "durationMs must not be null")
        @Min(value = 0, message = "durationMs must be zero or positive")
        Long durationMs
) {}

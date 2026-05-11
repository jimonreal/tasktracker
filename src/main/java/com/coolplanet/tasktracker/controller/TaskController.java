package com.coolplanet.tasktracker.controller;

import com.coolplanet.tasktracker.dto.TaskAverageResponse;
import com.coolplanet.tasktracker.dto.TaskDurationRequest;
import com.coolplanet.tasktracker.service.TaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tasks")
@Validated
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/{taskId}/durations")
    public ResponseEntity<Void> recordDuration(
            @PathVariable @NotBlank(message = "taskId must not be blank") String taskId,
            @Valid @RequestBody TaskDurationRequest request) {
        taskService.recordDuration(taskId, request.durationMs());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{taskId}/average")
    public ResponseEntity<TaskAverageResponse> getAverage(
            @PathVariable @NotBlank(message = "taskId must not be blank") String taskId) {
        return ResponseEntity.ok(taskService.getAverage(taskId));
    }
}

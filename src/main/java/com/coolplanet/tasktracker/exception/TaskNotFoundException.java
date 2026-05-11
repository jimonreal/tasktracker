package com.coolplanet.tasktracker.exception;

public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(String taskId) {
        super("Task '%s' not found".formatted(taskId));
    }
}

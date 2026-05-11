package com.coolplanet.tasktracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "task_statistics")
public class TaskStatistics {

    @Id
    @Column(name = "task_id", nullable = false)
    private String taskId;

    @Column(name = "count", nullable = false)
    private long count;

    @Column(name = "mean_duration_ms", nullable = false)
    private double meanDurationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TaskStatistics() {}

    public TaskStatistics(String taskId, long count, double meanDurationMs, Instant createdAt, Instant updatedAt) {
        this.taskId = taskId;
        this.count = count;
        this.meanDurationMs = meanDurationMs;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getTaskId() { return taskId; }
    public long getCount() { return count; }
    public double getMeanDurationMs() { return meanDurationMs; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

package com.coolplanet.tasktracker.repository;

import com.coolplanet.tasktracker.model.TaskStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface TaskStatisticsRepository extends JpaRepository<TaskStatistics, String> {

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO task_statistics (task_id, count, mean_duration_ms, created_at, updated_at)
            VALUES (:taskId, 1, :durationMs, NOW(), NOW())
            ON CONFLICT (task_id) DO UPDATE
              SET count            = task_statistics.count + 1,
                  mean_duration_ms = task_statistics.mean_duration_ms
                                   + (:durationMs - task_statistics.mean_duration_ms)
                                   / (task_statistics.count + 1),
                  updated_at       = NOW()
            """)
    void recordDuration(@Param("taskId") String taskId, @Param("durationMs") double durationMs);
}

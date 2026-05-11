CREATE TABLE IF NOT EXISTS task_statistics (
    task_id         VARCHAR(255)     NOT NULL,
    count           BIGINT           NOT NULL DEFAULT 0,
    mean_duration_ms DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_task_statistics PRIMARY KEY (task_id),
    CONSTRAINT chk_count_non_negative CHECK (count >= 0),
    CONSTRAINT chk_mean_non_negative  CHECK (mean_duration_ms >= 0)
);

CREATE INDEX IF NOT EXISTS idx_task_statistics_updated_at ON task_statistics (updated_at);

CREATE TABLE IF NOT EXISTS agent_report_cleanup_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cleanup_run_id VARCHAR(64) NOT NULL,
    cutoff_at DATETIME(6) NOT NULL,
    batch_size INT NOT NULL,
    selected_report_count INT NOT NULL DEFAULT 0,
    deleted_report_run_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    failure_message VARCHAR(500) NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_report_cleanup_run_id (cleanup_run_id),
    KEY idx_agent_report_cleanup_status_time (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_report_execution_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    execution_ref VARCHAR(64) NOT NULL,
    report_definition_id VARCHAR(100) NOT NULL,
    owner_user_id INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    duration_ms BIGINT NOT NULL,
    partial_data TINYINT(1) NOT NULL DEFAULT 0,
    failure_code VARCHAR(64) NULL,
    failure_summary VARCHAR(255) NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_report_execution_ref (execution_ref),
    KEY idx_agent_report_execution_started (started_at),
    KEY idx_agent_report_execution_status_started (status, started_at),
    KEY idx_agent_report_execution_definition_started (report_definition_id, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inventory_history_job_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_run_id VARCHAR(64) NOT NULL,
    business_date DATE NOT NULL,
    job_mode VARCHAR(16) NOT NULL,
    trigger_source VARCHAR(40) NOT NULL,
    status VARCHAR(24) NOT NULL,
    snapshot_run_id VARCHAR(64) NULL,
    snapshot_reused TINYINT(1) NOT NULL DEFAULT 0,
    reconciliation_run_id VARCHAR(64) NULL,
    reconciliation_status VARCHAR(32) NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    executor_instance VARCHAR(128) NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_history_job_run_id (job_run_id),
    KEY idx_inventory_history_job_date (business_date, started_at),
    KEY idx_inventory_history_job_status (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存历史日任务运行证据';

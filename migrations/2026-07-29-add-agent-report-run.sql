CREATE TABLE IF NOT EXISTS agent_report_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    report_run_id VARCHAR(64) NOT NULL,
    owner_user_id INT NOT NULL,
    owner_display_name VARCHAR(100) NOT NULL,
    report_definition_id VARCHAR(100) NOT NULL,
    report_version INT NOT NULL,
    required_permission VARCHAR(100) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    generated_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_report_run_id (report_run_id),
    KEY idx_agent_report_run_owner_created (owner_user_id, created_at),
    KEY idx_agent_report_run_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_report_export_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    audit_ref VARCHAR(64) NOT NULL,
    report_run_id VARCHAR(64) NOT NULL,
    exporter_user_id INT NOT NULL,
    exporter_display_name VARCHAR(100) NOT NULL,
    export_format VARCHAR(20) NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    exported_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_report_export_audit_ref (audit_ref),
    KEY idx_agent_report_export_run_time (report_run_id, exported_at),
    KEY idx_agent_report_export_user_time (exporter_user_id, exported_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

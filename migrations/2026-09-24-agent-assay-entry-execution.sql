-- Dedicated assay confirmation/result/audit. No business permission grants and no assay schema changes.
CREATE TABLE IF NOT EXISTS agent_assay_entry_confirmation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    confirmation_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    preview_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_user_id INT NOT NULL,
    agent_session_id VARCHAR(64) NOT NULL,
    content_digest CHAR(64) CHARACTER SET ascii NOT NULL,
    token_digest CHAR(64) CHARACTER SET ascii NOT NULL,
    idempotency_digest CHAR(64) CHARACTER SET ascii NOT NULL,
    status VARCHAR(24) NOT NULL,
    confirmed_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    assay_id INT NULL,
    result_json LONGTEXT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_assay_entry_confirmation_ref (confirmation_ref),
    UNIQUE KEY uk_assay_entry_confirmation_preview (preview_ref),
    UNIQUE KEY uk_assay_entry_confirmation_idempotency (idempotency_digest)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_assay_entry_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_user_id INT NOT NULL,
    agent_session_id VARCHAR(64) NOT NULL,
    preview_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    event_code VARCHAR(32) NOT NULL,
    result_code INT NULL,
    assay_id INT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_assay_entry_audit_preview (preview_ref, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

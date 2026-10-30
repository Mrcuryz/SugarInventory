-- Control-plane preview storage only. Does not alter assay or grant business permissions.
CREATE TABLE IF NOT EXISTS agent_assay_entry_preview (
    id BIGINT NOT NULL AUTO_INCREMENT,
    preview_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_user_id INT NOT NULL,
    agent_session_id VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    normalized_input_json LONGTEXT NOT NULL,
    state_snapshot_json LONGTEXT NOT NULL,
    state_digest CHAR(64) CHARACTER SET ascii NOT NULL,
    public_payload_json LONGTEXT NOT NULL,
    content_digest CHAR(64) CHARACTER SET ascii NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_assay_entry_preview_ref (preview_ref),
    KEY idx_assay_entry_preview_session (agent_session_id, owner_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS auto_inbound_execution (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_id VARCHAR(64) NOT NULL,
    source_task_id VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    result_json LONGTEXT NULL,
    created_by INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auto_inbound_batch_task (batch_id, source_task_id),
    KEY idx_auto_inbound_created_by_time (created_by, created_at),
    CONSTRAINT fk_auto_inbound_execution_user
        FOREIGN KEY (created_by) REFERENCES user (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='智能报数入库执行幂等与恢复事实';

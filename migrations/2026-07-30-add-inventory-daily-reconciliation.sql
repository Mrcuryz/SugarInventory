CREATE TABLE IF NOT EXISTS inventory_reconciliation_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reconciliation_run_id VARCHAR(64) NOT NULL,
    business_date DATE NOT NULL,
    revision INT NOT NULL,
    opening_snapshot_run_id VARCHAR(64) NULL,
    closing_snapshot_run_id VARCHAR(64) NULL,
    rule_version VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    global_result_count INT NOT NULL DEFAULT 0,
    warehouse_result_count INT NOT NULL DEFAULT 0,
    failed_result_count INT NOT NULL DEFAULT 0,
    issue_count INT NOT NULL DEFAULT 0,
    max_abs_piece_difference INT NOT NULL DEFAULT 0,
    reason VARCHAR(255) NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_reconciliation_run_id (reconciliation_run_id),
    UNIQUE KEY uk_inventory_reconciliation_revision (business_date, revision),
    KEY idx_inventory_reconciliation_date_status (business_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存每日守恒对账批次';

CREATE TABLE IF NOT EXISTS inventory_reconciliation_result (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reconciliation_run_id VARCHAR(64) NOT NULL,
    business_date DATE NOT NULL,
    dimension_type VARCHAR(32) NOT NULL,
    product_id INT NOT NULL,
    product_name_snapshot VARCHAR(255) NOT NULL,
    warehouse_id INT NOT NULL DEFAULT 0,
    warehouse_name_snapshot VARCHAR(255) NULL,
    opening_pieces INT NOT NULL DEFAULT 0,
    inbound_pieces INT NOT NULL DEFAULT 0,
    outbound_pieces INT NOT NULL DEFAULT 0,
    transfer_in_pieces INT NOT NULL DEFAULT 0,
    transfer_out_pieces INT NOT NULL DEFAULT 0,
    expected_closing_pieces INT NOT NULL DEFAULT 0,
    actual_closing_pieces INT NOT NULL DEFAULT 0,
    piece_difference INT NOT NULL DEFAULT 0,
    opening_weight_kg DECIMAL(18, 4) NOT NULL DEFAULT 0,
    inbound_weight_kg DECIMAL(18, 4) NOT NULL DEFAULT 0,
    outbound_weight_kg DECIMAL(18, 4) NOT NULL DEFAULT 0,
    transfer_in_weight_kg DECIMAL(18, 4) NOT NULL DEFAULT 0,
    transfer_out_weight_kg DECIMAL(18, 4) NOT NULL DEFAULT 0,
    expected_closing_weight_kg DECIMAL(18, 4) NOT NULL DEFAULT 0,
    actual_closing_weight_kg DECIMAL(18, 4) NOT NULL DEFAULT 0,
    weight_difference_kg DECIMAL(18, 4) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_reconciliation_dimension (
        reconciliation_run_id,
        dimension_type,
        product_id,
        warehouse_id
    ),
    KEY idx_inventory_reconciliation_result_status (business_date, status),
    KEY idx_inventory_reconciliation_result_product (business_date, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存每日守恒对账维度结果';

CREATE TABLE IF NOT EXISTS inventory_data_quality_issue (
    id BIGINT NOT NULL AUTO_INCREMENT,
    issue_id VARCHAR(64) NOT NULL,
    issue_fingerprint VARCHAR(64) NOT NULL,
    reconciliation_run_id VARCHAR(64) NOT NULL,
    business_date DATE NOT NULL,
    issue_code VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    source_type VARCHAR(32) NULL,
    source_record_id BIGINT NULL,
    business_action_id VARCHAR(80) NULL,
    product_id INT NULL,
    warehouse_id INT NULL,
    message VARCHAR(500) NOT NULL,
    details_json TEXT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_quality_issue_id (issue_id),
    UNIQUE KEY uk_inventory_quality_issue_fingerprint (
        reconciliation_run_id,
        issue_fingerprint
    ),
    KEY idx_inventory_quality_issue_date (business_date, severity, status),
    KEY idx_inventory_quality_issue_action (business_action_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存趋势数据质量问题';

CREATE TABLE IF NOT EXISTS inventory_trend_release_gate (
    id BIGINT NOT NULL AUTO_INCREMENT,
    gate_key VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    required_passed_days INT NOT NULL,
    consecutive_passed_days INT NOT NULL DEFAULT 0,
    window_start_date DATE NULL,
    window_end_date DATE NULL,
    last_reconciliation_run_id VARCHAR(64) NULL,
    rule_version VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    evaluated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_trend_release_gate (gate_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存趋势报表发布门禁';

INSERT IGNORE INTO inventory_trend_release_gate (
    gate_key,
    status,
    required_passed_days,
    consecutive_passed_days,
    rule_version,
    reason,
    evaluated_at
) VALUES (
    'INVENTORY_LEVEL_TREND',
    'BLOCKED',
    7,
    0,
    'inventory-reconciliation-v1',
    '等待连续日终快照和每日守恒对账',
    CURRENT_TIMESTAMP(6)
);

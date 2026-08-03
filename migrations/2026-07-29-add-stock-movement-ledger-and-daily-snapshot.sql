CREATE TABLE IF NOT EXISTS stock_movement_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    business_action_id VARCHAR(80) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_record_id BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    product_id INT NOT NULL,
    product_status VARCHAR(32) NULL,
    production_date DATE NULL,
    from_warehouse_id INT NULL,
    to_warehouse_id INT NULL,
    pallet_code_id INT NULL,
    board_quantity INT NOT NULL DEFAULT 0,
    loose_piece_quantity INT NOT NULL DEFAULT 0,
    total_pieces INT NOT NULL DEFAULT 0,
    total_weight_kg DECIMAL(18, 4) NOT NULL DEFAULT 0,
    conversion_rule_version VARCHAR(32) NOT NULL,
    operator_id INT NULL,
    action_kind VARCHAR(48) NOT NULL,
    metadata_json TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_movement_event_id (event_id),
    UNIQUE KEY uk_stock_movement_source (source_type, source_record_id, event_type),
    KEY idx_stock_movement_occurred (occurred_at),
    KEY idx_stock_movement_product_time (product_id, occurred_at),
    KEY idx_stock_movement_business_action (business_action_id),
    KEY idx_stock_movement_from_warehouse (from_warehouse_id, occurred_at),
    KEY idx_stock_movement_to_warehouse (to_warehouse_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变库存业务事件账本';

CREATE TABLE IF NOT EXISTS inventory_snapshot_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    snapshot_run_id VARCHAR(64) NOT NULL,
    snapshot_date DATE NOT NULL,
    snapshot_type VARCHAR(32) NOT NULL,
    revision INT NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    rule_version VARCHAR(32) NOT NULL,
    row_count INT NOT NULL DEFAULT 0,
    content_sha256 VARCHAR(64) NULL,
    status VARCHAR(24) NOT NULL,
    reason VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_snapshot_run_id (snapshot_run_id),
    UNIQUE KEY uk_inventory_snapshot_revision (snapshot_date, snapshot_type, revision),
    KEY idx_inventory_snapshot_date_status (snapshot_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存快照批次';

CREATE TABLE IF NOT EXISTS inventory_daily_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    snapshot_run_id VARCHAR(64) NOT NULL,
    snapshot_date DATE NOT NULL,
    product_id INT NOT NULL,
    product_name_snapshot VARCHAR(255) NOT NULL,
    product_status_snapshot VARCHAR(32) NULL,
    warehouse_id INT NOT NULL,
    warehouse_name_snapshot VARCHAR(255) NOT NULL,
    production_date DATE NULL,
    board_count INT NOT NULL DEFAULT 0,
    loose_piece_count INT NOT NULL DEFAULT 0,
    total_pieces INT NOT NULL DEFAULT 0,
    total_weight_kg DECIMAL(18, 4) NOT NULL DEFAULT 0,
    inventory_record_count INT NOT NULL DEFAULT 0,
    pallet_count INT NOT NULL DEFAULT 0,
    pieces_per_pallet_snapshot INT NULL,
    weight_per_piece_snapshot DECIMAL(18, 6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_snapshot_dimension (
        snapshot_run_id,
        product_id,
        warehouse_id,
        production_date
    ),
    KEY idx_inventory_daily_product (snapshot_date, product_id),
    KEY idx_inventory_daily_warehouse (snapshot_date, warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按产品、库位和生产日期聚合的日终库存快照';

SET @baseline_run_id = CONCAT('baseline_', DATE_FORMAT(CURRENT_DATE, '%Y%m%d'));

INSERT IGNORE INTO inventory_snapshot_run (
    snapshot_run_id,
    snapshot_date,
    snapshot_type,
    revision,
    data_as_of,
    rule_version,
    row_count,
    content_sha256,
    status,
    reason,
    created_at,
    completed_at
) VALUES (
    @baseline_run_id,
    CURRENT_DATE,
    'INITIAL_BASELINE',
    1,
    CURRENT_TIMESTAMP(6),
    'inventory-snapshot-v1',
    0,
    NULL,
    'COMPLETED',
    '库存趋势底座上线基线；不代表上线前历史',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
);

INSERT IGNORE INTO inventory_daily_snapshot (
    snapshot_run_id,
    snapshot_date,
    product_id,
    product_name_snapshot,
    product_status_snapshot,
    warehouse_id,
    warehouse_name_snapshot,
    production_date,
    board_count,
    loose_piece_count,
    total_pieces,
    total_weight_kg,
    inventory_record_count,
    pallet_count,
    pieces_per_pallet_snapshot,
    weight_per_piece_snapshot,
    created_at
)
SELECT
    @baseline_run_id,
    CURRENT_DATE,
    p.id,
    p.product_name,
    COALESCE(i.product_status, p.status),
    w.id,
    w.warehouse_name,
    COALESCE(pc.production_date, i.entry_date),
    SUM(CASE WHEN COALESCE(i.pieces, 0) > 0 THEN 0 ELSE COALESCE(i.quantity, 1) END),
    SUM(CASE WHEN COALESCE(i.pieces, 0) > 0 THEN i.pieces ELSE 0 END),
    SUM(
        CASE
            WHEN COALESCE(i.pieces, 0) > 0 THEN i.pieces
            ELSE COALESCE(i.quantity, 1) * COALESCE(p.pieces_per_pallet, 0)
        END
    ),
    SUM(
        CASE
            WHEN COALESCE(i.pieces, 0) > 0 THEN i.pieces
            ELSE COALESCE(i.quantity, 1) * COALESCE(p.pieces_per_pallet, 0)
        END
    ) * COALESCE(p.weight_per_piece, 0),
    COUNT(*),
    COUNT(DISTINCT i.pallet_code_id),
    p.pieces_per_pallet,
    p.weight_per_piece,
    CURRENT_TIMESTAMP(6)
FROM inventory i
JOIN product p ON p.id = i.product_id
JOIN warehouse w ON w.id = i.warehouse_id
LEFT JOIN pallet_code pc ON pc.id = i.pallet_code_id
GROUP BY
    p.id,
    p.product_name,
    COALESCE(i.product_status, p.status),
    w.id,
    w.warehouse_name,
    COALESCE(pc.production_date, i.entry_date),
    p.pieces_per_pallet,
    p.weight_per_piece;

UPDATE inventory_snapshot_run
SET row_count = (
        SELECT COUNT(*)
        FROM inventory_daily_snapshot
        WHERE snapshot_run_id = @baseline_run_id
    )
WHERE snapshot_run_id = @baseline_run_id;

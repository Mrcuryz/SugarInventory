CREATE TABLE IF NOT EXISTS production_prepare_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    product_id INT NOT NULL COMMENT '半成品产品ID',
    product_name_snapshot VARCHAR(100) NULL COMMENT '产品名称快照',
    product_status VARCHAR(20) NULL COMMENT '产品状态',
    production_date DATE NULL COMMENT '半成品生产日期',
    screen_mesh_id INT NULL COMMENT '筛网ID',
    assay_id INT NULL COMMENT '化验ID',
    source_pallet_code_id INT NULL COMMENT '来源二维码ID',
    source_pallet_code VARCHAR(50) NULL COMMENT '来源二维码编码快照',
    source_inventory_id INT NULL COMMENT '来源库存ID快照',
    source_warehouse_id INT NULL COMMENT '来源库位ID快照',
    source_warehouse_name VARCHAR(100) NULL COMMENT '来源库位名称快照',
    source_side VARCHAR(10) NULL COMMENT '来源侧别快照',
    source_row_number INT NULL COMMENT '来源排号快照',
    source_layer INT NULL COMMENT '来源层号快照',
    board_count_snapshot INT DEFAULT 0 COMMENT '领用板数快照',
    piece_count_snapshot INT DEFAULT 0 COMMENT '领用散件数快照',
    total_pieces INT NOT NULL COMMENT '折算总件数',
    pieces_per_pallet INT NULL COMMENT '每板件数快照',
    source_task_id INT NULL COMMENT '来源任务ID',
    source_flow_id BIGINT NULL COMMENT '来源流转记录ID',
    created_by INT NULL COMMENT '操作人',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    remark VARCHAR(500) NULL COMMENT '备注'
) COMMENT='生产领用出库统计台账';

CREATE TABLE IF NOT EXISTS semi_prepare_pool_balance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    product_id INT NOT NULL COMMENT '半成品产品ID',
    product_name_snapshot VARCHAR(100) NULL COMMENT '产品名称快照',
    production_date DATE NULL COMMENT '生产日期',
    screen_mesh_id INT NULL COMMENT '筛网ID',
    assay_id INT NULL COMMENT '化验ID',
    in_pieces INT NOT NULL DEFAULT 0 COMMENT '累计入池件数',
    consumed_pieces INT NOT NULL DEFAULT 0 COMMENT '累计消耗件数',
    remaining_pieces INT NOT NULL DEFAULT 0 COMMENT '当前剩余件数',
    pieces_per_pallet INT NULL COMMENT '每板件数快照',
    weight_per_piece DECIMAL(10, 3) NULL COMMENT '单件重量快照',
    remaining_weight DECIMAL(12, 3) NULL COMMENT '剩余重量',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/CONSUMED',
    first_in_at DATETIME NOT NULL COMMENT '首次入池时间',
    last_in_at DATETIME NOT NULL COMMENT '最近入池时间',
    last_consumed_at DATETIME NULL COMMENT '最近消耗时间',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NULL COMMENT '更新时间',
    UNIQUE KEY uk_prepare_balance_batch (product_id, production_date, screen_mesh_id, assay_id),
    KEY idx_prepare_balance_active (remaining_pieces, status),
    KEY idx_prepare_balance_product_date (product_id, production_date)
) COMMENT='备料池半成品余额';

CREATE TABLE IF NOT EXISTS production_consumption_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    product_id INT NOT NULL COMMENT '半成品产品ID',
    product_name_snapshot VARCHAR(100) NULL COMMENT '产品名称快照',
    production_date DATE NULL COMMENT '生产日期',
    screen_mesh_id INT NULL COMMENT '筛网ID',
    assay_id INT NULL COMMENT '化验ID',
    consume_pieces INT NOT NULL COMMENT '扣减件数',
    balance_id BIGINT NULL COMMENT '备料池余额ID',
    source_batch_id VARCHAR(80) NULL COMMENT '智能报数批次ID',
    source_task_id VARCHAR(80) NULL COMMENT '智能报数任务ID',
    source_text TEXT NULL COMMENT '原始报数文本',
    created_by INT NULL COMMENT '操作人',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    remark VARCHAR(500) NULL COMMENT '备注',
    KEY idx_consumption_batch (source_batch_id),
    KEY idx_consumption_product_date (product_id, production_date)
) COMMENT='生产消耗扣减记录';

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE pallet_task_semi_item ADD COLUMN prepare_balance_id BIGINT NULL COMMENT ''备料池余额ID'' AFTER semi_pallet_code_id',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pallet_task_semi_item'
      AND COLUMN_NAME = 'prepare_balance_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE pallet_task_semi_item ADD COLUMN board_count INT NULL COMMENT ''登记板数'' AFTER unit',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pallet_task_semi_item'
      AND COLUMN_NAME = 'board_count'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE pallet_task_semi_item ADD COLUMN piece_count INT NULL COMMENT ''登记件数'' AFTER board_count',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pallet_task_semi_item'
      AND COLUMN_NAME = 'piece_count'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE pallet_task_semi_item ADD COLUMN total_pieces INT NULL COMMENT ''折算总件数'' AFTER piece_count',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pallet_task_semi_item'
      AND COLUMN_NAME = 'total_pieces'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS production_report_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    source_text TEXT NOT NULL COMMENT '原始报数全文',
    report_type VARCHAR(50) NULL COMMENT '报数类型',
    report_date DATE NULL COMMENT '报数日期',
    inbound_json JSON NULL COMMENT '识别到的入库项JSON',
    consumption_text TEXT NULL COMMENT '生产消耗文本摘要',
    consumption_json JSON NULL COMMENT '生产消耗结构化JSON',
    unmatched_names JSON NULL COMMENT '未匹配产品名称',
    inbound_task_ids JSON NULL COMMENT '本次报数生成的入库任务ID列表',
    pallet_codes JSON NULL COMMENT '本次报数绑定的二维码列表',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
    error_message VARCHAR(500) NULL COMMENT '失败原因',
    created_by INT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NULL COMMENT '更新时间',
    remark VARCHAR(500) NULL COMMENT '备注'
) COMMENT='生产报数留档记录';

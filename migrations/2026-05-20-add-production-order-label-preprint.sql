CREATE TABLE IF NOT EXISTS `production_order_label_batch` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `production_order_id` bigint NOT NULL COMMENT '生产订单ID',
    `order_no` varchar(50) NOT NULL COMMENT '订单号快照',
    `batch_no` varchar(80) NOT NULL COMMENT '订单标签批次号',
    `product_id` int NOT NULL COMMENT '产品ID',
    `product_name_snapshot` varchar(100) NULL COMMENT '产品名称快照',
    `reserved_count` int NOT NULL DEFAULT 0 COMMENT '预分配数量',
    `used_count` int NOT NULL DEFAULT 0 COMMENT '已核销使用数量',
    `recycled_count` int NOT NULL DEFAULT 0 COMMENT '已回收数量',
    `status` varchar(30) NOT NULL DEFAULT 'RESERVED' COMMENT 'RESERVED/PRINTED/CLOSED/CANCELED',
    `printed_at` datetime NULL COMMENT '打印时间',
    `closed_at` datetime NULL COMMENT '关闭失效时间',
    `created_by` int NULL COMMENT '创建人',
    `created_at` datetime NOT NULL COMMENT '创建时间',
    `updated_at` datetime NULL COMMENT '更新时间',
    `remark` varchar(500) NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_polb_batch_no` (`batch_no`),
    KEY `idx_polb_order` (`production_order_id`),
    KEY `idx_polb_product` (`product_id`),
    KEY `idx_polb_status` (`status`)
) COMMENT='生产订单预打印标签批次';

CREATE TABLE IF NOT EXISTS `production_order_label_code` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `production_order_id` bigint NOT NULL COMMENT '生产订单ID',
    `order_no` varchar(50) NOT NULL COMMENT '订单号快照',
    `batch_id` bigint NOT NULL COMMENT '预打印批次ID',
    `batch_no` varchar(80) NOT NULL COMMENT '批次号快照',
    `sequence_no` int NOT NULL COMMENT '批次内顺序号',
    `pallet_code_id` int NOT NULL COMMENT '固定产品二维码ID',
    `pallet_code` varchar(50) NOT NULL COMMENT '固定产品二维码编码快照',
    `product_id` int NOT NULL COMMENT '产品ID',
    `product_name_snapshot` varchar(100) NULL COMMENT '产品名称快照',
    `label_token` varchar(64) NOT NULL COMMENT '标签校验令牌',
    `qr_content` varchar(500) NOT NULL COMMENT '预打印标签二维码内容',
    `status` varchar(30) NOT NULL DEFAULT 'RESERVED' COMMENT 'RESERVED/USED/RECYCLED/CANCELED',
    `used_output_code_id` bigint NULL COMMENT '核销后对应真实产出码ID',
    `used_at` datetime NULL COMMENT '核销时间',
    `recycled_at` datetime NULL COMMENT '回收时间',
    `created_at` datetime NOT NULL COMMENT '创建时间',
    `updated_at` datetime NULL COMMENT '更新时间',
    `remark` varchar(500) NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_polc_token` (`label_token`),
    UNIQUE KEY `uk_polc_batch_seq` (`batch_id`, `sequence_no`),
    KEY `idx_polc_order_product_status` (`production_order_id`, `product_id`, `status`),
    KEY `idx_polc_pallet` (`pallet_code_id`),
    KEY `idx_polc_status` (`status`)
) COMMENT='生产订单预打印标签授权码';

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'pallet_code'
              AND COLUMN_NAME = 'status'
              AND COLUMN_TYPE LIKE '%ORDER_RESERVED%'
              AND COLUMN_COMMENT = '托盘状态'
        ),
        'SELECT 1',
        'ALTER TABLE `pallet_code` MODIFY COLUMN `status` enum(''FREE'',''PENDING'',''INSTOCK'',''INVALID'',''ORDER_RESERVED'') NOT NULL DEFAULT ''FREE'' COMMENT ''托盘状态'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'production_order_output_code'
              AND COLUMN_NAME = 'label_code_id'
        ),
        'SELECT 1',
        'ALTER TABLE `production_order_output_code` ADD COLUMN `label_code_id` bigint NULL COMMENT ''来源预打印标签授权ID'' AFTER `pallet_code_id`'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'production_order_output_code'
              AND INDEX_NAME = 'idx_pooc_label_code'
        ),
        'SELECT 1',
        'ALTER TABLE `production_order_output_code` ADD KEY `idx_pooc_label_code` (`label_code_id`)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'pallet_flow_record'
              AND COLUMN_NAME = 'operation_type'
              AND COLUMN_TYPE LIKE '%ORDER_LABEL_RESERVE%'
              AND COLUMN_TYPE LIKE '%ORDER_LABEL_USED%'
              AND COLUMN_TYPE LIKE '%ORDER_LABEL_RECYCLE%'
              AND COLUMN_COMMENT = '操作类型'
        ),
        'SELECT 1',
        'ALTER TABLE `pallet_flow_record` MODIFY COLUMN `operation_type` enum(''SEMI_BIND'',''ASSAY'',''SEMI_INSTOCK'',''FINISH_BIND'',''FINISH_INSTOCK'',''TRANSFER'',''CONSUMED'',''OUT'',''CANCELED'',''PREPARE_CONSUMED'',''ORDER_MATERIAL_PICK'',''ORDER_OUTPUT_BIND'',''ORDER_LABEL_RESERVE'',''ORDER_LABEL_USED'',''ORDER_LABEL_RECYCLE'') NOT NULL COMMENT ''操作类型'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO `permission` (`perm_code`, `perm_name`, `description`)
VALUES
    ('production:label:reserve', '生产订单预分配标签', '允许按订单预分配固定产品二维码'),
    ('production:label:print', '生产订单预打印标签', '允许打印或重打订单标签批次'),
    ('production:label:finish', '确认生产结束', '允许按实际产量核销预打印标签并回收未用码'),
    ('production:label:recycle', '生产订单标签回收', '允许取消未使用预打印标签并回收二维码')
ON DUPLICATE KEY UPDATE
    `perm_name` = VALUES(`perm_name`),
    `description` = VALUES(`description`);

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p
WHERE r.`role_code` = 'ADMIN'
  AND p.`perm_code` LIKE 'production:label:%';

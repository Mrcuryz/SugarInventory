CREATE TABLE IF NOT EXISTS `production_boiling_batch` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `batch_no` varchar(80) NOT NULL COMMENT '煮糖批次号',
    `boiling_date` date NOT NULL COMMENT '煮糖日期',
    `team_name` varchar(100) NULL COMMENT '班组/机组/人员',
    `sugar_type` varchar(100) NULL COMMENT '糖类或来源名称',
    `product_id` int NULL COMMENT '关联产品ID，可为空',
    `product_name_snapshot` varchar(100) NULL COMMENT '产品名称快照',
    `pot_count` decimal(12,2) NULL COMMENT '锅数',
    `bucket_count` decimal(12,2) NOT NULL DEFAULT 180.00 COMMENT '实际桶数',
    `kg_per_bucket` decimal(12,3) NOT NULL DEFAULT 11.000 COMMENT '每桶重量kg',
    `total_weight_kg` decimal(14,3) NOT NULL DEFAULT 1980.000 COMMENT '总重量kg',
    `status` varchar(30) NOT NULL DEFAULT 'AVAILABLE' COMMENT 'DRAFT/AVAILABLE/USED_UP/CANCELED',
    `source_text` text NULL COMMENT '原始报数文本',
    `remark` varchar(500) NULL COMMENT '备注',
    `created_by` int NULL COMMENT '创建人ID',
    `created_by_name` varchar(100) NULL COMMENT '创建人名称快照',
    `created_at` datetime NOT NULL COMMENT '创建时间',
    `updated_at` datetime NULL COMMENT '更新时间',
    `canceled_by` int NULL COMMENT '作废人ID',
    `canceled_at` datetime NULL COMMENT '作废时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pbb_batch_no` (`batch_no`),
    KEY `idx_pbb_date` (`boiling_date`),
    KEY `idx_pbb_status` (`status`),
    KEY `idx_pbb_product` (`product_id`)
) COMMENT='生产煮糖批次';

CREATE TABLE IF NOT EXISTS `production_boiling_batch_usage` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `batch_id` bigint NOT NULL COMMENT '煮糖批次ID',
    `batch_no` varchar(80) NOT NULL COMMENT '煮糖批次号快照',
    `production_order_id` bigint NOT NULL COMMENT '生产订单ID',
    `order_no` varchar(50) NOT NULL COMMENT '生产订单号快照',
    `usage_unit` varchar(20) NOT NULL DEFAULT 'BUCKET' COMMENT '引用单位：BUCKET/KG',
    `usage_quantity` decimal(14,3) NOT NULL COMMENT '用户输入引用数量',
    `bucket_quantity` decimal(14,3) NOT NULL COMMENT '折算桶数',
    `weight_kg` decimal(14,3) NOT NULL COMMENT '折算重量kg',
    `status` varchar(30) NOT NULL DEFAULT 'RESERVED' COMMENT 'RESERVED/CONSUMED/RELEASED/CANCELED',
    `created_by` int NULL COMMENT '创建人ID',
    `created_by_name` varchar(100) NULL COMMENT '创建人名称快照',
    `created_at` datetime NOT NULL COMMENT '创建时间',
    `updated_at` datetime NULL COMMENT '更新时间',
    `remark` varchar(500) NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_pbbu_batch` (`batch_id`),
    KEY `idx_pbbu_order` (`production_order_id`),
    KEY `idx_pbbu_status` (`status`)
) COMMENT='生产订单引用煮糖批次记录';

INSERT INTO `permission` (`perm_code`, `perm_name`, `description`)
VALUES
    ('production:boiling:view', '煮糖批次查看', '允许查看煮糖批次列表、详情和余量'),
    ('production:boiling:create', '煮糖批次创建', '允许创建煮糖批次'),
    ('production:boiling:update', '煮糖批次编辑', '允许编辑未作废煮糖批次'),
    ('production:boiling:cancel', '煮糖批次作废', '允许作废未被使用的煮糖批次')
ON DUPLICATE KEY UPDATE
    `perm_name` = VALUES(`perm_name`),
    `description` = VALUES(`description`);

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p
WHERE r.`role_code` = 'ADMIN'
  AND p.`perm_code` LIKE 'production:boiling:%';

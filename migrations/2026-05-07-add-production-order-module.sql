CREATE TABLE IF NOT EXISTS `production_order` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no` varchar(50) NOT NULL COMMENT '生产订单号',
    `order_type` varchar(20) NOT NULL COMMENT '订单类型：SEMI/FINISH',
    `status` varchar(30) NOT NULL COMMENT '订单状态',
    `production_date` date NOT NULL COMMENT '生产日期',
    `planned_material_json` json NULL COMMENT '计划半成品用量，仅参考',
    `planned_output_json` json NULL COMMENT '预计产出，仅参考',
    `team_name` varchar(100) NULL COMMENT '班组/工序/负责人备注',
    `remark` varchar(500) NULL COMMENT '备注',
    `created_by` int NULL COMMENT '创建人ID',
    `created_by_name` varchar(100) NULL COMMENT '创建人名称快照',
    `created_at` datetime NOT NULL COMMENT '创建时间',
    `updated_at` datetime NULL COMMENT '更新时间',
    `completed_at` datetime NULL COMMENT '完成时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_production_order_no` (`order_no`),
    KEY `idx_production_order_status` (`status`),
    KEY `idx_production_order_type` (`order_type`),
    KEY `idx_production_order_date` (`production_date`),
    KEY `idx_production_order_created_by` (`created_by`)
) COMMENT='生产订单主表';

CREATE TABLE IF NOT EXISTS `production_order_material` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `production_order_id` bigint NOT NULL COMMENT '生产订单ID',
    `order_no` varchar(50) NOT NULL COMMENT '生产订单号快照',
    `pallet_code_id` int NULL COMMENT '来源半成品二维码ID',
    `pallet_code` varchar(50) NULL COMMENT '来源半成品二维码编码快照',
    `pallet_cycle_no` int NULL COMMENT '二维码轮次快照',
    `inventory_id` int NULL COMMENT '来源库存ID快照',
    `product_id` int NOT NULL COMMENT '半成品产品ID',
    `product_name_snapshot` varchar(100) NULL COMMENT '半成品名称快照',
    `product_status` varchar(20) NULL COMMENT '产品状态，通常为半成品',
    `production_date` date NULL COMMENT '半成品生产日期',
    `warehouse_id` int NULL COMMENT '来源库位ID',
    `warehouse_name_snapshot` varchar(100) NULL COMMENT '来源库位名称快照',
    `side` varchar(10) NULL COMMENT '来源侧别',
    `row_number` int NULL COMMENT '来源排号',
    `layer` int NULL COMMENT '来源层号',
    `quantity` int NULL COMMENT '数量。整板时通常为1',
    `unit` varchar(10) NULL COMMENT '单位：0=板，1=件',
    `pieces` int NULL COMMENT '散件数',
    `pieces_per_pallet` int NULL COMMENT '每板件数快照',
    `total_pieces` int NULL COMMENT '折算总件数',
    `weight_per_piece` decimal(10,2) NULL COMMENT '每件重量快照',
    `total_weight` decimal(12,2) NULL COMMENT '总重量快照',
    `status` varchar(30) NOT NULL DEFAULT 'PICKED' COMMENT 'PICKED/CONSUMED/RETURNED/CANCELED',
    `picked_by` int NULL COMMENT '领用人',
    `picked_by_name` varchar(100) NULL COMMENT '领用人名称快照',
    `picked_at` datetime NOT NULL COMMENT '领用时间',
    `canceled_by` int NULL COMMENT '取消人',
    `canceled_at` datetime NULL COMMENT '取消时间',
    `cancel_reason` varchar(500) NULL COMMENT '取消原因',
    `remark` varchar(500) NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_pom_order` (`production_order_id`),
    KEY `idx_pom_product_date` (`product_id`, `production_date`),
    KEY `idx_pom_pallet_code` (`pallet_code_id`),
    KEY `idx_pom_status` (`status`)
) COMMENT='生产订单实际领用半成品表';

CREATE TABLE IF NOT EXISTS `production_order_output` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `production_order_id` bigint NOT NULL COMMENT '生产订单ID',
    `order_no` varchar(50) NOT NULL COMMENT '订单号快照',
    `product_id` int NOT NULL COMMENT '产出产品ID',
    `product_name_snapshot` varchar(100) NULL COMMENT '产出产品名称快照',
    `product_status` varchar(20) NULL COMMENT '产品状态：半成品/成品',
    `production_date` date NOT NULL COMMENT '产出生产日期',
    `board_count` int NOT NULL DEFAULT 0 COMMENT '产出整板数',
    `piece_count` int NOT NULL DEFAULT 0 COMMENT '产出散件数',
    `total_pieces` int NULL COMMENT '折算总件数',
    `pieces_per_pallet` int NULL COMMENT '每板件数快照',
    `weight_per_piece` decimal(10,2) NULL COMMENT '每件重量快照',
    `total_weight` decimal(12,2) NULL COMMENT '总重量快照',
    `required_qr_count` int NOT NULL DEFAULT 0 COMMENT '需要二维码数量',
    `bound_qr_count` int NOT NULL DEFAULT 0 COMMENT '已绑定二维码数量',
    `inbound_qr_count` int NOT NULL DEFAULT 0 COMMENT '已入库二维码数量',
    `status` varchar(30) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/BOUND/PART_INBOUND/INSTOCK/CANCELED',
    `created_by` int NULL COMMENT '创建人',
    `created_at` datetime NOT NULL COMMENT '创建时间',
    `updated_at` datetime NULL COMMENT '更新时间',
    `remark` varchar(500) NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_poo_order` (`production_order_id`),
    KEY `idx_poo_product` (`product_id`),
    KEY `idx_poo_status` (`status`)
) COMMENT='生产订单产出成品/半成品表';

CREATE TABLE IF NOT EXISTS `production_order_output_code` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `production_order_id` bigint NOT NULL COMMENT '生产订单ID',
    `order_no` varchar(50) NOT NULL COMMENT '订单号快照',
    `output_id` bigint NOT NULL COMMENT '产出行ID',
    `pallet_code_id` int NOT NULL COMMENT '成品/半成品二维码ID',
    `pallet_code` varchar(50) NOT NULL COMMENT '二维码编码快照',
    `pallet_cycle_no` int NULL COMMENT '二维码轮次快照',
    `product_id` int NOT NULL COMMENT '产品ID',
    `product_name_snapshot` varchar(100) NULL COMMENT '产品名称快照',
    `quantity` int NOT NULL COMMENT '本码对应数量',
    `unit` varchar(10) NOT NULL COMMENT '0=板，1=件',
    `pieces` int NULL COMMENT '散件数。整板为0或null',
    `pallet_task_id` int NULL COMMENT '创建的托盘任务ID',
    `inventory_id` int NULL COMMENT '入库后的库存ID，可后补',
    `status` varchar(30) NOT NULL DEFAULT 'BOUND' COMMENT 'BOUND/PENDING_INBOUND/INSTOCK/CANCELED',
    `printed_at` datetime NULL COMMENT '打印时间',
    `inbound_at` datetime NULL COMMENT '入库时间',
    `created_at` datetime NOT NULL COMMENT '创建时间',
    `remark` varchar(500) NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_pooc_order` (`production_order_id`),
    KEY `idx_pooc_output` (`output_id`),
    KEY `idx_pooc_pallet_code` (`pallet_code_id`),
    KEY `idx_pooc_task` (`pallet_task_id`),
    KEY `idx_pooc_status` (`status`)
) COMMENT='生产订单产出二维码关联表';

INSERT INTO `permission` (`perm_code`, `perm_name`, `description`)
VALUES
    ('production:order:view', '生产订单查看', '允许查看生产订单列表和详情'),
    ('production:order:create', '生产订单创建', '允许创建生产订单'),
    ('production:order:update', '生产订单更新', '允许更新生产订单状态'),
    ('production:order:cancel', '生产订单取消', '允许取消生产订单'),
    ('production:material:view', '半成品领用查看', '允许查看生产订单半成品领用'),
    ('production:material:pick', '半成品领用', '允许确认生产订单半成品领用'),
    ('production:output:view', '产出贴码查看', '允许查看生产订单产出贴码'),
    ('production:output:create', '添加产出', '允许添加生产订单产出'),
    ('production:output:bindQr', '产出绑定二维码', '允许批量绑定固定产品二维码'),
    ('production:output:print', '产出二维码打印', '允许打印生产订单产出二维码')
ON DUPLICATE KEY UPDATE
    `perm_name` = VALUES(`perm_name`),
    `description` = VALUES(`description`);

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p
WHERE r.`role_code` = 'ADMIN'
  AND p.`perm_code` LIKE 'production:%';

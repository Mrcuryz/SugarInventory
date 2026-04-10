-- Add semi prepare pool lifecycle support for pallet-based semi-product outflow

ALTER TABLE `pallet_task`
    ADD COLUMN `biz_scene` enum('DIRECT_OUT','PREPARE_CONSUMED') NULL DEFAULT NULL COMMENT '业务场景：普通出库/转入备料池' AFTER `task_type`;

ALTER TABLE `pallet_flow_record`
    MODIFY COLUMN `operation_type` enum(
        'SEMI_BIND',
        'ASSAY',
        'SEMI_INSTOCK',
        'FINISH_BIND',
        'FINISH_INSTOCK',
        'TRANSFER',
        'CONSUMED',
        'OUT',
        'CANCELED',
        'PREPARE_CONSUMED'
    ) NOT NULL COMMENT '操作类型';

CREATE TABLE `semi_prepare_pool` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '备料池记录ID',
    `product_id` int NOT NULL COMMENT '半成品产品ID',
    `production_date` date NOT NULL COMMENT '生产日期',
    `pallet_code_id` int NOT NULL COMMENT '托盘码ID',
    `cycle_no` int NOT NULL DEFAULT 0 COMMENT '所属托盘循环号',
    `status` enum('ACTIVE','CONSUMED','CANCELED') NOT NULL DEFAULT 'ACTIVE' COMMENT '备料池状态',
    `remark` varchar(255) NULL DEFAULT NULL COMMENT '备注/实际备料位置',
    `created_by` int NOT NULL COMMENT '创建人',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by` int NULL DEFAULT NULL COMMENT '更新人',
    `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_prepare_pallet_cycle` (`pallet_code_id`, `cycle_no`),
    KEY `idx_prepare_product_date_status` (`product_id`, `production_date`, `status`),
    KEY `idx_prepare_pallet_cycle` (`pallet_code_id`, `cycle_no`),
    CONSTRAINT `fk_prepare_pool_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_prepare_pool_pallet` FOREIGN KEY (`pallet_code_id`) REFERENCES `pallet_code` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_prepare_pool_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_prepare_pool_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) COMMENT='半成品备料池记录表';

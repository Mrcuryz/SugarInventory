CREATE TABLE IF NOT EXISTS `production_daily_report` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `report_date` date NOT NULL COMMENT '日报业务日期',
    `prepared_date` date NULL COMMENT '制表日期',
    `prepared_by_name` varchar(100) NULL COMMENT '制表人',
    `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SUBMITTED',
    `version` int NOT NULL DEFAULT 0 COMMENT '顶部信息乐观锁版本',
    `created_by` int NULL COMMENT '创建人ID',
    `created_by_name` varchar(100) NULL COMMENT '创建人名称快照',
    `created_at` datetime NOT NULL COMMENT '创建时间',
    `updated_by` int NULL COMMENT '最后修改人ID',
    `updated_by_name` varchar(100) NULL COMMENT '最后修改人名称快照',
    `updated_at` datetime NULL COMMENT '最后修改时间',
    `submitted_by` int NULL COMMENT '最近提交人ID',
    `submitted_by_name` varchar(100) NULL COMMENT '最近提交人名称快照',
    `submitted_at` datetime NULL COMMENT '最近提交时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pdr_report_date` (`report_date`),
    KEY `idx_pdr_status_date` (`status`, `report_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产日报主表';

CREATE TABLE IF NOT EXISTS `production_daily_report_section` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `report_id` bigint NOT NULL COMMENT '日报ID',
    `department_code` varchar(40) NOT NULL COMMENT '固定部门分区编码',
    `department_name_snapshot` varchar(100) NOT NULL COMMENT '部门分区名称快照',
    `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SUBMITTED',
    `version` int NOT NULL DEFAULT 0 COMMENT '分区乐观锁版本',
    `updated_by` int NULL COMMENT '最后修改人ID',
    `updated_by_name` varchar(100) NULL COMMENT '最后修改人名称快照',
    `updated_at` datetime NULL COMMENT '最后修改时间',
    `submitted_by` int NULL COMMENT '最近提交人ID',
    `submitted_by_name` varchar(100) NULL COMMENT '最近提交人名称快照',
    `submitted_at` datetime NULL COMMENT '最近提交时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pdrs_report_department` (`report_id`, `department_code`),
    KEY `idx_pdrs_report` (`report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产日报部门分区';

CREATE TABLE IF NOT EXISTS `production_daily_metric_value` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `report_section_id` bigint NOT NULL COMMENT '所属日报分区ID',
    `metric_code` varchar(80) NOT NULL COMMENT '固定指标编码',
    `metric_name_snapshot` varchar(150) NOT NULL COMMENT '指标名称快照',
    `group_code` varchar(60) NOT NULL COMMENT '指标分组编码',
    `group_name_snapshot` varchar(100) NOT NULL COMMENT '指标分组名称快照',
    `unit_snapshot` varchar(50) NULL COMMENT '固定单位快照',
    `daily_actual` decimal(18,6) NULL COMMENT '当日实绩',
    `converted_tons` decimal(18,6) NULL COMMENT '折成吨',
    `month_quantity` decimal(18,6) NULL COMMENT '月累计数量',
    `month_tons` decimal(18,6) NULL COMMENT '月累计吨或指标值',
    `year_tons` decimal(18,6) NULL COMMENT '年累计吨或指标值',
    `remark` varchar(500) NULL COMMENT '备注',
    `display_order` int NOT NULL COMMENT '固定显示顺序',
    `updated_by` int NULL COMMENT '最后修改人ID',
    `updated_at` datetime NULL COMMENT '最后修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pdmv_section_metric` (`report_section_id`, `metric_code`),
    KEY `idx_pdmv_section_order` (`report_section_id`, `display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产日报固定指标值';

CREATE TABLE IF NOT EXISTS `production_daily_product_line` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `report_section_id` bigint NOT NULL COMMENT '所属日报分区ID',
    `category_code` varchar(60) NOT NULL COMMENT '产品分区编码',
    `category_name_snapshot` varchar(100) NOT NULL COMMENT '产品分区名称快照',
    `product_id` int NOT NULL COMMENT '产品ID',
    `product_name_snapshot` varchar(150) NOT NULL COMMENT '产品名称快照',
    `product_status_snapshot` varchar(30) NULL COMMENT '成品或半成品快照',
    `product_type_snapshot` varchar(100) NULL COMMENT '产品类型快照',
    `unit` varchar(100) NULL COMMENT '当前日报产品单位',
    `daily_actual` decimal(18,6) NULL COMMENT '当日实绩',
    `converted_tons` decimal(18,6) NULL COMMENT '折成吨',
    `month_quantity` decimal(18,6) NULL COMMENT '月累计数量',
    `month_tons` decimal(18,6) NULL COMMENT '月累计吨',
    `year_tons` decimal(18,6) NULL COMMENT '年累计吨',
    `remark` varchar(500) NULL COMMENT '备注',
    `display_order` int NOT NULL COMMENT '用户显示顺序',
    `created_by` int NULL COMMENT '创建人ID',
    `created_at` datetime NOT NULL COMMENT '创建时间',
    `updated_by` int NULL COMMENT '最后修改人ID',
    `updated_at` datetime NULL COMMENT '最后修改时间',
    PRIMARY KEY (`id`),
    KEY `idx_pdpl_section_category_order` (`report_section_id`, `category_code`, `display_order`),
    KEY `idx_pdpl_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产日报动态产品行';

INSERT INTO `permission` (`perm_code`, `perm_name`, `description`)
VALUES
    ('production:daily-report:view', '生产日报查看', '允许查看生产日报详情和历史记录'),
    ('production:daily-report:edit', '生产日报填写', '允许新建、填写、修改和提交生产日报'),
    ('production:daily-report:export', '生产日报导出', '允许导出生产日报Excel')
ON DUPLICATE KEY UPDATE
    `perm_name` = VALUES(`perm_name`),
    `description` = VALUES(`description`);

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p ON p.`perm_code` IN (
    'production:daily-report:view',
    'production:daily-report:edit',
    'production:daily-report:export'
)
WHERE r.`role_code` = 'ADMIN';

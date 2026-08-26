CREATE TABLE IF NOT EXISTS `equipment_unit` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
    `unit_code` varchar(20) NOT NULL COMMENT '设备单位代码，例如YZ',
    `unit_name` varchar(100) NOT NULL COMMENT '设备单位名称',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序号',
    `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_equipment_unit_code` (`unit_code`),
    UNIQUE KEY `uk_equipment_unit_name` (`unit_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备单位';

CREATE TABLE IF NOT EXISTS `equipment_category` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
    `category_name` varchar(100) NOT NULL COMMENT '详细设备类别，例如电机',
    `major_code` varchar(10) NOT NULL COMMENT '设备编号大类代码，例如D',
    `default_sequence_start` int NULL COMMENT '默认号段起点',
    `default_sequence_end` int NULL COMMENT '默认号段终点',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序号',
    `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_equipment_category_name` (`category_name`),
    CONSTRAINT `chk_equipment_category_range` CHECK (
        (`default_sequence_start` IS NULL AND `default_sequence_end` IS NULL)
        OR (`default_sequence_start` BETWEEN 0 AND 9999 AND `default_sequence_end` BETWEEN 0 AND 9999
            AND `default_sequence_start` <= `default_sequence_end`)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备类别';

CREATE TABLE IF NOT EXISTS `equipment_manufacturer` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
    `manufacturer_code` varchar(30) NOT NULL COMMENT '厂家编号',
    `manufacturer_name` varchar(150) NOT NULL COMMENT '厂家名称',
    `address` varchar(255) NULL COMMENT '地址',
    `contact_person` varchar(50) NULL COMMENT '联系人',
    `phone` varchar(50) NULL COMMENT '电话',
    `fax` varchar(50) NULL COMMENT '传真',
    `remark` varchar(1000) NULL COMMENT '备注',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` int NULL COMMENT '创建人',
    `updated_by` int NULL COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_equipment_manufacturer_code` (`manufacturer_code`),
    KEY `idx_equipment_manufacturer_name` (`manufacturer_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备生产厂家';

CREATE TABLE IF NOT EXISTS `equipment_renovation_type` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
    `type_name` varchar(100) NOT NULL COMMENT '技改类别名称',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序号',
    `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_equipment_renovation_type_name` (`type_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备技改类别';

CREATE TABLE IF NOT EXISTS `equipment_repair_type` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
    `type_name` varchar(100) NOT NULL COMMENT '修理类型名称',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序号',
    `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_equipment_repair_type_name` (`type_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备修理类型';

CREATE TABLE IF NOT EXISTS `equipment_code_rule` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
    `company_code` varchar(20) NOT NULL DEFAULT 'LBYX' COMMENT '企业特征号',
    `unit_id` int NOT NULL COMMENT '设备单位ID',
    `category_id` int NOT NULL COMMENT '设备类别ID',
    `section_code` varchar(10) NOT NULL COMMENT '工段代码',
    `sequence_start` int NOT NULL COMMENT '顺序号起点',
    `sequence_end` int NOT NULL COMMENT '顺序号终点',
    `next_sequence` int NOT NULL COMMENT '下一候选顺序号',
    `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    `version` int NOT NULL DEFAULT 0 COMMENT '并发版本',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_equipment_code_rule` (`unit_id`, `category_id`, `section_code`),
    CONSTRAINT `fk_equipment_code_rule_unit` FOREIGN KEY (`unit_id`) REFERENCES `equipment_unit` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_equipment_code_rule_category` FOREIGN KEY (`category_id`) REFERENCES `equipment_category` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `chk_equipment_code_rule_range` CHECK (
        `sequence_start` BETWEEN 0 AND 9999 AND `sequence_end` BETWEEN 0 AND 9999
        AND `sequence_start` <= `sequence_end` AND `next_sequence` >= `sequence_start`
        AND `next_sequence` <= `sequence_end` + 1
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备编号规则';

CREATE TABLE IF NOT EXISTS `equipment_asset` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
    `equipment_code` varchar(50) NOT NULL COMMENT '完整设备编号',
    `equipment_sub_no` varchar(10) NOT NULL COMMENT '设备子编号，保留前导零',
    `unit_id` int NOT NULL COMMENT '设备单位ID',
    `category_id` int NOT NULL COMMENT '设备类别ID',
    `renovation_type_id` int NULL COMMENT '技改类别ID',
    `parent_equipment_id` int NULL COMMENT '主设备ID，附属设备使用',
    `manufacturer_id` int NULL COMMENT '生产厂家ID',
    `equipment_name` varchar(100) NOT NULL COMMENT '设备名称',
    `model` varchar(100) NULL COMMENT '设备型号',
    `rated_power_kw` decimal(12,3) NULL COMMENT '额定功率kW',
    `rated_voltage_v` decimal(12,3) NULL COMMENT '额定电压V',
    `rated_current_a` decimal(12,3) NULL COMMENT '额定电流A',
    `rated_speed_rpm` decimal(12,3) NULL COMMENT '额定转速r/min',
    `price` decimal(14,2) NULL COMMENT '价格元',
    `installation_cost` decimal(14,2) NULL COMMENT '安装费元',
    `installation_location` varchar(255) NULL COMMENT '安装位置',
    `factory_serial_no` varchar(100) NULL COMMENT '出厂编号',
    `production_date` date NULL COMMENT '生产日期',
    `arrival_date` date NULL COMMENT '进厂日期',
    `commissioning_date` date NULL COMMENT '启用日期',
    `remark` text NULL COMMENT '说明/备注',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` int NULL COMMENT '创建人',
    `updated_by` int NULL COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_equipment_asset_code` (`equipment_code`),
    KEY `idx_equipment_asset_unit_category` (`unit_id`, `category_id`),
    KEY `idx_equipment_asset_manufacturer` (`manufacturer_id`),
    KEY `idx_equipment_asset_parent` (`parent_equipment_id`),
    KEY `idx_equipment_asset_name` (`equipment_name`),
    CONSTRAINT `fk_equipment_asset_unit` FOREIGN KEY (`unit_id`) REFERENCES `equipment_unit` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_equipment_asset_category` FOREIGN KEY (`category_id`) REFERENCES `equipment_category` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_equipment_asset_renovation_type` FOREIGN KEY (`renovation_type_id`) REFERENCES `equipment_renovation_type` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_equipment_asset_parent` FOREIGN KEY (`parent_equipment_id`) REFERENCES `equipment_asset` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_equipment_asset_manufacturer` FOREIGN KEY (`manufacturer_id`) REFERENCES `equipment_manufacturer` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `chk_equipment_asset_non_negative` CHECK (
        (`rated_power_kw` IS NULL OR `rated_power_kw` >= 0)
        AND (`rated_voltage_v` IS NULL OR `rated_voltage_v` >= 0)
        AND (`rated_current_a` IS NULL OR `rated_current_a` >= 0)
        AND (`rated_speed_rpm` IS NULL OR `rated_speed_rpm` >= 0)
        AND (`price` IS NULL OR `price` >= 0)
        AND (`installation_cost` IS NULL OR `installation_cost` >= 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备台账';

CREATE TABLE IF NOT EXISTS `equipment_repair_record` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
    `equipment_id` int NOT NULL COMMENT '设备ID',
    `repair_date` date NOT NULL COMMENT '修理日期',
    `repair_type_id` int NOT NULL COMMENT '修理类型ID',
    `repair_person` varchar(100) NULL COMMENT '修理人或班组文本',
    `acceptance_person` varchar(100) NULL COMMENT '验收人文本',
    `repair_content` text NOT NULL COMMENT '修理内容',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` int NULL COMMENT '创建人',
    `updated_by` int NULL COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_equipment_repair_equipment_date` (`equipment_id`, `repair_date`),
    KEY `idx_equipment_repair_type` (`repair_type_id`),
    CONSTRAINT `fk_equipment_repair_equipment` FOREIGN KEY (`equipment_id`) REFERENCES `equipment_asset` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_equipment_repair_type` FOREIGN KEY (`repair_type_id`) REFERENCES `equipment_repair_type` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备修理记录';

INSERT INTO `permission` (`perm_code`, `perm_name`, `description`)
VALUES
    ('equipment:asset:view', '设备台账查看', '允许查看设备台账和详情'),
    ('equipment:asset:create', '设备台账新增', '允许新增设备并生成设备编号'),
    ('equipment:asset:update', '设备台账编辑', '允许编辑设备非编号字段'),
    ('equipment:asset:delete', '设备台账删除', '允许删除未被引用的设备'),
    ('equipment:asset:export', '设备台账导出', '允许导出设备台账'),
    ('equipment:repair:view', '设备修理记录查看', '允许查看设备修理记录'),
    ('equipment:repair:create', '设备修理记录新增', '允许新增设备修理记录'),
    ('equipment:repair:update', '设备修理记录编辑', '允许编辑设备修理记录'),
    ('equipment:repair:delete', '设备修理记录删除', '允许删除设备修理记录'),
    ('equipment:repair:export', '设备修理记录导出', '允许导出设备修理记录'),
    ('equipment:config:view', '设备基础资料查看', '允许查看设备基础资料'),
    ('equipment:config:manage', '设备基础资料维护', '允许维护设备单位、类别、厂家和类型'),
    ('equipment:code-rule:manage', '设备编号规则维护', '允许维护设备编号规则')
ON DUPLICATE KEY UPDATE `perm_name` = VALUES(`perm_name`), `description` = VALUES(`description`);

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r JOIN `permission` p
WHERE r.`role_code` = 'ADMIN' AND p.`perm_code` LIKE 'equipment:%';

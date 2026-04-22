-- Step 1 for assay standard refactor:
-- 1. Upgrade quality_standards to formal master table semantics.
-- 2. Add quality_standard_item detail table.
-- 3. Add product_quality_standard_relation relation table.
-- 4. Extend assay with applied-standard / judge / snapshot fields.
-- 5. Backfill detail items and initialize product-standard relations.

ALTER TABLE `quality_standards`
    DROP INDEX `product_type`,
    ADD COLUMN `standard_code` varchar(64) NULL DEFAULT NULL COMMENT '标准编码' AFTER `id`,
    ADD COLUMN `standard_level` varchar(64) NULL DEFAULT NULL COMMENT '标准等级' AFTER `product_type`,
    ADD COLUMN `version` int NOT NULL DEFAULT 1 COMMENT '标准版本' AFTER `standard_level`,
    ADD COLUMN `status` varchar(20) NOT NULL DEFAULT 'ENABLED' COMMENT '标准状态：ENABLED/DISABLED' AFTER `version`,
    ADD COLUMN `remark` varchar(255) NULL DEFAULT NULL COMMENT '备注' AFTER `ph_max`,
    ADD COLUMN `created_by` int NULL DEFAULT NULL COMMENT '创建人' AFTER `updated_at`,
    ADD COLUMN `updated_by` int NULL DEFAULT NULL COMMENT '更新人' AFTER `created_by`,
    ADD UNIQUE INDEX `uk_quality_standard_code` (`standard_code`),
    ADD UNIQUE INDEX `uk_quality_standard_name_version` (`product_type`, `standard_name`, `version`);

CREATE TABLE `quality_standard_item` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
    `quality_standard_id` int NOT NULL COMMENT '化验标准ID',
    `metric_code` varchar(64) NOT NULL COMMENT '指标编码',
    `metric_name` varchar(64) NOT NULL COMMENT '指标名称',
    `min_value` decimal(10, 2) NULL DEFAULT NULL COMMENT '下限值',
    `max_value` decimal(10, 2) NULL DEFAULT NULL COMMENT '上限值',
    `unit` varchar(32) NULL DEFAULT NULL COMMENT '单位',
    `compare_type` varchar(16) NOT NULL DEFAULT 'range' COMMENT '比较方式：range/lte/gte',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
    `remark` varchar(255) NULL DEFAULT NULL COMMENT '备注',
    `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_quality_standard_metric` (`quality_standard_id`, `metric_code`),
    INDEX `idx_quality_standard_item_standard` (`quality_standard_id`),
    CONSTRAINT `fk_quality_standard_item_standard`
        FOREIGN KEY (`quality_standard_id`) REFERENCES `quality_standards` (`id`)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '化验标准指标明细';

INSERT INTO `quality_standard_item`
(`quality_standard_id`, `metric_code`, `metric_name`, `min_value`, `max_value`, `unit`, `compare_type`, `sort_order`)
SELECT `id`, 'color_value', '色值', `color_min`, `color_max`, 'IU',
       CASE
           WHEN `color_min` IS NOT NULL AND `color_max` IS NULL THEN 'gte'
           WHEN `color_min` IS NULL AND `color_max` IS NOT NULL THEN 'lte'
           ELSE 'range'
       END,
       10
FROM `quality_standards`
WHERE `color_min` IS NOT NULL OR `color_max` IS NOT NULL
UNION ALL
SELECT `id`, 'reducing_sugar', '还原糖分', `reducing_sugar_min`, `reducing_sugar_max`, 'g/100g',
       CASE
           WHEN `reducing_sugar_min` IS NOT NULL AND `reducing_sugar_max` IS NULL THEN 'gte'
           WHEN `reducing_sugar_min` IS NULL AND `reducing_sugar_max` IS NOT NULL THEN 'lte'
           ELSE 'range'
       END,
       20
FROM `quality_standards`
WHERE `reducing_sugar_min` IS NOT NULL OR `reducing_sugar_max` IS NOT NULL
UNION ALL
SELECT `id`, 'dry_weight_loss', '干燥失重', `dry_weight_min`, `dry_weight_max`, 'g/100g',
       CASE
           WHEN `dry_weight_min` IS NOT NULL AND `dry_weight_max` IS NULL THEN 'gte'
           WHEN `dry_weight_min` IS NULL AND `dry_weight_max` IS NOT NULL THEN 'lte'
           ELSE 'range'
       END,
       30
FROM `quality_standards`
WHERE `dry_weight_min` IS NOT NULL OR `dry_weight_max` IS NOT NULL
UNION ALL
SELECT `id`, 'conductivity_ash', '电导灰分', `conductivity_ash_min`, `conductivity_ash_max`, 'g/100g',
       CASE
           WHEN `conductivity_ash_min` IS NOT NULL AND `conductivity_ash_max` IS NULL THEN 'gte'
           WHEN `conductivity_ash_min` IS NULL AND `conductivity_ash_max` IS NOT NULL THEN 'lte'
           ELSE 'range'
       END,
       40
FROM `quality_standards`
WHERE `conductivity_ash_min` IS NOT NULL OR `conductivity_ash_max` IS NOT NULL
UNION ALL
SELECT `id`, 'sucrose', '蔗糖分', `sucrose_min`, `sucrose_max`, 'g/100g',
       CASE
           WHEN `sucrose_min` IS NOT NULL AND `sucrose_max` IS NULL THEN 'gte'
           WHEN `sucrose_min` IS NULL AND `sucrose_max` IS NOT NULL THEN 'lte'
           ELSE 'range'
       END,
       50
FROM `quality_standards`
WHERE `sucrose_min` IS NOT NULL OR `sucrose_max` IS NOT NULL
UNION ALL
SELECT `id`, 'insoluble_impurity', '不溶于水杂质', `insoluble_impurity_min`, `insoluble_impurity_max`, 'mg/kg',
       CASE
           WHEN `insoluble_impurity_min` IS NOT NULL AND `insoluble_impurity_max` IS NULL THEN 'gte'
           WHEN `insoluble_impurity_min` IS NULL AND `insoluble_impurity_max` IS NOT NULL THEN 'lte'
           ELSE 'range'
       END,
       60
FROM `quality_standards`
WHERE `insoluble_impurity_min` IS NOT NULL OR `insoluble_impurity_max` IS NOT NULL
UNION ALL
SELECT `id`, 'ph', 'pH', `ph_min`, `ph_max`, NULL,
       CASE
           WHEN `ph_min` IS NOT NULL AND `ph_max` IS NULL THEN 'gte'
           WHEN `ph_min` IS NULL AND `ph_max` IS NOT NULL THEN 'lte'
           ELSE 'range'
       END,
       70
FROM `quality_standards`
WHERE `ph_min` IS NOT NULL OR `ph_max` IS NOT NULL;

ALTER TABLE `quality_standards`
    DROP COLUMN `color_min`,
    DROP COLUMN `color_max`,
    DROP COLUMN `reducing_sugar_min`,
    DROP COLUMN `reducing_sugar_max`,
    DROP COLUMN `dry_weight_min`,
    DROP COLUMN `dry_weight_max`,
    DROP COLUMN `conductivity_ash_min`,
    DROP COLUMN `conductivity_ash_max`,
    DROP COLUMN `sucrose_min`,
    DROP COLUMN `sucrose_max`,
    DROP COLUMN `insoluble_impurity_min`,
    DROP COLUMN `insoluble_impurity_max`,
    DROP COLUMN `ph_min`,
    DROP COLUMN `ph_max`;

CREATE TABLE `product_quality_standard_relation` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
    `product_id` int NOT NULL COMMENT '产品ID',
    `quality_standard_id` int NOT NULL COMMENT '化验标准ID',
    `is_default` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否默认标准',
    `priority` int NOT NULL DEFAULT 100 COMMENT '优先级，数字越小优先级越高',
    `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    `effective_from` datetime NULL DEFAULT NULL COMMENT '生效时间',
    `effective_to` datetime NULL DEFAULT NULL COMMENT '失效时间',
    `remark` varchar(255) NULL DEFAULT NULL COMMENT '备注',
    `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` int NULL DEFAULT NULL COMMENT '创建人',
    `updated_by` int NULL DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_product_quality_standard` (`product_id`, `quality_standard_id`),
    INDEX `idx_product_quality_standard_product` (`product_id`),
    INDEX `idx_product_quality_standard_standard` (`quality_standard_id`),
    CONSTRAINT `fk_product_quality_standard_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_product_quality_standard_standard`
        FOREIGN KEY (`quality_standard_id`) REFERENCES `quality_standards` (`id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '产品与化验标准关联';

INSERT INTO `product_quality_standard_relation`
(`product_id`, `quality_standard_id`, `is_default`, `priority`, `enabled`, `effective_from`, `effective_to`, `remark`)
SELECT t.`product_id`,
       t.`quality_standard_id`,
       CASE WHEN t.`row_num` = 1 THEN 1 ELSE 0 END AS `is_default`,
       t.`row_num` AS `priority`,
       1 AS `enabled`,
       NULL AS `effective_from`,
       NULL AS `effective_to`,
       '由 product_type 自动初始化' AS `remark`
FROM (
    SELECT p.`id` AS `product_id`,
           qs.`id` AS `quality_standard_id`,
           ROW_NUMBER() OVER (PARTITION BY p.`id` ORDER BY qs.`id`) AS `row_num`
    FROM `product` p
    INNER JOIN `quality_standards` qs ON qs.`product_type` = p.`product_type`
) t;

ALTER TABLE `assay`
    MODIFY COLUMN `is_qualified` enum('合格','不合格','无标准') NOT NULL COMMENT '兼容旧前端的判定结论',
    ADD COLUMN `applied_standard_id` int NULL DEFAULT NULL COMMENT '本次判定采用的标准ID' AFTER `qualified_standards`,
    ADD COLUMN `applied_standard_name` varchar(128) NULL DEFAULT NULL COMMENT '本次判定采用的标准名称' AFTER `applied_standard_id`,
    ADD COLUMN `applied_standard_version` int NULL DEFAULT NULL COMMENT '本次判定采用的标准版本' AFTER `applied_standard_name`,
    ADD COLUMN `judge_result` varchar(32) NULL DEFAULT NULL COMMENT '判定结果：PASS/FAIL/NO_STANDARD/MULTIPLE_CANDIDATES' AFTER `applied_standard_version`,
    ADD COLUMN `failed_metric_count` int NOT NULL DEFAULT 0 COMMENT '不达标指标数量' AFTER `judge_result`,
    ADD COLUMN `failed_metrics_json` json NULL COMMENT '不达标指标明细快照' AFTER `failed_metric_count`,
    ADD COLUMN `standard_snapshot_json` json NULL COMMENT '采用标准快照' AFTER `failed_metrics_json`,
    ADD COLUMN `judge_message` varchar(255) NULL DEFAULT NULL COMMENT '判定说明' AFTER `standard_snapshot_json`,
    ADD INDEX `idx_assay_applied_standard` (`applied_standard_id`),
    ADD INDEX `idx_assay_judge_result` (`judge_result`),
    ADD CONSTRAINT `fk_assay_applied_standard`
        FOREIGN KEY (`applied_standard_id`) REFERENCES `quality_standards` (`id`)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

UPDATE `assay`
SET `judge_result` = CASE
        WHEN `is_qualified` = '合格' THEN 'PASS'
        WHEN `is_qualified` = '不合格' THEN 'FAIL'
        ELSE 'NO_STANDARD'
    END,
    `failed_metric_count` = 0,
    `judge_message` = CASE
        WHEN `is_qualified` = '合格' THEN '历史记录迁移：原记录判定为合格'
        WHEN `is_qualified` = '不合格' THEN '历史记录迁移：原记录判定为不合格'
        ELSE '历史记录迁移：原记录未固化标准'
    END
WHERE `judge_result` IS NULL;

-- Allow inbound and inventory records to be created before assay data is available.
-- Assay links are resolved and backfilled later by the unified assay resolver.

ALTER TABLE `in_stock`
    MODIFY COLUMN `assay_id` int NULL DEFAULT NULL COMMENT '关联化验数据ID（可后补）';

ALTER TABLE `inventory`
    MODIFY COLUMN `assay_id` int NULL DEFAULT NULL COMMENT '关联的化验数据ID（可后补）';

ALTER TABLE `semi_product_record`
    MODIFY COLUMN `assay_id` int NULL DEFAULT NULL COMMENT '化验记录id（可后补）';

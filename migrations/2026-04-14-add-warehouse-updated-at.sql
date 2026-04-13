-- Add warehouse update timestamp for ledger sorting

ALTER TABLE `warehouse`
    ADD COLUMN `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近修改时间' AFTER `created_at`;

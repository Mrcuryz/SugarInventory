-- 基线结构来源：
-- D:\Users\Mrcury\Desktop\dump-laibin-202604281621.sql
--
-- 目的：
-- 适配“登记半成品用量 -> 从备料池汇总选择批次”的新逻辑。
-- 新逻辑不再强制绑定半成品托盘码，因此 semi_pallet_code_id 必须允许为空；
-- 同时 PalletTaskSemiItem / PalletTaskSemiItemMapper 已依赖以下新增字段。

ALTER TABLE `pallet_task_semi_item`
    MODIFY COLUMN `semi_pallet_code_id` INT NULL COMMENT '半成品托盘码ID',
    ADD COLUMN `prepare_balance_id` BIGINT NULL COMMENT '备料池余额ID' AFTER `semi_pallet_code_id`,
    ADD COLUMN `board_count` INT NULL COMMENT '登记板数' AFTER `unit`,
    ADD COLUMN `piece_count` INT NULL COMMENT '登记件数' AFTER `board_count`,
    ADD COLUMN `total_pieces` INT NULL COMMENT '折算总件数' AFTER `piece_count`;
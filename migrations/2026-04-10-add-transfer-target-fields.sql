-- Add transfer task target warehouse fields

ALTER TABLE `pallet_task`
    ADD COLUMN `target_warehouse_id` int NULL DEFAULT NULL COMMENT '调拨目标仓库ID' AFTER `biz_scene`,
    ADD COLUMN `target_side` enum('左','右') NULL DEFAULT NULL COMMENT '调拨目标侧向' AFTER `target_warehouse_id`,
    ADD INDEX `idx_task_target_warehouse` (`target_warehouse_id`),
    ADD CONSTRAINT `fk_task_target_warehouse` FOREIGN KEY (`target_warehouse_id`) REFERENCES `warehouse` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

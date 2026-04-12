-- Add inventory location uniqueness and structured pallet flow task relation

ALTER TABLE `inventory`
    ADD UNIQUE INDEX `uk_inventory_location` (`warehouse_id`, `side`, `row_number`, `layer`);

ALTER TABLE `pallet_flow_record`
    ADD COLUMN `task_id` int NULL COMMENT '关联任务ID' AFTER `pallet_code_id`,
    ADD INDEX `idx_flow_task_id` (`task_id`);

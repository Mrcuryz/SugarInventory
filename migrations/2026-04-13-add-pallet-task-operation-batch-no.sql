-- Add operation batch number for warehouse-map initiated task groups

ALTER TABLE `pallet_task`
    ADD COLUMN `operation_batch_no` varchar(64) NULL DEFAULT NULL COMMENT '操作批次号/单据号' AFTER `target_side`,
    ADD INDEX `idx_task_operation_batch_no` (`operation_batch_no`);

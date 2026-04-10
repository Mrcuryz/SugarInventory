-- Add cycle_no support and status cleanup for pallet reuse lifecycle

ALTER TABLE `pallet_code`
    ADD COLUMN `current_cycle_no` int NOT NULL DEFAULT 0 COMMENT '当前/最近一次循环号' AFTER `updated_by`;

ALTER TABLE `pallet_task`
    ADD COLUMN `cycle_no` int NOT NULL DEFAULT 0 COMMENT '所属循环号' AFTER `remark`;

ALTER TABLE `pallet_flow_record`
    ADD COLUMN `cycle_no` int NOT NULL DEFAULT 0 COMMENT '所属循环号' AFTER `remark`;


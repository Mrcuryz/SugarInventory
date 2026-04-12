-- Add indexes for pallet flow cycle query and retention cleanup

ALTER TABLE `pallet_flow_record`
    ADD INDEX `idx_flow_pallet_cycle_time` (`pallet_code_id`, `cycle_no`, `operation_time`),
    ADD INDEX `idx_flow_operation_time` (`operation_time`);

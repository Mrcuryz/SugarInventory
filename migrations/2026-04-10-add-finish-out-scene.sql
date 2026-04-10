-- Add finish pallet QR outflow scene support

ALTER TABLE `pallet_task`
    MODIFY COLUMN `biz_scene` enum('DIRECT_OUT','PREPARE_CONSUMED','FINISH_OUT') NULL DEFAULT NULL COMMENT '业务场景：普通出库/转入备料池/成品出库';

ALTER TABLE `pallet_flow_record`
    MODIFY COLUMN `operation_type` enum(
        'SEMI_BIND',
        'ASSAY',
        'SEMI_INSTOCK',
        'FINISH_BIND',
        'FINISH_INSTOCK',
        'TRANSFER',
        'CONSUMED',
        'OUT',
        'CANCELED',
        'PREPARE_CONSUMED',
        'ORDER_MATERIAL_PICK',
        'ORDER_OUTPUT_BIND'
    ) NOT NULL COMMENT '操作类型';

package com.Laibin.SugarInventory.domain.enumObject;

import lombok.Getter;

@Getter
public enum ErrorCode {
    PRODUCT_NOT_FOUND(1001, "产品不存在"),
    INVALID_PRODUCT_TYPE(1002, "无效的产品类型"),
    INVALID_PRODUCT_STATUS(1003, "无效的产品状态"),
    INVALID_PACKAGING_METHOD(1004, "无效的包装方式"),
    USER_NOT_FOUND(1005, "用户不存在"),
    EMPLOYEE_NOT_FOUND(1006, "员工不存在"),
    NAME_VALIDATION_FAILED(1007, "姓名验证失败"),
    PRODUCT_NAME_EXISTS(1008, "产品名称已存在"),
    RECORD_CREATE_FAILED(1009, "记录创建失败"),
    RECORD_NOT_FOUND(1010, "找不到半成品记录"),
    OPERATION_FORBIDDEN(1011, "没有权限进行此操作"),
    MODIFY_LIMIT_EXCEEDED(1012, "修改次数超限"),
    ZERO_WEIGHT_RECORD(1013, "数量或单位重量不能为空"),
    STOCK_IN_FAILED(1014, "入库失败"),
    UPDATE_INVENTORY_FAILED(1015, "更新库存失败"),
    ASSAY_RECORD_NOT_FOUND(1016, "找不到化验记录"),
    INVENTORY_NOT_FOUND(1017, "找不到库存记录"),
    INSERT_INVENTORY_LOCATION_FAILED(1018, "插入库存位置失败"),
    DATA_IMPORT_FAILED(1019, "数据导入失败"),
    ASSAY_NOT_FOUND(1020, "找不到化验数据"),
    DUPLICATE_ASSAY_RECORD(1021, "重复的化验记录"),
    INSUFFICIENT_STOCK(1022, "库存不足"),
    WAREHOUSE_NOT_FOUND(1023, "找不到库位"),
    WAREHOUSE_FULL(1024, "库位已满"),
    MULTIPLE_USE_ASSAY_FLAGS(1025, "存在多次使用化验标记"),
    INVALID_SEMI_RECORD(1026, "无效的半成品记录"),
    ASSAY_GROUP_RECORD_NOT_FOUND(1027, "找不到批量化验组"),
    SCREEN_MESH_NOT_FOUND(1028, "找不到关联筛网"),
    PRODUCT_SPECIFICATION_LOCKED_BY_INVENTORY(1029, "产品已有库存，不能修改每板件数或单件重量；不同规格请新建产品"),
    INVALID_PALLET_CODE(1030, "托盘码格式或校验位无效"),
    PALLET_CODE_NOT_FOUND(1031, "托盘码不存在"),
    PRODUCTION_DAILY_REPORT_CONFLICT(1032, "生产日报已被其他用户更新，请刷新后重试"),
    PRODUCTION_DAILY_REPORT_SECTION_INVALID(1033, "生产日报车间分区无效"),
    PRODUCTION_DAILY_REPORT_METRIC_INVALID(1034, "生产日报固定指标无效"),
    PRODUCTION_DAILY_REPORT_PRODUCT_INVALID(1035, "生产日报产品行无效"),
    PRODUCTION_DAILY_REPORT_IMPORT_INVALID(1036, "生产日报Excel格式无效"),
    EQUIPMENT_NOT_FOUND(1041, "设备不存在"),
    EQUIPMENT_REFERENCE_INVALID(1042, "设备关联数据无效"),
    EQUIPMENT_CODE_RULE_NOT_FOUND(1043, "找不到可用的设备编号规则"),
    EQUIPMENT_CODE_RANGE_EXHAUSTED(1044, "设备编号区间已用完"),
    EQUIPMENT_DATA_IN_USE(1045, "设备数据已被引用，不能删除"),
    EQUIPMENT_CONCURRENT_MODIFICATION(1046, "设备数据已被其他用户修改，请刷新后重试"),
    EQUIPMENT_BASIC_DATA_NOT_FOUND(1047, "设备基础资料不存在"),
    EQUIPMENT_DUPLICATE_DATA(1048, "设备基础资料已存在"),
    EQUIPMENT_REPAIR_NOT_FOUND(1049, "设备修理记录不存在"),
    EQUIPMENT_CODE_RULE_AMBIGUOUS(1050, "同一设备单位和类别存在多条启用的编号规则");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}

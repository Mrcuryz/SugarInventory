package com.Laibin.SugarInventory.domain.enumObject;

import lombok.Getter;

@Getter
public enum ErrorCode {
    PRODUCTION_DAILY_REPORT_QUERY_INVALID(1040, "日报查询条件无效，请选择不超过31天的日期范围、有效车间及分页条件"),
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
    EQUIPMENT_CODE_RULE_AMBIGUOUS(1050, "同一设备单位和类别存在多条启用的编号规则"),
    ASSAY_ENTRY_INPUT_INVALID(1051, "化验录入信息无效，请核对产品、采样日期和实测值"),
    ASSAY_ENTRY_VERSION_CONFLICT(1052, "该产品日期的化验版本状态异常，请核对已有记录后重新预览"),
    ASSAY_ENTRY_JUDGEMENT_UNAVAILABLE(1053, "暂时无法获得化验判定，请稍后重新预览"),
    ASSAY_ENTRY_PREVIEW_INVALID(1054, "化验预览不存在或已经失效，请重新预览"),
    ASSAY_ENTRY_STATE_CHANGED(1055, "产品、化验版本或标准已变化，请重新预览"),
    ASSAY_ENTRY_PERMISSION_DENIED(1056, "当前账号没有化验查看和录入权限"),
    ASSAY_ENTRY_SESSION_INVALID(1057, "当前会话已失效，请重新打开助手"),
    ASSAY_ENTRY_CONFIRMATION_INVALID(1058, "本次化验确认无效或已过期，请重新预览"),
    ASSAY_ENTRY_EXECUTION_DISABLED(1059, "化验保存暂未开放，请联系管理员");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}

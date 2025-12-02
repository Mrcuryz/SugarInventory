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
    ASSAY_GROUP_RECORD_NOT_FOUND(1027, "找不到化验验收标准"),
    SCREEN_MESH_NOT_FOUND(1028, "找不到关联筛网")
    ;

    // 获取 code 和 message
    private final int code;
    private final String message;

    // 构造函数
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

}

package com.laibin.sugarinventory.dt50;

final class WarehouseRecentOperation {
    final String operationTime;
    final String operationName;
    final String operatorName;
    final String palletCode;
    final String productName;
    final String fromWarehouseName;
    final String toWarehouseName;
    final String remark;

    WarehouseRecentOperation(String operationTime, String operationName, String operatorName,
                             String palletCode, String productName, String fromWarehouseName,
                             String toWarehouseName, String remark) {
        this.operationTime = operationTime;
        this.operationName = operationName;
        this.operatorName = operatorName;
        this.palletCode = palletCode;
        this.productName = productName;
        this.fromWarehouseName = fromWarehouseName;
        this.toWarehouseName = toWarehouseName;
        this.remark = remark;
    }
}

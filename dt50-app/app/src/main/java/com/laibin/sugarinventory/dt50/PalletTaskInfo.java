package com.laibin.sugarinventory.dt50;

final class PalletTaskInfo {
    final String code;
    final String productName;
    final String productionDate;
    final Integer quantity;
    final String unit;

    PalletTaskInfo(String code, String productName, String productionDate, Integer quantity, String unit) {
        this.code = code;
        this.productName = productName;
        this.productionDate = productionDate;
        this.quantity = quantity;
        this.unit = unit;
    }

    String quantityLabel() {
        if (quantity == null) {
            return "数量以待入库记录为准";
        }
        return quantity + ("1".equals(unit) ? " 件" : " 板");
    }
}

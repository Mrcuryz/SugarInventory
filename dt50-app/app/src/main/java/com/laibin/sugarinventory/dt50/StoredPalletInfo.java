package com.laibin.sugarinventory.dt50;

final class StoredPalletInfo {
    final String code;
    final String productName;
    final String productStatus;
    final String productionDate;
    final String warehouseName;
    final int quantity;
    final boolean pieceUnit;

    StoredPalletInfo(String code, String productName, String productStatus, String productionDate,
                     String warehouseName, int quantity, boolean pieceUnit) {
        this.code = code;
        this.productName = productName;
        this.productStatus = productStatus;
        this.productionDate = productionDate;
        this.warehouseName = warehouseName;
        this.quantity = quantity;
        this.pieceUnit = pieceUnit;
    }

    String quantityLabel() {
        return quantity + (pieceUnit ? " 件" : " 板");
    }
}

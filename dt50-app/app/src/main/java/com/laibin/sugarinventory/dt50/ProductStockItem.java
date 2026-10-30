package com.laibin.sugarinventory.dt50;

final class ProductStockItem {
    final String productName;
    final String productStatus;
    final String palletCode;
    final String warehouseName;
    final int palletQuantity;
    final int pieces;
    final String entryDate;
    final String productionDate;

    ProductStockItem(String productName, String productStatus, String palletCode,
                     String warehouseName, int palletQuantity, int pieces,
                     String entryDate, String productionDate) {
        this.productName = productName;
        this.productStatus = productStatus;
        this.palletCode = palletCode;
        this.warehouseName = warehouseName;
        this.palletQuantity = palletQuantity;
        this.pieces = pieces;
        this.entryDate = entryDate;
        this.productionDate = productionDate;
    }
}

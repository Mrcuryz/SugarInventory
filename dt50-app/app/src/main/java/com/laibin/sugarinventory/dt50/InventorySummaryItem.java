package com.laibin.sugarinventory.dt50;

final class InventorySummaryItem {
    final String productName;
    final int totalQuantity;
    final int totalPieces;
    final double totalWeight;
    final String firstEntryDate;

    InventorySummaryItem(String productName, int totalQuantity, int totalPieces,
                         double totalWeight, String firstEntryDate) {
        this.productName = productName;
        this.totalQuantity = totalQuantity;
        this.totalPieces = totalPieces;
        this.totalWeight = totalWeight;
        this.firstEntryDate = firstEntryDate;
    }
}

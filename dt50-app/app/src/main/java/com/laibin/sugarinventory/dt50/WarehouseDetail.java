package com.laibin.sugarinventory.dt50;

import java.util.List;

final class WarehouseDetail {
    final List<InventorySummaryItem> inventory;
    final List<WarehouseRecentOperation> recentOperations;

    WarehouseDetail(List<InventorySummaryItem> inventory,
                    List<WarehouseRecentOperation> recentOperations) {
        this.inventory = inventory;
        this.recentOperations = recentOperations;
    }
}

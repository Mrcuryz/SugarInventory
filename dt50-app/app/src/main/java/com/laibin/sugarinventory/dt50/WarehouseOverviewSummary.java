package com.laibin.sugarinventory.dt50;

import java.util.List;

final class WarehouseOverviewSummary {
    final int warehouseCount;
    final int emptyCount;
    final int fullCount;
    final double currentCapacity;
    final double maxCapacity;

    private WarehouseOverviewSummary(int warehouseCount, int emptyCount, int fullCount,
                                     double currentCapacity, double maxCapacity) {
        this.warehouseCount = warehouseCount;
        this.emptyCount = emptyCount;
        this.fullCount = fullCount;
        this.currentCapacity = currentCapacity;
        this.maxCapacity = maxCapacity;
    }

    static WarehouseOverviewSummary from(List<WarehouseCapacityItem> items) {
        int empty = 0;
        int full = 0;
        double current = 0;
        double maximum = 0;
        if (items != null) {
            for (WarehouseCapacityItem item : items) {
                if (item == null) {
                    continue;
                }
                if (item.isEmpty()) {
                    empty++;
                }
                if (item.isFull()) {
                    full++;
                }
                current += item.currentCapacity;
                maximum += item.maxCapacity;
            }
        }
        return new WarehouseOverviewSummary(items == null ? 0 : items.size(), empty, full, current, maximum);
    }

    int occupancyPercentage() {
        if (maxCapacity <= 0) {
            return 0;
        }
        return (int) Math.round(Math.min(100, Math.max(0, currentCapacity * 100 / maxCapacity)));
    }
}

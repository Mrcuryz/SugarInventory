package com.laibin.sugarinventory.dt50;

final class WarehouseCapacityItem {
    final int id;
    final String name;
    final String status;
    final double currentCapacity;
    final double maxCapacity;
    final double capacityPercentage;
    final int palletCount;
    final int productCount;

    WarehouseCapacityItem(int id, String name, String status, double currentCapacity,
                          double maxCapacity, double capacityPercentage,
                          int palletCount, int productCount) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.currentCapacity = currentCapacity;
        this.maxCapacity = maxCapacity;
        this.capacityPercentage = capacityPercentage;
        this.palletCount = palletCount;
        this.productCount = productCount;
    }

    boolean isEmpty() {
        return currentCapacity <= 0;
    }

    boolean isFull() {
        return maxCapacity > 0 && currentCapacity >= maxCapacity;
    }
}

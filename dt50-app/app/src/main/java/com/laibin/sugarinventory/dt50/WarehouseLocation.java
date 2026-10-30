package com.laibin.sugarinventory.dt50;

final class WarehouseLocation {
    final String code;
    final int id;
    final String name;
    final String status;
    final int currentCapacity;
    final int maxCapacity;

    WarehouseLocation(String code, int id, String name, String status, int currentCapacity, int maxCapacity) {
        this.code = code;
        this.id = id;
        this.name = name;
        this.status = status;
        this.currentCapacity = currentCapacity;
        this.maxCapacity = maxCapacity;
    }
}

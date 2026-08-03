package com.Laibin.SugarInventory.inventoryhistory.task;

@FunctionalInterface
public interface InventoryHistoryProcessTerminator {
    void exit(int exitCode);
}

package com.Laibin.SugarInventory.inventoryhistory.task;

import org.springframework.stereotype.Component;

@Component
public class SystemInventoryHistoryProcessTerminator
        implements InventoryHistoryProcessTerminator {
    @Override
    public void exit(int exitCode) {
        System.exit(exitCode);
    }
}

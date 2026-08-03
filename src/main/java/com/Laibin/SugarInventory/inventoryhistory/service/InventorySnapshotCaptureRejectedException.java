package com.Laibin.SugarInventory.inventoryhistory.service;

public class InventorySnapshotCaptureRejectedException extends RuntimeException {
    private final String errorCode;

    public InventorySnapshotCaptureRejectedException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

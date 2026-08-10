package com.Laibin.SugarInventory.agent.service;

public interface FinishInboundExecutionNoopAdapter {
    AdapterResult execute(String confirmationRef, String executionRef);

    record AdapterResult(String resultCode, int businessWrites) {
    }
}

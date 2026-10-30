package com.laibin.sugarinventory.dt50;

import java.util.ArrayList;
import java.util.List;

public class TransferActivity extends PalletOperationActivity {
    @Override
    protected String operationTitle() {
        return "扫码调拨";
    }

    @Override
    protected String operationVerb() {
        return "调拨";
    }

    @Override
    protected boolean requiresTargetWarehouse() {
        return true;
    }

    @Override
    protected boolean acceptsPallet(StoredPalletInfo item) {
        return "成品".equals(item.productStatus) || "半成品".equals(item.productStatus);
    }

    @Override
    protected String incompatiblePalletMessage(StoredPalletInfo item) {
        return item.code + " 对应的产品暂不支持扫码调拨";
    }

    @Override
    protected void createTasks(List<StoredPalletInfo> items, WarehouseLocation target,
                               ApiClient.Callback<Void> callback) {
        apiClient.createTransferTasks(target.name, codes(items), callback);
    }

    @Override
    protected void confirmTasks(List<StoredPalletInfo> items, ApiClient.Callback<Void> callback) {
        apiClient.confirmTransferTasks(codes(items), callback);
    }

    private List<String> codes(List<StoredPalletInfo> items) {
        List<String> result = new ArrayList<>();
        for (StoredPalletInfo item : items) {
            result.add(item.code);
        }
        return result;
    }
}

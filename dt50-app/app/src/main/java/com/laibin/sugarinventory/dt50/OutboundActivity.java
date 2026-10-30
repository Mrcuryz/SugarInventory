package com.laibin.sugarinventory.dt50;

import java.util.ArrayList;
import java.util.List;

public class OutboundActivity extends PalletOperationActivity {
    @Override
    protected String operationTitle() {
        return "扫码出库";
    }

    @Override
    protected String operationVerb() {
        return "出库";
    }

    @Override
    protected boolean requiresTargetWarehouse() {
        return false;
    }

    @Override
    protected boolean acceptsPallet(StoredPalletInfo item) {
        if (!"成品".equals(item.productStatus) && !"半成品".equals(item.productStatus)) {
            return false;
        }
        List<StoredPalletInfo> current = currentPallets();
        return current.isEmpty() || current.get(0).productStatus.equals(item.productStatus);
    }

    @Override
    protected String incompatiblePalletMessage(StoredPalletInfo item) {
        if (!"成品".equals(item.productStatus) && !"半成品".equals(item.productStatus)) {
            return item.code + " 对应的产品暂不支持扫码出库";
        }
        return "同一批次不能混合成品和半成品，请先完成当前批次";
    }

    @Override
    protected void createTasks(List<StoredPalletInfo> items, WarehouseLocation target,
                               ApiClient.Callback<Void> callback) {
        apiClient.createOutboundTasks(items.get(0).productStatus, codes(items), callback);
    }

    @Override
    protected void confirmTasks(List<StoredPalletInfo> items, ApiClient.Callback<Void> callback) {
        apiClient.confirmOutboundTasks(items.get(0).productStatus, codes(items), callback);
    }

    private List<String> codes(List<StoredPalletInfo> items) {
        List<String> result = new ArrayList<>();
        for (StoredPalletInfo item : items) {
            result.add(item.code);
        }
        return result;
    }
}

package com.laibin.sugarinventory.dt50;

import java.util.List;

public class HistoryActivity extends SimpleSearchListActivity {
    @Override
    protected boolean hasRequiredPermission() {
        return preferences.hasPermission("qrcode:view");
    }

    @Override
    protected String pageTitle() {
        return "操作记录";
    }

    @Override
    protected String pageSubtitle() {
        return "查看二维码的入库、出库和调拨记录";
    }

    @Override
    protected String searchHint() {
        return "扫描或输入二维码，留空查看最近记录";
    }

    @Override
    protected String emptyMessage() {
        return "未找到匹配的二维码操作记录";
    }

    @Override
    protected void performSearch(String query) {
        apiClient.searchFlowRecords(query, null, new ApiClient.Callback<>() {
            @Override
            public void onSuccess(List<FlowRecordItem> value) {
                for (int index = 0; index < value.size(); index++) {
                    FlowRecordItem item = value.get(index);
                    StringBuilder detail = new StringBuilder();
                    detail.append(MobileDisplay.dateTime(item.time));
                    if (!item.productLabel.trim().isEmpty()) {
                        detail.append("  ·  ").append(item.productLabel);
                    }
                    if (!item.fromWarehouseLabel.trim().isEmpty()
                            || !item.toWarehouseLabel.trim().isEmpty()) {
                        detail.append("\n位置：")
                                .append(MobileDisplay.fallback(item.fromWarehouseLabel, "无"))
                                .append(" → ")
                                .append(MobileDisplay.fallback(item.toWarehouseLabel, "无"));
                    }
                    if (!item.operatorLabel.trim().isEmpty()) {
                        detail.append("\n操作人：").append(item.operatorLabel);
                    }
                    addResultCard(index,
                            MobileDisplay.operation(item.eventLabel) + "  ·  "
                                    + MobileDisplay.fallback(item.codeLabel, "未记录二维码"),
                            detail.toString(), getColor(R.color.primary));
                }
                showResults(value.size());
            }

            @Override
            public void onError(String message, int httpStatus) {
                showError(message, httpStatus);
            }
        });
    }
}

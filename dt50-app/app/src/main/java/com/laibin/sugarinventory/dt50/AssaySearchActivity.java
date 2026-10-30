package com.laibin.sugarinventory.dt50;

import java.util.List;

public class AssaySearchActivity extends SimpleSearchListActivity {
    @Override
    protected boolean hasRequiredPermission() {
        return preferences.hasPermission("assay:view");
    }

    @Override
    protected String pageTitle() {
        return "化验查询";
    }

    @Override
    protected String pageSubtitle() {
        return "查看采样日期、判定和采用标准";
    }

    @Override
    protected String searchHint() {
        return "输入准确产品名称，留空查看最近记录";
    }

    @Override
    protected String emptyMessage() {
        return "未找到匹配的化验记录";
    }

    @Override
    protected void performSearch(String query) {
        apiClient.searchAssayRecords(query, new ApiClient.Callback<>() {
            @Override
            public void onSuccess(List<AssayRecordItem> value) {
                for (int index = 0; index < value.size(); index++) {
                    AssayRecordItem item = value.get(index);
                    StringBuilder detail = new StringBuilder();
                    detail.append("采样日期：").append(MobileDisplay.fallback(item.sampleDate, "未记录"));
                    if (!item.standardLabel.trim().isEmpty()) {
                        detail.append("\n采用标准：").append(item.standardLabel);
                    }
                    if (!item.testerLabel.trim().isEmpty()) {
                        detail.append("  ·  化验员：").append(item.testerLabel);
                    }
                    if (item.failedMetricCount > 0 || !item.failedMetricText.trim().isEmpty()) {
                        detail.append("\n异常指标：")
                                .append(MobileDisplay.fallback(item.failedMetricText,
                                        item.failedMetricCount + " 项"));
                    }
                    addResultCard(index,
                            item.productLabel + "  ·  " + item.judgeLabel,
                            detail.toString(),
                            "合格".equals(item.judgeLabel) ? getColor(R.color.success)
                                    : getColor(R.color.text_primary));
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

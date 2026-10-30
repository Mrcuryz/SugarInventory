package com.laibin.sugarinventory.dt50;

import java.util.List;

public class ProductSearchActivity extends SimpleSearchListActivity {
    @Override
    protected boolean hasRequiredPermission() {
        return preferences.hasPermission("inventory:view");
    }

    @Override
    protected String pageTitle() {
        return "查找产品";
    }

    @Override
    protected String pageSubtitle() {
        return "按产品名称查询库存数量和所在库位";
    }

    @Override
    protected String searchHint() {
        return "输入产品名称，支持模糊查询";
    }

    @Override
    protected String emptyMessage() {
        return "未找到匹配的在库产品";
    }

    @Override
    protected void performSearch(String query) {
        apiClient.searchProductStock(query, new ApiClient.Callback<>() {
            @Override
            public void onSuccess(List<ProductStockItem> value) {
                for (int index = 0; index < value.size(); index++) {
                    ProductStockItem item = value.get(index);
                    StringBuilder detail = new StringBuilder();
                    detail.append("当前位置：").append(item.warehouseName)
                            .append("  ·  数量 ").append(item.palletQuantity).append(" 板");
                    if (item.pieces > 0) {
                        detail.append(" ").append(item.pieces).append(" 件");
                    }
                    if (!item.productionDate.trim().isEmpty()) {
                        detail.append("\n生产日期：").append(item.productionDate);
                    }
                    if (!item.entryDate.trim().isEmpty()) {
                        detail.append("  ·  入库日期：").append(item.entryDate);
                    }
                    addResultCard(index,
                            item.productName + " · " + MobileDisplay.status(item.productStatus)
                                    + " · " + item.palletCode,
                            detail.toString(), getColor(R.color.text_primary));
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

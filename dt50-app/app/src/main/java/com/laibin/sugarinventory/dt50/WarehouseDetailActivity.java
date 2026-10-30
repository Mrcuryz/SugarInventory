package com.laibin.sugarinventory.dt50;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class WarehouseDetailActivity extends Activity {
    private AppPreferences preferences;
    private ApiClient apiClient;
    private int warehouseId;
    private Button refreshButton;
    private TextView statusText;
    private TextView inventoryEmptyText;
    private TextView operationEmptyText;
    private LinearLayout inventoryList;
    private LinearLayout operationList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = new AppPreferences(this);
        if (preferences.token().trim().isEmpty()) {
            returnToLogin();
            return;
        }
        warehouseId = getIntent().getIntExtra(WarehouseOverviewActivity.EXTRA_WAREHOUSE_ID, 0);
        if (warehouseId <= 0) {
            finish();
            return;
        }
        setContentView(R.layout.activity_warehouse_detail);
        apiClient = new ApiClient(preferences.serverUrl(), preferences.token());
        refreshButton = findViewById(R.id.detailRefreshButton);
        statusText = findViewById(R.id.detailStatusText);
        inventoryEmptyText = findViewById(R.id.detailInventoryEmptyText);
        operationEmptyText = findViewById(R.id.detailOperationEmptyText);
        inventoryList = findViewById(R.id.detailInventoryList);
        operationList = findViewById(R.id.detailOperationList);
        TextView title = findViewById(R.id.detailTitleText);
        TextView subtitle = findViewById(R.id.detailSubtitleText);
        title.setText(getIntent().getStringExtra(WarehouseOverviewActivity.EXTRA_WAREHOUSE_NAME));
        subtitle.setText(getIntent().getStringExtra(WarehouseOverviewActivity.EXTRA_WAREHOUSE_SUMMARY));
        findViewById(R.id.detailBackButton).setOnClickListener(view -> finish());
        refreshButton.setOnClickListener(view -> load());
        load();
    }

    private void load() {
        refreshButton.setEnabled(false);
        statusText.setText("正在读取实时库存与最近操作…");
        statusText.setBackgroundResource(R.drawable.bg_feedback_info);
        statusText.setTextColor(getColor(R.color.primary));
        apiClient.loadWarehouseDetail(warehouseId, new ApiClient.Callback<>() {
            @Override
            public void onSuccess(WarehouseDetail value) {
                refreshButton.setEnabled(true);
                renderInventory(value);
                renderOperations(value);
                statusText.setText("数据已更新 · " + value.inventory.size() + " 条库存明细 · "
                        + value.recentOperations.size() + " 条最近操作");
                statusText.setBackgroundResource(R.drawable.bg_feedback_success);
                statusText.setTextColor(getColor(R.color.success));
            }

            @Override
            public void onError(String message, int httpStatus) {
                refreshButton.setEnabled(true);
                statusText.setText("读取失败：" + message);
                statusText.setBackgroundResource(R.drawable.bg_feedback_error);
                statusText.setTextColor(getColor(R.color.danger));
                handleUnauthorized(httpStatus);
            }
        });
    }

    private void renderInventory(WarehouseDetail value) {
        inventoryList.removeAllViews();
        inventoryEmptyText.setVisibility(value.inventory.isEmpty() ? View.VISIBLE : View.GONE);
        for (int index = 0; index < value.inventory.size(); index++) {
            InventorySummaryItem item = value.inventory.get(index);
            StringBuilder details = new StringBuilder();
            details.append("板数 ").append(item.totalQuantity)
                    .append("  ·  件数 ").append(item.totalPieces);
            if (item.totalWeight > 0) {
                details.append("  ·  ").append(MobileDisplay.number(item.totalWeight)).append(" kg");
            }
            if (!item.firstEntryDate.trim().isEmpty()) {
                details.append("\n最早入库：").append(MobileDisplay.dateTime(item.firstEntryDate));
            }
            addCard(inventoryList, index,
                    item.productName,
                    details.toString(),
                    getColor(R.color.text_primary));
        }
    }

    private void renderOperations(WarehouseDetail value) {
        operationList.removeAllViews();
        operationEmptyText.setVisibility(value.recentOperations.isEmpty() ? View.VISIBLE : View.GONE);
        for (int index = 0; index < value.recentOperations.size(); index++) {
            WarehouseRecentOperation item = value.recentOperations.get(index);
            String object = join(" · ", item.palletCode, item.productName);
            String route = "";
            if (!item.fromWarehouseName.trim().isEmpty() || !item.toWarehouseName.trim().isEmpty()) {
                route = "位置：" + MobileDisplay.fallback(item.fromWarehouseName, "无")
                        + " → " + MobileDisplay.fallback(item.toWarehouseName, "无");
            }
            String detail = MobileDisplay.dateTime(item.operationTime)
                    + "  ·  " + MobileDisplay.fallback(item.operatorName, "未记录");
            if (!object.isEmpty()) {
                detail += "\n" + object;
            }
            if (!item.remark.trim().isEmpty()) {
                detail += "\n" + MobileDisplay.userMessage(item.remark);
            } else if (!route.isEmpty()) {
                detail += "\n" + route;
            }
            addCard(operationList, index,
                    MobileDisplay.operation(item.operationName),
                    detail,
                    getColor(R.color.primary));
        }
    }

    private void addCard(LinearLayout parent, int index, String title, String detail, int titleColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundResource(R.drawable.bg_card);
        card.setElevation(dp(2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (index > 0) {
            params.topMargin = dp(9);
        }
        card.setLayoutParams(params);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(titleColor);
        titleView.setTextSize(16f);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(titleView);

        TextView detailView = new TextView(this);
        detailView.setText(detail);
        detailView.setTextColor(getColor(R.color.text_secondary));
        detailView.setTextSize(13f);
        detailView.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(7);
        card.addView(detailView, detailParams);
        parent.addView(card);
    }

    private String join(String separator, String first, String second) {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();
        if (left.isEmpty()) {
            return right;
        }
        if (right.isEmpty()) {
            return left;
        }
        return left + separator + right;
    }

    private void handleUnauthorized(int httpStatus) {
        if (httpStatus != 401) {
            return;
        }
        preferences.clearToken();
        returnToLogin();
    }

    private void returnToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

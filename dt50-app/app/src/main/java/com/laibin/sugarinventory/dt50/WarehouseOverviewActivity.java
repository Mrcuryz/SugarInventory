package com.laibin.sugarinventory.dt50;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class WarehouseOverviewActivity extends Activity {
    static final String EXTRA_WAREHOUSE_ID = "warehouse_id";
    static final String EXTRA_WAREHOUSE_NAME = "warehouse_name";
    static final String EXTRA_WAREHOUSE_SUMMARY = "warehouse_summary";

    private AppPreferences preferences;
    private ApiClient apiClient;
    private Button refreshButton;
    private TextView statusText;
    private TextView capacityText;
    private TextView countsText;
    private TextView emptyText;
    private LinearLayout warehouseList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = new AppPreferences(this);
        if (preferences.token().trim().isEmpty()) {
            returnToLogin();
            return;
        }
        if (!preferences.hasPermission("inventory:view")
                && !preferences.hasPermission("record:query")) {
            finish();
            return;
        }
        setContentView(R.layout.activity_warehouse_overview);
        apiClient = new ApiClient(preferences.serverUrl(), preferences.token());
        refreshButton = findViewById(R.id.overviewRefreshButton);
        statusText = findViewById(R.id.overviewStatusText);
        capacityText = findViewById(R.id.overviewCapacityText);
        countsText = findViewById(R.id.overviewCountsText);
        emptyText = findViewById(R.id.warehouseOverviewEmptyText);
        warehouseList = findViewById(R.id.warehouseOverviewList);
        findViewById(R.id.overviewBackButton).setOnClickListener(view -> finish());
        refreshButton.setOnClickListener(view -> load());
        load();
    }

    private void load() {
        refreshButton.setEnabled(false);
        statusText.setText("正在读取实时仓库数据…");
        statusText.setBackgroundResource(R.drawable.bg_feedback_info);
        statusText.setTextColor(getColor(R.color.primary));
        apiClient.loadWarehouseOverview(new ApiClient.Callback<>() {
            @Override
            public void onSuccess(List<WarehouseCapacityItem> value) {
                refreshButton.setEnabled(true);
                render(value);
                statusText.setText("数据已更新 · 共 " + value.size() + " 个库位");
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

    private void render(List<WarehouseCapacityItem> items) {
        WarehouseOverviewSummary summary = WarehouseOverviewSummary.from(items);
        capacityText.setText(MobileDisplay.occupancy(summary.currentCapacity, summary.maxCapacity));
        countsText.setText("库位 " + summary.warehouseCount
                + " · 空置 " + summary.emptyCount
                + " · 满仓 " + summary.fullCount
                + "\n容量 " + MobileDisplay.number(summary.currentCapacity)
                + " / " + MobileDisplay.number(summary.maxCapacity));
        warehouseList.removeAllViews();
        emptyText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        for (int index = 0; index < items.size(); index++) {
            WarehouseCapacityItem item = items.get(index);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(16), dp(14), dp(14), dp(14));
            card.setBackgroundResource(R.drawable.bg_card);
            card.setElevation(dp(2));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (index > 0) {
                params.topMargin = dp(9);
            }
            card.setLayoutParams(params);

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView name = new TextView(this);
            name.setText(item.name);
            name.setTextColor(getColor(R.color.text_primary));
            name.setTextSize(17f);
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            content.addView(name);

            TextView meta = new TextView(this);
            meta.setText(MobileDisplay.status(item.status)
                    + " · 容量 " + MobileDisplay.number(item.currentCapacity)
                    + "/" + MobileDisplay.number(item.maxCapacity)
                    + " · 占用 " + MobileDisplay.occupancy(item.currentCapacity, item.maxCapacity)
                    + "\n二维码 " + item.palletCount + " · 产品 " + item.productCount + " 种");
            meta.setTextColor(getColor(R.color.text_secondary));
            meta.setTextSize(12.5f);
            meta.setLineSpacing(0, 1.22f);
            LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            metaParams.topMargin = dp(6);
            content.addView(meta, metaParams);
            card.addView(content);

            TextView next = new TextView(this);
            next.setText("查看  ›");
            next.setTextColor(getColor(R.color.primary));
            next.setTextSize(12f);
            next.setTypeface(null, android.graphics.Typeface.BOLD);
            next.setBackgroundResource(R.drawable.bg_badge_blue);
            next.setGravity(Gravity.CENTER);
            card.addView(next, new LinearLayout.LayoutParams(dp(66), dp(36)));
            card.setOnClickListener(view -> openDetail(item));
            warehouseList.addView(card);
        }
    }

    private void openDetail(WarehouseCapacityItem item) {
        Intent intent = new Intent(this, WarehouseDetailActivity.class);
        intent.putExtra(EXTRA_WAREHOUSE_ID, item.id);
        intent.putExtra(EXTRA_WAREHOUSE_NAME, item.name);
        intent.putExtra(EXTRA_WAREHOUSE_SUMMARY,
                MobileDisplay.status(item.status) + " · 容量 "
                        + MobileDisplay.number(item.currentCapacity) + "/"
                        + MobileDisplay.number(item.maxCapacity));
        startActivity(intent);
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

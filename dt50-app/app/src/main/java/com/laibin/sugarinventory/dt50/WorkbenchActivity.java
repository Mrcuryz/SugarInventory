package com.laibin.sugarinventory.dt50;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class WorkbenchActivity extends Activity {
    private AppPreferences preferences;
    private TextView operatorText;
    private TextView sessionStatusText;
    private View inboundButton;
    private View warehouseOverviewButton;
    private View productSearchButton;
    private View assayButton;
    private View historyButton;
    private View outboundButton;
    private View transferButton;
    private View dailyReportButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = new AppPreferences(this);
        if (preferences.token().trim().isEmpty()) {
            returnToLogin();
            return;
        }
        setContentView(R.layout.activity_workbench);
        operatorText = findViewById(R.id.workbenchOperatorText);
        sessionStatusText = findViewById(R.id.sessionStatusText);
        inboundButton = findViewById(R.id.inboundButton);
        warehouseOverviewButton = findViewById(R.id.warehouseOverviewButton);
        productSearchButton = findViewById(R.id.productSearchButton);
        assayButton = findViewById(R.id.assayButton);
        historyButton = findViewById(R.id.historyButton);
        outboundButton = findViewById(R.id.outboundButton);
        transferButton = findViewById(R.id.transferButton);
        dailyReportButton = findViewById(R.id.dailyReportButton);

        inboundButton.setOnClickListener(view -> openActivity(MainActivity.class));
        warehouseOverviewButton.setOnClickListener(view -> openActivity(WarehouseOverviewActivity.class));
        productSearchButton.setOnClickListener(view -> openActivity(ProductSearchActivity.class));
        assayButton.setOnClickListener(view -> openActivity(AssaySearchActivity.class));
        historyButton.setOnClickListener(view -> openActivity(HistoryActivity.class));
        outboundButton.setOnClickListener(view -> openActivity(OutboundActivity.class));
        transferButton.setOnClickListener(view -> openActivity(TransferActivity.class));
        dailyReportButton.setOnClickListener(view -> openActivity(ProductionDailyReportActivity.class));
        findViewById(R.id.logoutButton).setOnClickListener(view -> confirmLogout());

        renderPermissions();
        refreshIdentity();
    }

    private void refreshIdentity() {
        new ApiClient(preferences.serverUrl(), preferences.token()).loadCurrentUser(new ApiClient.Callback<>() {
            @Override
            public void onSuccess(ApiClient.CurrentUser value) {
                preferences.updateIdentity(value.name, value.permissionCodes);
                sessionStatusText.setVisibility(View.GONE);
                renderPermissions();
            }

            @Override
            public void onError(String message, int httpStatus) {
                if (httpStatus == 401) {
                    preferences.clearToken();
                    returnToLogin();
                    return;
                }
                sessionStatusText.setVisibility(View.VISIBLE);
                sessionStatusText.setText("部分功能暂不可用：" + message + "。可稍后重新进入工作台");
                sessionStatusText.setBackgroundResource(R.drawable.bg_feedback_error);
                sessionStatusText.setTextColor(getColor(R.color.danger));
            }
        });
    }

    private void renderPermissions() {
        operatorText.setText("你好，" + emptyFallback(preferences.userName(), "当前用户"));
        boolean canInbound = preferences.hasPermission("warehouse:view")
                && preferences.hasPermission("qrcode:view")
                && preferences.hasPermission("task:view")
                && preferences.hasPermission("task:confirm");
        setAccess(inboundButton, canInbound);

        boolean canReadInventory = preferences.hasPermission("inventory:view")
                || preferences.hasPermission("record:query");
        setAccess(warehouseOverviewButton, canReadInventory);
        setAccess(productSearchButton, preferences.hasPermission("inventory:view"));
        setAccess(assayButton, preferences.hasPermission("assay:view"));
        setAccess(historyButton, preferences.hasPermission("qrcode:view"));
        boolean canCreateAndConfirm = preferences.hasPermission("qrcode:view")
                && preferences.hasPermission("task:create")
                && preferences.hasPermission("task:confirm");
        setAccess(outboundButton, canCreateAndConfirm);
        setAccess(transferButton,
                canCreateAndConfirm && preferences.hasPermission("warehouse:view"));
        setAccess(dailyReportButton, preferences.hasPermission("production:daily-report:view"));
    }

    private void setAccess(View entry, boolean enabled) {
        entry.setEnabled(enabled);
        entry.setAlpha(enabled ? 1f : 0.52f);
        entry.setContentDescription(enabled ? null : "当前账号无权使用此功能");
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("退出登录？")
                .setMessage("退出后需要重新输入姓名和登录口令。")
                .setNegativeButton("取消", null)
                .setPositiveButton("退出", (dialog, which) -> {
                    preferences.clearDailyDrafts();
                    preferences.clearToken();
                    returnToLogin();
                })
                .show();
    }

    private void openActivity(Class<? extends Activity> target) {
        startActivity(new Intent(this, target));
    }

    private void returnToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private String emptyFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}

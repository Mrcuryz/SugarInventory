package com.laibin.sugarinventory.dt50;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

abstract class SimpleSearchListActivity extends Activity {
    protected AppPreferences preferences;
    protected ApiClient apiClient;
    protected EditText searchInput;
    protected LinearLayout resultList;
    protected TextView emptyText;
    private TextView statusText;
    private Button searchButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = new AppPreferences(this);
        if (preferences.token().trim().isEmpty()) {
            returnToLogin();
            return;
        }
        if (!hasRequiredPermission()) {
            finish();
            return;
        }
        setContentView(R.layout.activity_search_list);
        apiClient = new ApiClient(preferences.serverUrl(), preferences.token());
        searchInput = findViewById(R.id.searchInput);
        resultList = findViewById(R.id.searchResultList);
        emptyText = findViewById(R.id.searchEmptyText);
        statusText = findViewById(R.id.searchStatusText);
        searchButton = findViewById(R.id.searchButton);
        ((TextView) findViewById(R.id.searchTitleText)).setText(pageTitle());
        ((TextView) findViewById(R.id.searchSubtitleText)).setText(pageSubtitle());
        searchInput.setHint(searchHint());
        emptyText.setText(emptyMessage());
        findViewById(R.id.searchBackButton).setOnClickListener(view -> finish());
        searchButton.setOnClickListener(view -> search());
        searchInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });
        search();
    }

    protected abstract boolean hasRequiredPermission();
    protected abstract String pageTitle();
    protected abstract String pageSubtitle();
    protected abstract String searchHint();
    protected abstract String emptyMessage();
    protected abstract void performSearch(String query);

    private void search() {
        searchButton.setEnabled(false);
        resultList.removeAllViews();
        emptyText.setVisibility(android.view.View.GONE);
        showStatus("正在查询实时数据…", StatusKind.INFO);
        performSearch(searchInput.getText().toString().trim());
    }

    protected void showResults(int size) {
        searchButton.setEnabled(true);
        emptyText.setVisibility(size == 0 ? android.view.View.VISIBLE : android.view.View.GONE);
        showStatus(size == 0 ? "查询完成 · 暂无匹配记录" : "查询完成 · 共显示 " + size + " 条",
                size == 0 ? StatusKind.INFO : StatusKind.SUCCESS);
    }

    protected void showError(String message, int httpStatus) {
        searchButton.setEnabled(true);
        showStatus("查询失败：" + message, StatusKind.ERROR);
        if (httpStatus == 401) {
            preferences.clearToken();
            returnToLogin();
        }
    }

    protected void addResultCard(int index, String title, String detail, int titleColor) {
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
        titleView.setTypeface(null, Typeface.BOLD);
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
        resultList.addView(card);
    }

    private void showStatus(String message, StatusKind kind) {
        statusText.setText(message);
        switch (kind) {
            case SUCCESS -> {
                statusText.setBackgroundResource(R.drawable.bg_feedback_success);
                statusText.setTextColor(getColor(R.color.success));
            }
            case ERROR -> {
                statusText.setBackgroundResource(R.drawable.bg_feedback_error);
                statusText.setTextColor(getColor(R.color.danger));
            }
            default -> {
                statusText.setBackgroundResource(R.drawable.bg_feedback_info);
                statusText.setTextColor(getColor(R.color.primary));
            }
        }
    }

    private void returnToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    protected int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private enum StatusKind {
        INFO,
        SUCCESS,
        ERROR
    }
}

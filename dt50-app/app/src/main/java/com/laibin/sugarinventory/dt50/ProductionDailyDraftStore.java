package com.laibin.sugarinventory.dt50;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

final class ProductionDailyDraftStore {
    private static final String FILE_NAME = "production_daily_drafts";
    private final SharedPreferences preferences;

    ProductionDailyDraftStore(Context context) {
        preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    void save(String userName, ProductionDailyReportData.Report report) throws JSONException {
        preferences.edit().putString(key(userName, report.date),
                ProductionDailyReportData.localSnapshot(report).toString()).apply();
    }

    ProductionDailyReportData.Report load(String userName, String date) throws JSONException {
        String saved = preferences.getString(key(userName, date), "");
        return saved.isEmpty() ? null : ProductionDailyReportData.parseReport(new JSONObject(saved));
    }

    void remove(String userName, String date) {
        preferences.edit().remove(key(userName, date)).apply();
    }

    void saveImportPreview(String userName, ProductionDailyReportData.Report report) throws JSONException {
        preferences.edit().putString(importKey(userName, report.date),
                ProductionDailyReportData.localSnapshot(report).toString()).apply();
    }

    ProductionDailyReportData.Report loadImportPreview(String userName, String date) throws JSONException {
        String saved = preferences.getString(importKey(userName, date), "");
        return saved.isEmpty() ? null : ProductionDailyReportData.parseReport(new JSONObject(saved));
    }

    boolean hasImportPreview(String userName, String date) {
        return preferences.contains(importKey(userName, date));
    }

    void removeImportPreview(String userName, String date) {
        preferences.edit().remove(importKey(userName, date)).apply();
    }

    void clearAll() {
        preferences.edit().clear().apply();
    }

    private String key(String userName, String date) {
        return (userName == null ? "" : userName.trim()) + "|" + date;
    }

    private String importKey(String userName, String date) {
        return key(userName, date) + "|import";
    }
}

package com.laibin.sugarinventory.dt50;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

final class AppPreferences {
    private static final String FILE_NAME = "dt50_scanner";
    private final SharedPreferences preferences;

    AppPreferences(Context context) {
        preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    String serverUrl() {
        return normalizeServerUrl(BuildConfig.SERVER_BASE_URL);
    }

    String userName() {
        return preferences.getString("user_name", "");
    }

    String token() {
        return preferences.getString("token", "");
    }

    List<String> permissionCodes() {
        return SessionPermissions.decode(preferences.getString("permission_codes", ""));
    }

    boolean hasPermission(String permission) {
        return SessionPermissions.contains(preferences.getString("permission_codes", ""), permission);
    }

    void saveSession(String userName, String token, List<String> permissionCodes) {
        preferences.edit()
                .remove("server_url")
                .putString("user_name", userName == null ? "" : userName.trim())
                .putString("token", token == null ? "" : token.trim())
                .putString("permission_codes", SessionPermissions.encode(permissionCodes))
                .apply();
    }

    void updateIdentity(String userName, List<String> permissionCodes) {
        preferences.edit()
                .putString("user_name", userName == null ? "" : userName.trim())
                .putString("permission_codes", SessionPermissions.encode(permissionCodes))
                .apply();
    }

    void clearToken() {
        preferences.edit()
                .remove("token")
                .remove("permission_codes")
                .apply();
    }

    static String normalizeServerUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}

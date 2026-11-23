package com.laibin.sugarinventory.dt50;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Public HTTPS feed; the browser downloads, and the Android installer confirms installation. */
final class AppUpdateController {
    private static final long CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L;
    private final Activity activity;
    private final TextView button;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SharedPreferences preferences;
    private volatile boolean closed;
    private volatile HttpURLConnection connection;
    private boolean busy;
    private boolean resumed;
    private AlertDialog dialog;

    AppUpdateController(Activity activity, TextView button) {
        this.activity = activity;
        this.button = button;
        preferences = activity.getSharedPreferences("app_updates", Activity.MODE_PRIVATE);
        button.setText("v" + BuildConfig.VERSION_NAME + " · 检查更新");
        button.setOnClickListener(view -> check(true));
    }

    void onResume() {
        resumed = true;
        long last = preferences.getLong("last_check_elapsed", -1);
        long now = SystemClock.elapsedRealtime();
        if (last < 0 || now < last || now - last >= CHECK_INTERVAL_MS) check(false);
    }

    void onPause() {
        resumed = false;
        if (dialog != null) dialog.dismiss();
    }

    void close() {
        closed = true;
        onPause();
        main.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        HttpURLConnection active = connection;
        if (active != null) active.disconnect();
    }

    private void check(boolean manual) {
        if (closed || busy) return;
        busy = true;
        button.setEnabled(false);
        if (manual) toast("正在检查更新…");
        executor.execute(() -> {
            AppUpdateRelease release = null;
            String error = null;
            try {
                release = fetch();
            } catch (Exception exception) {
                error = "暂时无法检查更新，请确认网络连接后重试";
            }
            AppUpdateRelease result = release;
            String failure = error;
            main.post(() -> {
                if (closed || activity.isFinishing() || activity.isDestroyed()) return;
                busy = false;
                button.setEnabled(true);
                if (!resumed) return;
                if (failure != null) {
                    if (manual) toast(failure);
                    return;
                }
                preferences.edit().putLong("last_check_elapsed", SystemClock.elapsedRealtime()).apply();
                if (result == null || !result.newerThan(BuildConfig.VERSION_CODE)) {
                    if (manual) toast("当前已是最新版本");
                    return;
                }
                try {
                    if (!result.compatibleWith(activity.getPackageName(), installedCertificate(), Build.VERSION.SDK_INT)) {
                        if (manual) toast("新版本暂不适用于当前安装，请联系管理员");
                        return;
                    }
                } catch (Exception exception) {
                    if (manual) toast("暂时无法确认更新，请稍后重试");
                    return;
                }
                dialog = new AlertDialog.Builder(activity)
                        .setTitle("发现新版本 " + result.versionName)
                        .setMessage(result.notes + "\n\n请先保存正在填写的内容。下载完成后，打开安装包并按系统提示安装。")
                        .setNegativeButton("稍后再说", null)
                        .setPositiveButton("下载更新", (ignored, which) -> {
                            try {
                                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(result.downloadUrl))
                                        .addCategory(Intent.CATEGORY_BROWSABLE));
                            } catch (Exception exception) {
                                toast("未找到可下载更新的浏览器，请联系管理员安装");
                            }
                        }).show();
            });
        });
    }

    private AppUpdateRelease fetch() throws Exception {
        if (!BuildConfig.SERVER_BASE_URL.startsWith("https://")) throw new IllegalStateException();
        HttpURLConnection request = (HttpURLConnection) new URL(BuildConfig.SERVER_BASE_URL
                + "/app-updates/android/latest.json").openConnection();
        connection = request;
        try {
            if (closed) throw new IllegalStateException();
            request.setConnectTimeout(10000);
            request.setReadTimeout(10000);
            request.setInstanceFollowRedirects(false);
            request.setUseCaches(false);
            request.setRequestProperty("Accept", "application/json");
            request.setRequestProperty("Cache-Control", "no-cache");
            int status = request.getResponseCode();
            if (status != 200) throw new IllegalStateException();
            try (InputStream input = request.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (closed || output.size() + count > 32768) throw new IllegalStateException();
                    output.write(buffer, 0, count);
                }
                JSONObject json = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
                if (json.getInt("schemaVersion") != 1) throw new IllegalStateException();
                return new AppUpdateRelease(json.getInt("versionCode"), json.getString("versionName"),
                        json.getInt("minSdk"), json.getString("packageName"),
                        json.getString("certificateSha256"), json.getString("downloadUrl"),
                        json.getString("releaseNotes"), BuildConfig.SERVER_BASE_URL);
            }
        } finally {
            request.disconnect();
            connection = null;
        }
    }

    @SuppressWarnings("deprecation")
    private String installedCertificate() throws Exception {
        byte[] certificate = activity.getPackageManager().getPackageInfo(activity.getPackageName(),
                PackageManager.GET_SIGNATURES).signatures[0].toByteArray();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate);
        StringBuilder result = new StringBuilder();
        for (byte value : digest) result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }

    private void toast(String message) { Toast.makeText(activity, message, Toast.LENGTH_LONG).show(); }
}

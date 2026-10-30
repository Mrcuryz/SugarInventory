package com.laibin.sugarinventory.dt50;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int MAX_BATCH_SIZE = 20;

    private final List<PalletTaskInfo> pallets = new ArrayList<>();
    private final Set<String> acceptedCodes = new LinkedHashSet<>();
    private final Set<String> queuedInputs = new LinkedHashSet<>();
    private final ArrayDeque<String> scanQueue = new ArrayDeque<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AppPreferences preferences;
    private ApiClient apiClient;
    private ScannerInputBuffer scannerInputBuffer;
    private ToneGenerator toneGenerator;
    private Vibrator vibrator;
    private WarehouseLocation warehouse;
    private boolean resolvingPallet;
    private boolean confirming;
    private int sessionVersion;
    private int palletScanCount;
    private int duplicateCount;
    private int errorCount;

    private TextView operatorText;
    private TextView stepText;
    private TextView warehouseNameText;
    private TextView warehouseStatusText;
    private TextView feedbackText;
    private TextView totalCountText;
    private TextView successCountText;
    private TextView duplicateCountText;
    private TextView errorCountText;
    private TextView emptyText;
    private LinearLayout palletList;
    private Button confirmButton;
    private Button clearButton;
    private EditText scanInput;
    private boolean clearingScanInput;
    private final Runnable scanInputFlush = this::flushScanInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        preferences = new AppPreferences(this);
        if (preferences.token().trim().isEmpty()) {
            returnToLogin();
            return;
        }
        setContentView(R.layout.activity_main);
        apiClient = new ApiClient(preferences.serverUrl(), preferences.token());
        scannerInputBuffer = new ScannerInputBuffer(mainHandler, this::onScanned);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 85);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        bindViews();
        render();
    }

    private void bindViews() {
        operatorText = findViewById(R.id.operatorText);
        stepText = findViewById(R.id.stepText);
        warehouseNameText = findViewById(R.id.warehouseNameText);
        warehouseStatusText = findViewById(R.id.warehouseStatusText);
        feedbackText = findViewById(R.id.feedbackText);
        totalCountText = findViewById(R.id.totalCountText);
        successCountText = findViewById(R.id.successCountText);
        duplicateCountText = findViewById(R.id.duplicateCountText);
        errorCountText = findViewById(R.id.errorCountText);
        emptyText = findViewById(R.id.emptyText);
        palletList = findViewById(R.id.palletList);
        confirmButton = findViewById(R.id.confirmButton);
        clearButton = findViewById(R.id.clearButton);
        scanInput = findViewById(R.id.scanInput);
        View workbenchButton = findViewById(R.id.workbenchButton);

        operatorText.setText(preferences.userName());
        clearButton.setOnClickListener(view -> confirmClear());
        confirmButton.setOnClickListener(view -> confirmInbound());
        workbenchButton.setOnClickListener(view -> {
            startActivity(new Intent(this, WorkbenchActivity.class));
            finish();
        });
        configureScanInput();
    }

    private void configureScanInput() {
        scanInput.setShowSoftInputOnFocus(false);
        scanInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                flushScanInput();
                return true;
            }
            return false;
        });
        scanInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable value) {
                if (clearingScanInput || value.length() == 0) {
                    return;
                }
                mainHandler.removeCallbacks(scanInputFlush);
                mainHandler.postDelayed(scanInputFlush, 180L);
            }
        });
        scanInput.requestFocus();
        scanInput.post(this::hideSoftKeyboard);
    }

    private void hideSoftKeyboard() {
        InputMethodManager inputMethodManager =
                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (inputMethodManager != null && scanInput != null) {
            inputMethodManager.hideSoftInputFromWindow(scanInput.getWindowToken(), 0);
        }
    }

    private void flushScanInput() {
        mainHandler.removeCallbacks(scanInputFlush);
        String value = scanInput.getText().toString().trim();
        clearingScanInput = true;
        scanInput.setText("");
        clearingScanInput = false;
        scanInput.requestFocus();
        if (!value.isEmpty()) {
            onScanned(value);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && scanInput != null && !confirming) {
            scanInput.requestFocus();
            hideSoftKeyboard();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (scannerInputBuffer != null && scannerInputBuffer.onKeyEvent(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        if (toneGenerator != null) {
            toneGenerator.release();
        }
        super.onDestroy();
    }

    private void onScanned(String rawValue) {
        if (confirming) {
            showFeedback("正在确认入库，请稍候", FeedbackKind.INFO);
            return;
        }
        if (ScanCodeClassifier.isWarehouseCode(rawValue)) {
            handleWarehouseScan(rawValue);
            return;
        }
        palletScanCount++;
        if (warehouse == null) {
            errorCount++;
            reject("请先扫描库位二维码，再扫描产品二维码");
            renderCounts();
            return;
        }
        if (pallets.size() + scanQueue.size() + (resolvingPallet ? 1 : 0) >= MAX_BATCH_SIZE) {
            errorCount++;
            reject("一次最多处理 " + MAX_BATCH_SIZE + " 个二维码，请先确认本次入库");
            renderCounts();
            return;
        }
        String duplicateKey = ScanCodeClassifier.duplicateKey(rawValue);
        if (duplicateKey.trim().isEmpty()) {
            errorCount++;
            reject("未读取到有效扫码内容");
            renderCounts();
            return;
        }
        if (acceptedCodes.contains(duplicateKey) || queuedInputs.contains(duplicateKey)) {
            duplicateCount++;
            warn("重复扫码：" + duplicateKey);
            renderCounts();
            return;
        }
        queuedInputs.add(duplicateKey);
        scanQueue.add(rawValue);
        renderCounts();
        processNextPalletScan();
    }

    private void handleWarehouseScan(String rawValue) {
        if (warehouse != null && warehouse.code.equalsIgnoreCase(rawValue.trim())) {
            warn("当前入库库位已是 " + warehouse.name + "，请继续扫描产品二维码");
            return;
        }
        if (!pallets.isEmpty() || resolvingPallet || !scanQueue.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("切换库位？")
                    .setMessage("切换库位会清空当前待确认二维码，本次入库不会生效。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("清空并切换", (dialog, which) -> {
                        resetSession(false);
                        resolveWarehouse(rawValue);
                    })
                    .show();
            return;
        }
        resolveWarehouse(rawValue);
    }

    private void resolveWarehouse(String rawValue) {
        int expectedVersion = sessionVersion;
        showFeedback("正在核对库位码…", FeedbackKind.INFO);
        apiClient.resolveWarehouse(rawValue, new ApiClient.Callback<>() {
            @Override
            public void onSuccess(WarehouseLocation value) {
                if (expectedVersion != sessionVersion) {
                    return;
                }
                warehouse = value;
                accept("入库库位：" + value.name + "。请连续扫描产品二维码");
                render();
            }

            @Override
            public void onError(String message, int httpStatus) {
                if (expectedVersion != sessionVersion) {
                    return;
                }
                reject(message);
                handleUnauthorized(httpStatus);
            }
        });
    }

    private void processNextPalletScan() {
        if (resolvingPallet || scanQueue.isEmpty()) {
            return;
        }
        resolvingPallet = true;
        String rawValue = scanQueue.removeFirst();
        String inputKey = ScanCodeClassifier.duplicateKey(rawValue);
        int expectedVersion = sessionVersion;
        showFeedback("正在校验：" + inputKey, FeedbackKind.INFO);
        apiClient.resolveInboundTask(rawValue, new ApiClient.Callback<>() {
            @Override
            public void onSuccess(PalletTaskInfo value) {
                if (expectedVersion != sessionVersion) {
                    return;
                }
                resolvingPallet = false;
                queuedInputs.remove(inputKey);
                String canonicalKey = value.code.toUpperCase(Locale.ROOT);
                if (acceptedCodes.contains(canonicalKey)) {
                    duplicateCount++;
                    warn("重复扫码：" + value.code);
                } else {
                    pallets.add(value);
                    acceptedCodes.add(canonicalKey);
                    accept("已加入 " + value.code + " · " + value.productName);
                }
                render();
                processNextPalletScan();
            }

            @Override
            public void onError(String message, int httpStatus) {
                if (expectedVersion != sessionVersion) {
                    return;
                }
                resolvingPallet = false;
                queuedInputs.remove(inputKey);
                errorCount++;
                reject(message);
                render();
                handleUnauthorized(httpStatus);
                processNextPalletScan();
            }
        });
    }

    private void confirmInbound() {
        if (warehouse == null || pallets.isEmpty()) {
            reject("请先扫描库位二维码和至少一个待入库产品二维码");
            return;
        }
        if (resolvingPallet || !scanQueue.isEmpty()) {
            warn("仍有二维码正在读取，请稍候再确认");
            return;
        }
        StringBuilder preview = new StringBuilder();
        preview.append("入库库位：").append(warehouse.name)
                .append("\n二维码数量：").append(pallets.size()).append("\n\n");
        for (PalletTaskInfo pallet : pallets) {
            preview.append("• ").append(pallet.code).append("  ")
                    .append(pallet.productName).append("\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("确认入库")
                .setMessage(preview.toString())
                .setNegativeButton("返回核对", null)
                .setPositiveButton("确认入库", (dialog, which) -> executeConfirm())
                .show();
    }

    private void executeConfirm() {
        confirming = true;
        confirmButton.setEnabled(false);
        confirmButton.setText("正在确认…");
        int expectedVersion = sessionVersion;
        String warehouseName = warehouse.name;
        List<String> codes = new ArrayList<>();
        for (PalletTaskInfo pallet : pallets) {
            codes.add(pallet.code);
        }
        apiClient.confirmInbound(warehouseName, codes, new ApiClient.Callback<>() {
            @Override
            public void onSuccess(Integer affected) {
                if (expectedVersion != sessionVersion) {
                    return;
                }
                confirming = false;
                resetSession(false);
                accept("入库完成：" + affected + " 个二维码。请扫描下一个库位二维码");
                render();
            }

            @Override
            public void onError(String message, int httpStatus) {
                if (expectedVersion != sessionVersion) {
                    return;
                }
                confirming = false;
                reject("入库未执行：" + message);
                render();
                handleUnauthorized(httpStatus);
            }
        });
    }

    private void confirmClear() {
        if (warehouse == null && pallets.isEmpty() && scanQueue.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("重新开始？")
                .setMessage("将清空当前库位和待确认二维码，本次入库不会生效。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> {
                    resetSession(true);
                    showFeedback("已清空，请重新扫描库位二维码", FeedbackKind.INFO);
                    render();
                })
                .show();
    }

    private void resetSession(boolean resetCounters) {
        sessionVersion++;
        warehouse = null;
        pallets.clear();
        acceptedCodes.clear();
        queuedInputs.clear();
        scanQueue.clear();
        resolvingPallet = false;
        confirming = false;
        if (resetCounters) {
            palletScanCount = 0;
            duplicateCount = 0;
            errorCount = 0;
        }
    }

    private void render() {
        if (warehouse == null) {
            stepText.setText("第 1 步 · 扫描库位二维码");
            warehouseNameText.setText("请先扫描入库库位");
            warehouseStatusText.setText("对准库位二维码，按下扫描键");
        } else {
            stepText.setText("第 2 步 · 扫描产品二维码");
            warehouseNameText.setText(warehouse.name);
            warehouseStatusText.setText("状态：" + MobileDisplay.status(warehouse.status)
                    + " · 容量 " + warehouse.currentCapacity + "/" + warehouse.maxCapacity);
        }
        renderCounts();
        renderPallets();
        boolean canConfirm = warehouse != null && !pallets.isEmpty()
                && !resolvingPallet && scanQueue.isEmpty() && !confirming;
        confirmButton.setEnabled(canConfirm);
        confirmButton.setAlpha(canConfirm ? 1f : 0.5f);
        if (!confirming) {
            confirmButton.setText("确认入库（" + pallets.size() + "）");
        }
    }

    private void renderCounts() {
        totalCountText.setText(palletScanCount + "\n已扫描");
        successCountText.setText(pallets.size() + "\n待确认");
        duplicateCountText.setText(duplicateCount + "\n重复");
        errorCountText.setText(errorCount + "\n失败");
    }

    private void renderPallets() {
        palletList.removeAllViews();
        emptyText.setVisibility(pallets.isEmpty() ? View.VISIBLE : View.GONE);
        for (int index = 0; index < pallets.size(); index++) {
            PalletTaskInfo pallet = pallets.get(index);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(android.view.Gravity.CENTER_VERTICAL);
            card.setPadding(dp(16), dp(14), dp(10), dp(14));
            card.setBackgroundResource(R.drawable.bg_card);
            card.setElevation(dp(2));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (index > 0) {
                cardParams.topMargin = dp(9);
            }
            card.setLayoutParams(cardParams);

            TextView description = new TextView(this);
            description.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            description.setText((index + 1) + ". " + pallet.code + "\n"
                    + pallet.productName + " · " + pallet.quantityLabel() + "\n"
                    + "生产日期：" + emptyFallback(pallet.productionDate, "未记录"));
            description.setTextColor(getColor(R.color.text_primary));
            description.setTextSize(15f);
            description.setLineSpacing(0, 1.18f);
            card.addView(description);

            Button remove = new Button(this);
            remove.setText("移除");
            remove.setTextSize(13f);
            remove.setTextColor(getColor(R.color.danger));
            remove.setAllCaps(false);
            remove.setBackgroundResource(R.drawable.bg_remove_chip);
            remove.setStateListAnimator(null);
            remove.setMinimumHeight(0);
            remove.setMinimumWidth(0);
            remove.setPadding(0, 0, 0, 0);
            remove.setOnClickListener(view -> removePallet(pallet));
            card.addView(remove, new LinearLayout.LayoutParams(dp(70), dp(48)));
            palletList.addView(card);
        }
    }

    private void removePallet(PalletTaskInfo pallet) {
        pallets.remove(pallet);
        acceptedCodes.remove(pallet.code.toUpperCase(Locale.ROOT));
        showFeedback("已从清单移除 " + pallet.code, FeedbackKind.INFO);
        render();
    }

    private void accept(String message) {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 120);
        vibrate(55);
        showFeedback(message, FeedbackKind.SUCCESS);
    }

    private void warn(String message) {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 160);
        vibrate(90);
        showFeedback(message, FeedbackKind.INFO);
    }

    private void reject(String message) {
        toneGenerator.startTone(ToneGenerator.TONE_SUP_ERROR, 260);
        vibrate(180);
        showFeedback(message, FeedbackKind.ERROR);
    }

    private void showFeedback(String message, FeedbackKind kind) {
        feedbackText.setText(message);
        switch (kind) {
            case SUCCESS -> {
                feedbackText.setBackgroundResource(R.drawable.bg_feedback_success);
                feedbackText.setTextColor(getColor(R.color.success));
            }
            case ERROR -> {
                feedbackText.setBackgroundResource(R.drawable.bg_feedback_error);
                feedbackText.setTextColor(getColor(R.color.danger));
            }
            default -> {
                feedbackText.setBackgroundResource(R.drawable.bg_feedback_info);
                feedbackText.setTextColor(getColor(R.color.primary));
            }
        }
    }

    private void vibrate(long durationMs) {
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(durationMs);
        }
    }

    private void handleUnauthorized(int httpStatus) {
        if (httpStatus != 401) {
            return;
        }
        preferences.clearToken();
        new AlertDialog.Builder(this)
                .setTitle("登录已失效")
                .setMessage("请重新登录后继续扫码。当前未确认清单不会执行入库。")
                .setCancelable(false)
                .setPositiveButton("重新登录", (dialog, which) -> returnToLogin())
                .show();
    }

    private void returnToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private String emptyFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private enum FeedbackKind {
        INFO,
        SUCCESS,
        ERROR
    }
}

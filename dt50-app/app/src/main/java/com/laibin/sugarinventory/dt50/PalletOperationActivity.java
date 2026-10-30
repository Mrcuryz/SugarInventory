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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

abstract class PalletOperationActivity extends Activity {
    private static final int MAX_BATCH_SIZE = 20;

    private final List<StoredPalletInfo> pallets = new ArrayList<>();
    private final Set<String> acceptedCodes = new LinkedHashSet<>();
    private final Set<String> queuedCodes = new LinkedHashSet<>();
    private final ArrayDeque<String> scanQueue = new ArrayDeque<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    protected AppPreferences preferences;
    protected ApiClient apiClient;
    private ScannerInputBuffer scannerInputBuffer;
    private ToneGenerator toneGenerator;
    private Vibrator vibrator;
    private WarehouseLocation targetWarehouse;
    private boolean resolvingPallet;
    private boolean executing;
    private boolean tasksCreated;
    private int sessionVersion;

    private TextView stepText;
    private TextView targetText;
    private TextView instructionText;
    private TextView feedbackText;
    private TextView countText;
    private TextView emptyText;
    private LinearLayout palletList;
    private Button clearButton;
    private Button confirmButton;
    private EditText scanInput;
    private boolean clearingInput;
    private final Runnable inputFlush = this::flushScanInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        preferences = new AppPreferences(this);
        if (preferences.token().trim().isEmpty()) {
            returnToLogin();
            return;
        }
        if (!hasRequiredPermissions()) {
            finish();
            return;
        }
        setContentView(R.layout.activity_pallet_operation);
        apiClient = new ApiClient(preferences.serverUrl(), preferences.token());
        scannerInputBuffer = new ScannerInputBuffer(handler, this::onScanned);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 85);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        bindViews();
        configureScanInput();
        render();
        showFeedback(
                requiresTargetWarehouse() ? "等待扫描调入库位二维码" : "等待扫描产品二维码",
                FeedbackKind.INFO
        );
    }

    protected abstract String operationTitle();
    protected abstract String operationVerb();
    protected abstract boolean requiresTargetWarehouse();
    protected abstract boolean acceptsPallet(StoredPalletInfo item);
    protected abstract String incompatiblePalletMessage(StoredPalletInfo item);
    protected abstract void createTasks(List<StoredPalletInfo> items, WarehouseLocation target,
                                        ApiClient.Callback<Void> callback);
    protected abstract void confirmTasks(List<StoredPalletInfo> items, ApiClient.Callback<Void> callback);

    protected final List<StoredPalletInfo> currentPallets() {
        return new ArrayList<>(pallets);
    }

    private boolean hasRequiredPermissions() {
        boolean common = preferences.hasPermission("qrcode:view")
                && preferences.hasPermission("task:create")
                && preferences.hasPermission("task:confirm");
        return common && (!requiresTargetWarehouse() || preferences.hasPermission("warehouse:view"));
    }

    private void bindViews() {
        ((TextView) findViewById(R.id.operationTitleText)).setText(operationTitle());
        ((TextView) findViewById(R.id.operationOperatorText))
                .setText(preferences.userName());
        stepText = findViewById(R.id.operationStepText);
        targetText = findViewById(R.id.operationTargetText);
        instructionText = findViewById(R.id.operationInstructionText);
        feedbackText = findViewById(R.id.operationFeedbackText);
        countText = findViewById(R.id.operationCountText);
        emptyText = findViewById(R.id.operationEmptyText);
        palletList = findViewById(R.id.operationPalletList);
        clearButton = findViewById(R.id.operationClearButton);
        confirmButton = findViewById(R.id.operationConfirmButton);
        scanInput = findViewById(R.id.operationScanInput);
        findViewById(R.id.operationBackButton).setOnClickListener(view -> confirmLeave());
        clearButton.setOnClickListener(view -> confirmClear());
        confirmButton.setOnClickListener(view -> confirmOrRetry());
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
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable value) {
                if (clearingInput || value.length() == 0) {
                    return;
                }
                handler.removeCallbacks(inputFlush);
                handler.postDelayed(inputFlush, 180L);
            }
        });
        scanInput.requestFocus();
        scanInput.post(this::hideKeyboard);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (scannerInputBuffer != null && scannerInputBuffer.onKeyEvent(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && scanInput != null && !executing) {
            scanInput.requestFocus();
            hideKeyboard();
        }
    }

    @Override
    protected void onDestroy() {
        if (toneGenerator != null) {
            toneGenerator.release();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        confirmLeave();
    }

    private void flushScanInput() {
        handler.removeCallbacks(inputFlush);
        String value = scanInput.getText().toString().trim();
        clearingInput = true;
        scanInput.setText("");
        clearingInput = false;
        scanInput.requestFocus();
        if (!value.isEmpty()) {
            onScanned(value);
        }
    }

    private void onScanned(String rawValue) {
        if (executing || tasksCreated) {
            showFeedback("正在处理，请稍候", FeedbackKind.INFO);
            return;
        }
        if (requiresTargetWarehouse() && ScanCodeClassifier.isWarehouseCode(rawValue)) {
            handleWarehouseScan(rawValue);
            return;
        }
        if (requiresTargetWarehouse() && targetWarehouse == null) {
            reject("请先扫描调入库位二维码，再扫描产品二维码");
            return;
        }
        if (pallets.size() + scanQueue.size() + (resolvingPallet ? 1 : 0) >= MAX_BATCH_SIZE) {
            reject("一次最多处理 " + MAX_BATCH_SIZE + " 个二维码，请先确认本次操作");
            return;
        }
        String key = ScanCodeClassifier.duplicateKey(rawValue);
        if (key.isEmpty()) {
            reject("未读取到有效扫码内容");
            return;
        }
        if (acceptedCodes.contains(key) || queuedCodes.contains(key)) {
            warn("重复扫码：" + key);
            return;
        }
        queuedCodes.add(key);
        scanQueue.add(rawValue);
        processNext();
    }

    private void handleWarehouseScan(String rawValue) {
        if (!pallets.isEmpty() || resolvingPallet || !scanQueue.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("切换调入库位？")
                    .setMessage("切换会清空当前待确认二维码，本次调拨不会生效。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("清空并切换", (dialog, which) -> {
                        resetSession();
                        resolveWarehouse(rawValue);
                    })
                    .show();
            return;
        }
        resolveWarehouse(rawValue);
    }

    private void resolveWarehouse(String rawValue) {
        int expectedVersion = sessionVersion;
        showFeedback("正在读取调入库位…", FeedbackKind.INFO);
        apiClient.resolveWarehouse(rawValue, new ApiClient.Callback<>() {
            @Override
            public void onSuccess(WarehouseLocation value) {
                if (expectedVersion != sessionVersion) return;
                targetWarehouse = value;
                accept("调入库位：" + value.name + "。请连续扫描产品二维码");
                render();
            }

            @Override
            public void onError(String message, int httpStatus) {
                if (expectedVersion != sessionVersion) return;
                reject(message);
                handleUnauthorized(httpStatus);
            }
        });
    }

    private void processNext() {
        if (resolvingPallet || scanQueue.isEmpty()) {
            return;
        }
        resolvingPallet = true;
        String rawValue = scanQueue.removeFirst();
        String key = ScanCodeClassifier.duplicateKey(rawValue);
        int expectedVersion = sessionVersion;
        showFeedback("正在读取二维码：" + key, FeedbackKind.INFO);
        apiClient.resolveStoredPallet(rawValue, new ApiClient.Callback<>() {
            @Override
            public void onSuccess(StoredPalletInfo value) {
                if (expectedVersion != sessionVersion) return;
                resolvingPallet = false;
                queuedCodes.remove(key);
                String canonical = value.code.toUpperCase(Locale.ROOT);
                if (acceptedCodes.contains(canonical)) {
                    warn("重复扫码：" + value.code);
                } else if (!acceptsPallet(value)) {
                    reject(incompatiblePalletMessage(value));
                } else if (requiresTargetWarehouse()
                        && targetWarehouse.name.equals(value.warehouseName)) {
                    reject(value.code + " 已在调入库位 " + targetWarehouse.name);
                } else {
                    pallets.add(value);
                    acceptedCodes.add(canonical);
                    accept("已加入 " + value.code + " · " + value.productName);
                }
                render();
                processNext();
            }

            @Override
            public void onError(String message, int httpStatus) {
                if (expectedVersion != sessionVersion) return;
                resolvingPallet = false;
                queuedCodes.remove(key);
                reject(message);
                render();
                handleUnauthorized(httpStatus);
                processNext();
            }
        });
    }

    private void confirmOrRetry() {
        if (pallets.isEmpty()) {
            reject("请先扫描至少一个在库产品二维码");
            return;
        }
        if (tasksCreated) {
            confirmCreatedTasks();
            return;
        }
        StringBuilder message = new StringBuilder();
        if (targetWarehouse != null) {
            message.append("调入库位：").append(targetWarehouse.name).append("\n");
        }
        message.append("二维码数量：").append(pallets.size()).append("\n\n");
        for (StoredPalletInfo pallet : pallets) {
            message.append("• ").append(pallet.code).append("  ")
                    .append(pallet.productName).append("  ")
                    .append(pallet.quantityLabel()).append("\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("确认" + operationVerb())
                .setMessage(message.toString())
                .setNegativeButton("返回核对", null)
                .setPositiveButton("确认" + operationVerb(), (dialog, which) -> createAndConfirm())
                .show();
    }

    private void createAndConfirm() {
        executing = true;
        render();
        showFeedback("正在提交" + operationVerb() + "…", FeedbackKind.INFO);
        List<StoredPalletInfo> snapshot = new ArrayList<>(pallets);
        int expectedVersion = sessionVersion;
        createTasks(snapshot, targetWarehouse, new ApiClient.Callback<>() {
            @Override
            public void onSuccess(Void value) {
                if (expectedVersion != sessionVersion) return;
                tasksCreated = true;
                showFeedback("已提交，正在确认" + operationVerb() + "…", FeedbackKind.INFO);
                confirmCreatedTasks();
            }

            @Override
            public void onError(String message, int httpStatus) {
                if (expectedVersion != sessionVersion) return;
                executing = false;
                reject(operationVerb() + "未提交：" + message);
                render();
                handleUnauthorized(httpStatus);
            }
        });
    }

    private void confirmCreatedTasks() {
        executing = true;
        render();
        int expectedVersion = sessionVersion;
        List<StoredPalletInfo> snapshot = new ArrayList<>(pallets);
        confirmTasks(snapshot, new ApiClient.Callback<>() {
            @Override
            public void onSuccess(Void value) {
                if (expectedVersion != sessionVersion) return;
                int affected = pallets.size();
                resetSession();
                accept(operationVerb() + "完成：" + affected + " 个二维码");
                render();
            }

            @Override
            public void onError(String message, int httpStatus) {
                if (expectedVersion != sessionVersion) return;
                executing = false;
                tasksCreated = true;
                reject(operationVerb() + "尚未确认：" + message + "。请核对后重试");
                render();
                handleUnauthorized(httpStatus);
            }
        });
    }

    private void render() {
        if (requiresTargetWarehouse() && targetWarehouse == null) {
            stepText.setText("第 1 步 · 扫描调入库位二维码");
            targetText.setText("请先扫描调入库位");
            instructionText.setText("对准库位二维码，按下扫描键");
        } else {
            stepText.setText(requiresTargetWarehouse()
                    ? "第 2 步 · 扫描待调拨产品二维码"
                    : "扫描待" + operationVerb() + "产品二维码");
            targetText.setText(targetWarehouse == null ? "请扫描产品二维码" : targetWarehouse.name);
            instructionText.setText("扫描后会显示产品、数量和当前库位");
        }
        countText.setText("待确认二维码 · " + pallets.size());
        renderPallets();
        boolean ready = !pallets.isEmpty() && !resolvingPallet && scanQueue.isEmpty() && !executing;
        confirmButton.setEnabled(ready);
        confirmButton.setAlpha(ready ? 1f : 0.5f);
        confirmButton.setText(tasksCreated
                ? "重试确认" + operationVerb() + "（" + pallets.size() + "）"
                : "确认" + operationVerb() + "（" + pallets.size() + "）");
        clearButton.setEnabled(!tasksCreated && !executing);
        clearButton.setAlpha(clearButton.isEnabled() ? 1f : 0.5f);
    }

    private void renderPallets() {
        palletList.removeAllViews();
        emptyText.setVisibility(pallets.isEmpty() ? View.VISIBLE : View.GONE);
        for (int index = 0; index < pallets.size(); index++) {
            StoredPalletInfo item = pallets.get(index);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setPadding(dp(16), dp(14), dp(8), dp(14));
            card.setBackgroundResource(R.drawable.bg_card);
            card.setElevation(dp(2));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (index > 0) params.topMargin = dp(9);
            card.setLayoutParams(params);

            TextView detail = new TextView(this);
            detail.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            detail.setText((index + 1) + ". " + item.code + "\n"
                    + item.productName + " · " + MobileDisplay.status(item.productStatus)
                    + " · " + item.quantityLabel()
                    + "\n当前位置：" + item.warehouseName);
            detail.setTextColor(getColor(R.color.text_primary));
            detail.setTextSize(15f);
            detail.setLineSpacing(0, 1.18f);
            card.addView(detail);

            Button remove = new Button(this);
            remove.setText("移除");
            remove.setTextColor(getColor(R.color.danger));
            remove.setTextSize(13f);
            remove.setAllCaps(false);
            remove.setEnabled(!tasksCreated && !executing);
            remove.setBackgroundResource(R.drawable.bg_remove_chip);
            remove.setStateListAnimator(null);
            remove.setMinimumHeight(0);
            remove.setMinimumWidth(0);
            remove.setPadding(0, 0, 0, 0);
            remove.setOnClickListener(view -> {
                pallets.remove(item);
                acceptedCodes.remove(item.code.toUpperCase(Locale.ROOT));
                render();
            });
            card.addView(remove, new LinearLayout.LayoutParams(dp(70), dp(48)));
            palletList.addView(card);
        }
    }

    private void confirmClear() {
        if (pallets.isEmpty() && targetWarehouse == null) return;
        new AlertDialog.Builder(this)
                .setTitle("重新开始？")
                .setMessage("当前清单不会提交任何操作。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> {
                    resetSession();
                    showFeedback("已清空，请重新扫描", FeedbackKind.INFO);
                    render();
                })
                .show();
    }

    private void confirmLeave() {
        if (pallets.isEmpty()) {
            finish();
            return;
        }
        String message = tasksCreated
                ? "本次操作已经提交但尚未确认。离开后可在待处理任务中继续处理。"
                : "离开会清空当前扫描清单，不会提交任何操作。";
        new AlertDialog.Builder(this)
                .setTitle("返回工作台？")
                .setMessage(message)
                .setNegativeButton("继续处理", null)
                .setPositiveButton("返回", (dialog, which) -> finish())
                .show();
    }

    private void resetSession() {
        sessionVersion++;
        targetWarehouse = null;
        pallets.clear();
        acceptedCodes.clear();
        queuedCodes.clear();
        scanQueue.clear();
        resolvingPallet = false;
        executing = false;
        tasksCreated = false;
    }

    private void hideKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null && scanInput != null) {
            manager.hideSoftInputFromWindow(scanInput.getWindowToken(), 0);
        }
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
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(durationMs);
        }
    }

    private void handleUnauthorized(int httpStatus) {
        if (httpStatus != 401) return;
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

    private enum FeedbackKind {
        INFO, SUCCESS, ERROR
    }
}

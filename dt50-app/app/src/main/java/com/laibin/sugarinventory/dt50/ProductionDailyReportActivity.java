package com.laibin.sugarinventory.dt50;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Native DT50 editor for the existing production daily report API. */
public class ProductionDailyReportActivity extends Activity {
    private static final int PICK_IMPORT = 241;
    private static final int SAVE_EXPORT = 242;
    private static final int MAX_IMPORT_BYTES = 10 * 1024 * 1024;
    private static final String PERMISSION_VIEW = "production:daily-report:view";
    private static final String PERMISSION_EDIT = "production:daily-report:edit";
    private static final String PERMISSION_EXPORT = "production:daily-report:export";

    private AppPreferences preferences;
    private ApiClient api;
    private ProductionDailyDraftStore drafts;
    private ProductionDailyReportData.Report report;
    private ProductionDailyReportData.Report serverCopy;
    private List<ProductionDailyReportData.ProductOption> productOptions = new ArrayList<>();
    private final Map<String, ProductionDailyReportData.History> calendarHistory = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable draftWrite;
    private String date = ProductionDailyDates.today();
    private String calendarMonth = date.substring(0, 7);
    private String sectionCode = "";
    private String groupCode = "";
    private int level;
    private int field;
    private int loadSequence;
    private int calendarSequence;
    private boolean busy;
    private boolean showingServerCopy;
    private byte[] pendingExport;
    private Dialog currentDialog;

    private TextView titleView;
    private TextView subtitleView;
    private TextView messageView;
    private ScrollView scrollView;
    private LinearLayout contentView;
    private LinearLayout tabsView;
    private LinearLayout bottomBar;
    private Button secondaryAction;
    private Button primaryAction;
    private TextView liveStatusBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = new AppPreferences(this);
        if (preferences.token().isEmpty()) {
            returnToLogin();
            return;
        }
        if (!preferences.hasPermission(PERMISSION_VIEW)) {
            Toast.makeText(this, "当前账号没有生产日报查看权限", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        api = new ApiClient(preferences.serverUrl(), preferences.token());
        drafts = new ProductionDailyDraftStore(this);
        setContentView(R.layout.activity_production_daily_report);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        titleView = findViewById(R.id.dailyTitle);
        subtitleView = findViewById(R.id.dailySubtitle);
        messageView = findViewById(R.id.dailyMessage);
        scrollView = findViewById(R.id.dailyScroll);
        contentView = findViewById(R.id.dailyContent);
        tabsView = findViewById(R.id.dailyTabs);
        bottomBar = findViewById(R.id.dailyBottomBar);
        secondaryAction = findViewById(R.id.dailySecondaryAction);
        primaryAction = findViewById(R.id.dailyPrimaryAction);
        findViewById(R.id.dailyBack).setOnClickListener(view -> navigateBack());
        findViewById(R.id.dailyMore).setOnClickListener(this::showMoreMenu);
        if (savedInstanceState != null) {
            date = savedInstanceState.getString("daily_date", date);
            sectionCode = savedInstanceState.getString("daily_section", "");
            groupCode = savedInstanceState.getString("daily_group", "");
            level = savedInstanceState.getInt("daily_level", 0);
            field = savedInstanceState.getInt("daily_field", 0);
            calendarMonth = date.substring(0, 7);
        }
        showMessage("正在读取生产日报…", false);
        loadProducts();
        loadReport(date, true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        persistDraft();
        outState.putString("daily_date", date);
        outState.putString("daily_section", sectionCode);
        outState.putString("daily_group", groupCode);
        outState.putInt("daily_level", level);
        outState.putInt("daily_field", field);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        persistDraft();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        navigateBack();
    }

    private void navigateBack() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
            return;
        }
        if (level > 0) {
            level--;
            render(true);
            return;
        }
        if (report != null && report.dirty()) {
            new AlertDialog.Builder(this)
                    .setTitle("离开生产日报？")
                    .setMessage("未保存的填写内容已暂存在本机，下次进入仍可继续编辑。")
                    .setNegativeButton("继续编辑", null)
                    .setPositiveButton("离开", (dialog, which) -> finish())
                    .show();
        } else {
            finish();
        }
    }

    private boolean canEdit() {
        return preferences.hasPermission(PERMISSION_EDIT);
    }

    private boolean canExport() {
        return preferences.hasPermission(PERMISSION_EXPORT);
    }

    private ProductionDailyReportData.Section currentSection() {
        return report == null ? null : report.section(sectionCode);
    }

    private ProductionDailyReportData.Group currentGroup() {
        ProductionDailyReportData.Section section = currentSection();
        return section == null ? null : section.group(groupCode);
    }

    private void loadProducts() {
        api.loadProductionDailyProducts(new ApiClient.Callback<>() {
            @Override
            public void onSuccess(JSONArray value) {
                try {
                    productOptions = ProductionDailyReportData.parseProductOptions(value);
                } catch (JSONException exception) {
                    showMessage("产品选项暂时无法读取，请稍后重试", true);
                }
            }

            @Override
            public void onError(String message, int httpStatus) {
                handleApiError(message, httpStatus);
            }
        });
    }

    private void loadReport(String targetDate, boolean preserveLevel) {
        final int sequence = ++loadSequence;
        busy = true;
        render(false);
        api.loadProductionDailyReport(targetDate, new ApiClient.Callback<>() {
            @Override
            public void onSuccess(JSONObject data) {
                if (sequence != loadSequence || isFinishing()) return;
                try {
                    ProductionDailyReportData.Report server = ProductionDailyReportData.parseReport(data);
                    ProductionDailyReportData.Report local = drafts.load(preferences.userName(), targetDate);
                    serverCopy = ProductionDailyReportData.parseReport(data);
                    rememberHistory(server);
                    ProductionDailyReportData.Report importPreview =
                            drafts.loadImportPreview(preferences.userName(), targetDate);
                    if (importPreview != null && importPreview.importPending) {
                        report = importPreview;
                        checkImportPreviewConflict();
                    } else {
                        report = ProductionDailyReportData.mergeUnsaved(server, local);
                    }
                    date = targetDate;
                    calendarMonth = date.substring(0, 7);
                    showingServerCopy = false;
                    if (!preserveLevel) level = 0;
                    if (currentSection() == null) level = 0;
                    if (level == 2 && currentGroup() == null) level = 1;
                    busy = false;
                    hideMessage();
                    render(true);
                    if (report.importPending) {
                        int unmatched = unresolvedImportCount();
                        showMessage(unmatched == 0
                                ? "已恢复 Excel 导入预览。请核对内容后保存导入。"
                                : "已恢复 Excel 导入预览。还有 " + unmatched + " 条产品待匹配。",
                                unmatched > 0);
                    }
                    if (hasConflict()) showConflictDialog();
                } catch (JSONException exception) {
                    busy = false;
                    showMessage("日报内容格式不正确，请联系管理员", true);
                    render(false);
                }
            }

            @Override
            public void onError(String message, int httpStatus) {
                if (sequence != loadSequence || isFinishing()) return;
                busy = false;
                handleApiError(message, httpStatus);
                renderActions();
            }
        });
    }

    private void chooseDate(String targetDate) {
        if (busy) return;
        if (report != null && report.importPending) {
            showMessage("请先处理或取消当前 Excel 导入", true);
            return;
        }
        if (targetDate.equals(date)) return;
        Runnable change = () -> {
            persistDraft();
            loadReport(targetDate, false);
        };
        if (report != null && report.dirty()) {
            new AlertDialog.Builder(this)
                    .setTitle("有未保存的修改")
                    .setMessage("当前填写会暂存在本机，返回原日期后仍可继续编辑。")
                    .setNegativeButton("继续编辑", null)
                    .setPositiveButton("切换日期", (dialog, which) -> change.run())
                    .show();
        } else {
            change.run();
        }
    }

    private void scheduleDraft() {
        if (report == null || showingServerCopy) return;
        if (draftWrite != null) handler.removeCallbacks(draftWrite);
        draftWrite = this::persistDraft;
        handler.postDelayed(draftWrite, 350);
    }

    private void persistDraft() {
        if (draftWrite != null) handler.removeCallbacks(draftWrite);
        draftWrite = null;
        if (report == null || showingServerCopy) return;
        try {
            if (report.importPending) drafts.saveImportPreview(preferences.userName(), report);
            else if (report.dirty()) drafts.save(preferences.userName(), report);
            else drafts.remove(preferences.userName(), report.date);
        } catch (JSONException exception) {
            showMessage("本机暂存失败，请先保存日报", true);
        }
    }

    private void markDirty(boolean header) {
        if (report == null) return;
        if (header) report.headerDirty = true;
        else if (currentSection() != null) currentSection().dirty = true;
        if (liveStatusBadge != null) {
            liveStatusBadge.setText("未保存");
            liveStatusBadge.setTextColor(getColor(R.color.primary));
            liveStatusBadge.setBackground(shape(getColor(R.color.primary_soft), 0, 8));
        }
        scheduleDraft();
        renderActions();
    }

    private void applyServer(JSONObject data, boolean savedHeader, String savedSection) {
        try {
            ProductionDailyReportData.Report previous = report;
            if (previous != null) {
                if (savedHeader) previous.headerDirty = false;
                if (savedSection != null && previous.section(savedSection) != null) {
                    previous.section(savedSection).dirty = false;
                }
            }
            serverCopy = ProductionDailyReportData.parseReport(data);
            report = ProductionDailyReportData.mergeUnsaved(
                    ProductionDailyReportData.parseReport(data), previous);
            date = report.date;
            rememberHistory(serverCopy);
            showingServerCopy = false;
            persistDraft();
            render(false);
        } catch (JSONException exception) {
            showMessage("服务器已处理请求，但返回内容无法显示，请重新打开日报", true);
        }
    }

    private boolean hasConflict() {
        if (report == null) return false;
        if (report.headerConflict) return true;
        for (ProductionDailyReportData.Section section : report.sections) if (section.conflict) return true;
        return false;
    }

    private void rememberHistory(ProductionDailyReportData.Report server) {
        if (server == null || server.id == 0 || server.date.isEmpty()) return;
        ProductionDailyReportData.History history = new ProductionDailyReportData.History();
        history.date = server.date;
        history.status = server.status;
        history.preparedBy = server.preparedBy;
        history.updatedAt = server.updatedAt;
        calendarHistory.put(server.date, history);
    }

    private void handleApiError(String message, int httpStatus) {
        if (httpStatus == 401) {
            preferences.clearToken();
            returnToLogin();
            return;
        }
        busy = false;
        showMessage(message, true);
        render(false);
        if (httpStatus == 1032 || httpStatus == 409
                || message.contains("冲突") || message.contains("已更新")) {
            persistDraft();
            fetchServerForConflict();
        }
    }

    private void returnToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void showMessage(String message, boolean error) {
        if (messageView == null) return;
        messageView.setText(message);
        messageView.setTextColor(getColor(error ? R.color.danger : R.color.primary));
        messageView.setBackgroundResource(error ? R.drawable.bg_feedback_error : R.drawable.bg_feedback_info);
        messageView.setVisibility(View.VISIBLE);
    }

    private void hideMessage() {
        if (messageView != null) messageView.setVisibility(View.GONE);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(float size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private ImageView icon(int resource, int tint, int size) {
        ImageView view = new ImageView(this);
        view.setImageResource(resource);
        view.setImageTintList(ColorStateList.valueOf(tint));
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        return view;
    }

    private GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout card(int padding) {
        LinearLayout layout = column();
        layout.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        layout.setBackgroundResource(R.drawable.bg_card);
        layout.setElevation(dp(2));
        return layout;
    }

    private void append(LinearLayout parent, View child, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMargin);
        parent.addView(child, params);
    }

    private void divider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(getColor(R.color.line));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.topMargin = dp(10);
        params.bottomMargin = dp(10);
        parent.addView(line, params);
    }

    private TextView statusBadge(String status, boolean dirty) {
        String label = dirty ? "未保存" : "SUBMITTED".equals(status) ? "已提交"
                : "IMPORT_PREVIEW".equals(status) ? "待导入"
                : "EMPTY".equals(status) ? "待填写" : "草稿";
        int ink = dirty ? getColor(R.color.primary) : "SUBMITTED".equals(status)
                ? getColor(R.color.success) : "EMPTY".equals(status)
                ? getColor(R.color.text_muted) : getColor(R.color.warning);
        int fill = dirty ? getColor(R.color.primary_soft) : "SUBMITTED".equals(status)
                ? getColor(R.color.success_soft) : "EMPTY".equals(status)
                ? Color.rgb(240, 243, 248) : getColor(R.color.warning_soft);
        TextView view = text(label, 11, ink, true);
        view.setPadding(dp(8), dp(4), dp(8), dp(4));
        view.setBackground(shape(fill, 0, 8));
        return view;
    }

    private String sectionStatus(ProductionDailyReportData.Section section) {
        return section.version == 0 && section.updatedAt.isEmpty() ? "EMPTY" : section.status;
    }

    private void render(boolean resetScroll) {
        if (contentView == null) return;
        int oldScroll = resetScroll ? 0 : scrollView.getScrollY();
        contentView.removeAllViews();
        tabsView.removeAllViews();
        liveStatusBadge = null;
        if (report == null) {
            tabsView.setVisibility(View.GONE);
            renderActions();
            return;
        }
        if (level == 2 && currentGroup() == null) level = 1;
        if (level > 0 && currentSection() == null) level = 0;
        tabsView.setVisibility(level == 2 ? View.VISIBLE : View.GONE);
        String heading = level == 0 ? "生产日报" : level == 1 ? currentSection().name : currentGroup().name;
        titleView.setText(heading);
        subtitleView.setText(level == 0 ? "生产与仓库管理" : level == 1
                ? ProductionDailyDates.display(date)
                : currentSection().name + " · " + ProductionDailyDates.display(date));
        if (level == 0) renderOverview();
        else if (level == 1) renderSection();
        else renderGroup();
        renderActions();
        scrollView.post(() -> scrollView.scrollTo(0, oldScroll));
    }

    private void renderActions() {
        if (bottomBar == null) return;
        if (report == null) {
            bottomBar.setVisibility(View.GONE);
            return;
        }
        if (level > 0 && (!canEdit() || showingServerCopy) && !report.importPending) {
            bottomBar.setVisibility(View.GONE);
            return;
        }
        bottomBar.setVisibility(View.VISIBLE);
        if (report.importPending) {
            secondaryAction.setText("取消导入");
            secondaryAction.setOnClickListener(view -> cancelImport());
            secondaryAction.setVisibility(View.VISIBLE);
            primaryAction.setText("保存导入");
            primaryAction.setOnClickListener(view -> confirmImport());
            primaryAction.setVisibility(View.VISIBLE);
            primaryAction.setEnabled(!busy && !hasConflict() && unresolvedImportCount() == 0);
        } else if (level == 0) {
            secondaryAction.setText("日历与历史");
            secondaryAction.setOnClickListener(view -> showCalendar());
            secondaryAction.setVisibility(View.VISIBLE);
            primaryAction.setText("提交整张日报");
            primaryAction.setOnClickListener(view -> confirmSubmit(true));
            primaryAction.setVisibility(canEdit() ? View.VISIBLE : View.GONE);
            primaryAction.setEnabled(!busy && canEdit() && !hasConflict() && !showingServerCopy);
        } else {
            secondaryAction.setText("保存草稿");
            secondaryAction.setOnClickListener(view -> saveCurrentSection());
            primaryAction.setText("FACTORY_SUMMARY".equals(sectionCode) ? "提交汇总" : "提交本车间");
            primaryAction.setOnClickListener(view -> confirmSubmit(false));
            secondaryAction.setVisibility(canEdit() ? View.VISIBLE : View.GONE);
            primaryAction.setVisibility(canEdit() ? View.VISIBLE : View.GONE);
            primaryAction.setEnabled(!busy && canEdit() && !hasConflict() && !showingServerCopy);
        }
        secondaryAction.setEnabled(!busy && (level == 0 || !showingServerCopy));
    }

    private String overallStatus() {
        if (report.importPending) return "导入预览";
        if (report.dirty()) return "未保存";
        if (report.id == 0) return "待填写";
        return "SUBMITTED".equals(report.status) ? "已提交" : "草稿";
    }

    private void renderOverview() {
        renderDateCard();
        if (report.importPending) renderImportOverview();
        append(contentView, heading("制表信息", report.importPending ? "导入预览"
                : report.headerDirty ? "未保存"
                : canEdit() && !showingServerCopy ? "可修改" : "只读"), 18);
        LinearLayout headerCard = card(15);
        LinearLayout headerRow = row();
        LinearLayout headerText = column();
        headerText.addView(text("制表日期 · 制表人", 11, getColor(R.color.text_muted), true));
        append(headerText, text((report.preparedDate.isEmpty() ? "待填写" : report.preparedDate)
                + " · " + (report.preparedBy.isEmpty() ? "待填写" : report.preparedBy),
                14, getColor(R.color.text_primary), true), 4);
        headerRow.addView(headerText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (canEdit() && !showingServerCopy && !report.importPending) {
            headerRow.addView(icon(R.drawable.ic_daily_next, getColor(R.color.primary), 18));
        }
        headerCard.addView(headerRow);
        if (canEdit() && !showingServerCopy && !report.importPending) {
            headerCard.setOnClickListener(view -> editHeader());
        }
        append(contentView, headerCard, 9);
        append(contentView, heading("车间与汇总", "共 " + report.sections.size() + " 个分区"), 19);
        for (ProductionDailyReportData.Section section : report.sections) {
            append(contentView, sectionCard(section), 9);
        }
    }

    private void renderDateCard() {
        LinearLayout dateCard = column();
        dateCard.setPadding(dp(16), dp(15), dp(16), dp(12));
        dateCard.setBackgroundResource(R.drawable.bg_daily_date);
        dateCard.setElevation(dp(4));
        LinearLayout top = row();
        top.addView(text("日报日期", 12, Color.WHITE, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView status = text(overallStatus(), 11, Color.WHITE, true);
        status.setPadding(dp(9), dp(5), dp(9), dp(5));
        status.setBackground(shape(Color.argb(45, 255, 255, 255), 0, 8));
        top.addView(status);
        dateCard.addView(top);

        LinearLayout middle = row();
        middle.addView(text(String.valueOf(Integer.parseInt(date.substring(8))), 48, Color.WHITE, true));
        LinearLayout detail = column();
        Calendar parsed = ProductionDailyDates.parse(date);
        detail.addView(text(parsed.get(Calendar.YEAR) + "年" + (parsed.get(Calendar.MONTH) + 1) + "月",
                16, Color.WHITE, true));
        append(detail, text("星期" + ProductionDailyDates.weekday(date)
                + (date.equals(ProductionDailyDates.today()) ? " · 今天" : ""), 11, Color.WHITE, false), 4);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        detailParams.leftMargin = dp(11);
        middle.addView(detail, detailParams);
        LinearLayout calendarButton = column();
        calendarButton.setGravity(Gravity.CENTER);
        calendarButton.setPadding(dp(6), dp(5), dp(6), dp(5));
        calendarButton.setBackground(shape(Color.argb(40, 255, 255, 255), Color.argb(80, 255, 255, 255), 13));
        calendarButton.addView(icon(R.drawable.ic_daily_calendar, Color.WHITE, 21));
        append(calendarButton, text("日历", 11, Color.WHITE, true), 2);
        calendarButton.setMinimumWidth(dp(54));
        calendarButton.setMinimumHeight(dp(54));
        calendarButton.setContentDescription("打开日报日历");
        calendarButton.setOnClickListener(view -> showCalendar());
        middle.addView(calendarButton);
        append(dateCard, middle, 8);
        divider(dateCard);
        LinearLayout controls = row();
        TextView previous = dateControl("‹ 前一天");
        TextView today = dateControl("今天");
        TextView next = dateControl("后一天 ›");
        today.setBackground(shape(Color.argb(40, 255, 255, 255), 0, 9));
        controls.addView(previous, new LinearLayout.LayoutParams(0, dp(44), 1));
        controls.addView(today, new LinearLayout.LayoutParams(0, dp(44), 1));
        controls.addView(next, new LinearLayout.LayoutParams(0, dp(44), 1));
        previous.setOnClickListener(view -> chooseDate(ProductionDailyDates.shiftDay(date, -1)));
        today.setOnClickListener(view -> chooseDate(ProductionDailyDates.today()));
        next.setOnClickListener(view -> chooseDate(ProductionDailyDates.shiftDay(date, 1)));
        dateCard.addView(controls);
        append(contentView, dateCard, 0);
    }

    private TextView dateControl(String label) {
        TextView view = text(label, 11, Color.WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        return view;
    }

    private LinearLayout heading(String title, String detail) {
        LinearLayout row = row();
        row.addView(text(title, 17, getColor(R.color.text_primary), true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(text(detail, 11, getColor(R.color.text_muted), false));
        return row;
    }

    private LinearLayout sectionCard(ProductionDailyReportData.Section section) {
        LinearLayout card = card(14);
        LinearLayout line = row();
        int image = "BROWN_SUGAR".equals(section.code) ? R.drawable.ic_daily_flame
                : "MONOCRYSTAL".equals(section.code) ? R.drawable.ic_daily_crystal
                : "FACTORY_SUMMARY".equals(section.code) ? R.drawable.ic_daily_chart
                : R.drawable.ic_daily_factory;
        int tint = "BROWN_SUGAR".equals(section.code) ? Color.rgb(206, 118, 48)
                : "MONOCRYSTAL".equals(section.code) ? Color.rgb(118, 92, 218)
                : "FACTORY_SUMMARY".equals(section.code) ? Color.rgb(22, 143, 121)
                : getColor(R.color.primary);
        ImageView symbol = icon(image, tint, 41);
        symbol.setPadding(dp(9), dp(9), dp(9), dp(9));
        symbol.setBackground(shape("BROWN_SUGAR".equals(section.code) ? Color.rgb(255, 241, 229)
                : "MONOCRYSTAL".equals(section.code) ? Color.rgb(241, 236, 255)
                : "FACTORY_SUMMARY".equals(section.code) ? Color.rgb(232, 248, 244)
                : getColor(R.color.primary_soft), 0, 12));
        line.addView(symbol);
        LinearLayout copy = column();
        copy.addView(text(section.name, 15, getColor(R.color.text_primary), true));
        append(copy, text(report.importPending ? "Excel 预览，尚未保存"
                : section.updatedAt.isEmpty() ? "尚未填写"
                : MobileDisplay.fallback(section.updatedBy, "已更新") + " · "
                + MobileDisplay.dateTime(section.updatedAt),
                11, getColor(R.color.text_muted), false), 4);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        copyParams.leftMargin = dp(10);
        line.addView(copy, copyParams);
        line.addView(statusBadge(report.importPending ? "IMPORT_PREVIEW"
                : sectionStatus(section), section.dirty));
        line.addView(icon(R.drawable.ic_daily_next, getColor(R.color.primary), 17));
        card.addView(line);
        if ("SUBMITTED".equals(section.status) && section.missingCount() > 0) {
            divider(card);
            card.addView(text("⚠  " + section.missingCount() + " 项未填，可补录", 11,
                    Color.rgb(164, 85, 7), true));
        }
        card.setOnClickListener(view -> {
            sectionCode = section.code;
            level = 1;
            render(true);
        });
        return card;
    }

    private void renderSection() {
        ProductionDailyReportData.Section section = currentSection();
        LinearLayout hero = card(17);
        LinearLayout top = row();
        top.addView(text(section.name, 20, getColor(R.color.text_primary), true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(statusBadge(report.importPending ? "IMPORT_PREVIEW"
                : sectionStatus(section), section.dirty));
        hero.addView(top);
        append(hero, text(report.importPending ? "Excel 导入预览，尚未保存"
                : section.updatedAt.isEmpty() ? "尚未有保存记录"
                : "最后修改：" + MobileDisplay.fallback(section.updatedBy, "未知") + " · "
                + MobileDisplay.dateTime(section.updatedAt), 12,
                getColor(R.color.text_muted), false), 8);
        append(contentView, hero, 0);
        if (report.importPending && unresolvedImportCount(section) > 0) {
            append(contentView, importNotice("本分区还有 " + unresolvedImportCount(section)
                    + " 条产品待匹配。进入相应分组，选择产品后保存导入。"), 12);
        }
        if ("SUBMITTED".equals(section.status) && section.missingCount() > 0) {
            append(contentView, missingNotice(section.missingCount()), 12);
        }
        append(contentView, heading("填写分组", "依样表顺序"), 19);
        for (ProductionDailyReportData.Group group : section.groups) {
            LinearLayout groupCard = card(14);
            LinearLayout line = row();
            ImageView symbol = icon(group.productGroup ? R.drawable.ic_daily_box : R.drawable.ic_daily_grid,
                    getColor(R.color.primary), 38);
            symbol.setPadding(dp(9), dp(9), dp(9), dp(9));
            symbol.setBackground(shape(getColor(R.color.primary_soft), 0, 11));
            line.addView(symbol);
            LinearLayout info = column();
            info.addView(text(group.name, 15, getColor(R.color.text_primary), true));
            append(info, text(group.productGroup ? group.products.size() + " 条产品行"
                    : group.metrics.size() + " 项固定指标", 11, getColor(R.color.text_muted), false), 4);
            LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            infoParams.leftMargin = dp(9);
            line.addView(info, infoParams);
            if ("SUBMITTED".equals(section.status) && group.missingCount() > 0) {
                line.addView(text(group.missingCount() + " 项未填", 10, Color.rgb(164, 85, 7), true));
            }
            line.addView(icon(R.drawable.ic_daily_next, getColor(R.color.primary), 17));
            groupCard.addView(line);
            String preview = group.productGroup ? productPreview(group)
                    : metricPreview(group);
            append(groupCard, text(preview, 11, getColor(R.color.text_secondary), false), 8);
            groupCard.setOnClickListener(view -> {
                groupCode = group.code;
                field = 0;
                level = 2;
                render(true);
            });
            append(contentView, groupCard, 9);
        }
    }

    private String productPreview(ProductionDailyReportData.Group group) {
        if (group.products.isEmpty()) return "暂无产品行";
        StringBuilder names = new StringBuilder();
        for (int index = 0; index < Math.min(3, group.products.size()); index++) {
            if (index > 0) names.append("、");
            names.append(group.products.get(index).name);
        }
        return names.toString();
    }

    private String metricPreview(ProductionDailyReportData.Group group) {
        StringBuilder names = new StringBuilder();
        for (int index = 0; index < Math.min(3, group.metrics.size()); index++) {
            if (index > 0) names.append("、");
            names.append(group.metrics.get(index).name);
        }
        return names.toString();
    }

    private TextView missingNotice(int count) {
        TextView notice = text("ⓘ  已提交，仍有 " + count + " 项未填\n"
                + "这些项目可以继续补录，不影响已提交记录。", 12,
                Color.rgb(155, 77, 7), true);
        notice.setLineSpacing(dp(4), 1);
        notice.setPadding(dp(12), dp(12), dp(12), dp(12));
        notice.setBackgroundResource(R.drawable.bg_daily_missing);
        return notice;
    }

    private void renderGroup() {
        ProductionDailyReportData.Section section = currentSection();
        ProductionDailyReportData.Group group = currentGroup();
        LinearLayout context = row();
        context.addView(text(section.name + " / " + group.name, 12,
                getColor(R.color.text_secondary), false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        liveStatusBadge = statusBadge(report.importPending ? "IMPORT_PREVIEW"
                : sectionStatus(section), section.dirty);
        context.addView(liveStatusBadge);
        append(contentView, context, 0);
        if ("SUBMITTED".equals(section.status) && group.missingCount() > 0) {
            append(contentView, missingNotice(group.missingCount()), 12);
        }
        if (report.importPending) renderUnmatchedGroup(group);
        renderFieldTabs();
        append(contentView, heading(group.productGroup ? "指标与产品" : "固定指标", "点右侧查看全部字段"), 16);
        LinearLayout list = card(0);
        list.setClipToOutline(true);
        List<ProductionDailyReportData.Row> rows = group.allRows();
        for (int index = 0; index < rows.size(); index++) {
            if (index > 0) {
                View line = new View(this);
                line.setBackgroundColor(getColor(R.color.line));
                list.addView(line, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            }
            list.addView(metricRow(rows.get(index)));
        }
        append(contentView, list, 9);
        if (group.productGroup && canEdit() && !busy && !showingServerCopy) {
            TextView addProduct = text("＋  新增产品", 13, getColor(R.color.primary), true);
            addProduct.setGravity(Gravity.CENTER);
            addProduct.setMinHeight(dp(50));
            addProduct.setBackground(shape(Color.rgb(245, 249, 255), Color.rgb(164, 192, 251), 13));
            addProduct.setOnClickListener(view -> chooseProduct(group, null));
            append(contentView, addProduct, 10);
        }
    }

    private void renderFieldTabs() {
        LinearLayout titleRow = row();
        titleRow.addView(text("连续录入", 14, getColor(R.color.text_primary), true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        titleRow.addView(text("左右滑动，选择填写列", 10,
                getColor(R.color.text_muted), false));
        tabsView.addView(titleRow);
        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        LinearLayout pills = row();
        for (int index = 0; index < ProductionDailyReportData.FIELD_LABELS.length; index++) {
            final int selected = index;
            TextView tab = text(ProductionDailyReportData.FIELD_LABELS[index], 12,
                    index == field ? Color.WHITE : getColor(R.color.text_secondary), true);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(13), 0, dp(13), 0);
            tab.setMinHeight(dp(42));
            tab.setBackgroundResource(index == field ? R.drawable.bg_daily_field_selected
                    : R.drawable.bg_daily_field);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
            params.rightMargin = dp(7);
            pills.addView(tab, params);
            tab.setOnClickListener(view -> {
                field = selected;
                render(false);
            });
        }
        strip.addView(pills);
        append(tabsView, strip, 8);
        strip.post(() -> {
            if (pills.getChildCount() <= field) return;
            View selected = pills.getChildAt(field);
            strip.smoothScrollTo(Math.max(0, selected.getLeft() - strip.getWidth() / 2
                    + selected.getWidth() / 2), 0);
        });
    }

    private LinearLayout metricRow(ProductionDailyReportData.Row item) {
        final int displayedField = field;
        LinearLayout line = row();
        line.setPadding(dp(14), dp(6), dp(7), dp(6));
        line.setMinimumHeight(dp(72));
        boolean missing = "SUBMITTED".equals(currentSection().status) && item.empty();
        if (missing) line.setBackgroundColor(Color.rgb(255, 250, 243));
        LinearLayout copy = column();
        copy.addView(text(item.name.isEmpty() ? "请选择产品" : item.name, 13,
                getColor(R.color.text_primary), true));
        TextView detail = text(rowDetail(item, missing), 11,
                missing || !item.importError.isEmpty() ? Color.rgb(164, 85, 7)
                        : getColor(R.color.text_muted), false);
        append(copy, detail, 4);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        copyParams.rightMargin = dp(5);
        line.addView(copy, copyParams);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(14);
        input.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        input.setTextColor(getColor(R.color.text_primary));
        input.setHintTextColor(missing ? Color.rgb(164, 85, 7) : Color.rgb(129, 145, 168));
        input.setHint(missing ? "可补录" : displayedField == 5 ? "填写备注" : "点击填写");
        input.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        input.setBackgroundResource(missing ? R.drawable.bg_daily_missing : R.drawable.bg_input);
        input.setPadding(dp(8), 0, dp(8), 0);
        input.setInputType(displayedField == 5 ? InputType.TYPE_CLASS_TEXT
                : InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        if (displayedField == 5) input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});
        input.setText(item.values[displayedField]);
        input.setEnabled(canEdit() && !busy && !showingServerCopy);
        input.setContentDescription(item.name + "，" + ProductionDailyReportData.FIELD_LABELS[displayedField]);
        line.addView(input, new LinearLayout.LayoutParams(dp(112), dp(48)));
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                item.values[displayedField] = s.toString();
                markDirty(false);
                boolean nowMissing = "SUBMITTED".equals(currentSection().status) && item.empty();
                detail.setText(rowDetail(item, nowMissing));
                detail.setTextColor(nowMissing ? Color.rgb(164, 85, 7)
                        : getColor(R.color.text_muted));
                line.setBackgroundColor(nowMissing ? Color.rgb(255, 250, 243) : Color.TRANSPARENT);
                input.setHint(nowMissing ? "可补录" : displayedField == 5 ? "填写备注" : "点击填写");
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        ImageView arrow = icon(R.drawable.ic_daily_next, getColor(R.color.primary), 42);
        arrow.setPadding(dp(12), dp(12), dp(12), dp(12));
        arrow.setMinimumHeight(dp(48));
        arrow.setContentDescription("查看" + item.name + "全部字段");
        arrow.setOnClickListener(view -> openItem(item));
        line.addView(arrow);
        return line;
    }

    private String rowDetail(ProductionDailyReportData.Row item, boolean missing) {
        StringBuilder detail = new StringBuilder("单位：").append(item.unit);
        if (!item.importError.isEmpty()) detail.append(" · ").append(item.importError);
        else if (missing) detail.append(" · 未填");
        else if (item.otherFieldsFilled(field)) detail.append(" · 其他字段已填");
        return detail.toString();
    }

    private void saveCurrentSection() {
        if (busy || !canEdit() || report == null || report.importPending || showingServerCopy) return;
        if (hasConflict()) { showConflictDialog(); return; }
        String currentCode = sectionCode;
        ProductionDailyReportData.Section section = currentSection();
        if (section == null) return;
        String error = ProductionDailyReportData.validateSection(section);
        if (error != null) { showMessage(error, true); return; }
        busy = true;
        render(false);
        saveHeaderIfDirty(() -> saveSectionIfDirty(currentCode, () -> {
            busy = false;
            hideMessage();
            render(false);
            toast("当前分区已保存为草稿");
        }));
    }

    private void saveHeaderIfDirty(Runnable after) {
        if (!report.headerDirty) { after.run(); return; }
        if (report.preparedBy.length() > 100) {
            busy = false;
            showMessage("制表人不能超过 100 字", true);
            render(false);
            return;
        }
        try {
            api.saveProductionDailyHeader(date, ProductionDailyReportData.headerPayload(report),
                    new ApiClient.Callback<>() {
                        @Override public void onSuccess(JSONObject value) {
                            applyServer(value, true, null);
                            after.run();
                        }
                        @Override public void onError(String message, int httpStatus) {
                            handleApiError(message, httpStatus);
                            renderActions();
                        }
                    });
        } catch (JSONException exception) {
            busy = false;
            showMessage("制表信息无法保存，请检查填写内容", true);
            render(false);
        }
    }

    private void saveSectionIfDirty(String code, Runnable after) {
        ProductionDailyReportData.Section section = report.section(code);
        if (section == null || !section.dirty) { after.run(); return; }
        String error = ProductionDailyReportData.validateSection(section);
        if (error != null) {
            busy = false;
            showMessage(error, true);
            render(false);
            return;
        }
        try {
            api.saveProductionDailySection(date, code, ProductionDailyReportData.sectionPayload(section),
                    new ApiClient.Callback<>() {
                        @Override public void onSuccess(JSONObject value) {
                            applyServer(value, false, code);
                            after.run();
                        }
                        @Override public void onError(String message, int httpStatus) {
                            handleApiError(message, httpStatus);
                            renderActions();
                        }
                    });
        } catch (JSONException | NumberFormatException exception) {
            busy = false;
            showMessage("分区内容无法保存，请检查数字格式", true);
            render(false);
        }
    }

    private void confirmSubmit(boolean whole) {
        if (busy || !canEdit() || report == null || report.importPending || showingServerCopy) return;
        if (hasConflict()) { showConflictDialog(); return; }
        new AlertDialog.Builder(this)
                .setTitle(whole ? "提交整张日报？" : "提交当前分区？")
                .setMessage("将先保存本机尚未保存的填写内容。提交后仍可修改；完全空白的项目会显示补录提醒。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认提交", (dialog, which) -> {
                    busy = true;
                    render(false);
                    if (whole) {
                        saveHeaderIfDirty(() -> saveAllDirtySections(0, this::submitWholeNow));
                    } else {
                        String code = sectionCode;
                        saveHeaderIfDirty(() -> saveSectionIfDirty(code, () -> submitSectionNow(code)));
                    }
                }).show();
    }

    private void saveAllDirtySections(int index, Runnable after) {
        if (index >= report.sections.size()) { after.run(); return; }
        String code = report.sections.get(index).code;
        saveSectionIfDirty(code, () -> saveAllDirtySections(index + 1, after));
    }

    private void submitSectionNow(String code) {
        api.submitProductionDailySection(date, code, new ApiClient.Callback<>() {
            @Override public void onSuccess(JSONObject value) {
                busy = false;
                applyServer(value, false, null);
                hideMessage();
                toast("分区已提交");
            }
            @Override public void onError(String message, int httpStatus) {
                handleApiError(message, httpStatus);
                renderActions();
            }
        });
    }

    private void submitWholeNow() {
        api.submitProductionDailyReport(date, new ApiClient.Callback<>() {
            @Override public void onSuccess(JSONObject value) {
                busy = false;
                applyServer(value, false, null);
                hideMessage();
                toast("整张日报已提交");
            }
            @Override public void onError(String message, int httpStatus) {
                handleApiError(message, httpStatus);
                renderActions();
            }
        });
    }

    private int unresolvedImportCount() {
        if (report == null) return 0;
        int count = 0;
        for (ProductionDailyReportData.Section section : report.sections) {
            count += unresolvedImportCount(section);
        }
        return count;
    }

    private int unresolvedImportCount(ProductionDailyReportData.Section section) {
        int count = 0;
        for (ProductionDailyReportData.Group group : section.groups) {
            for (ProductionDailyReportData.Row row : group.products) {
                if (row.hasContent() && row.productId == null) count++;
            }
        }
        return count;
    }

    private TextView importNotice(String message) {
        TextView notice = text(message, 12, Color.rgb(155, 77, 7), true);
        notice.setPadding(dp(12), dp(12), dp(12), dp(12));
        notice.setBackgroundResource(R.drawable.bg_daily_missing);
        return notice;
    }

    private void renderImportOverview() {
        int unresolved = unresolvedImportCount();
        append(contentView, importNotice(unresolved == 0
                ? "Excel 产品已匹配完成。核对日报内容后，点底部“保存导入”。"
                : "Excel 导入预览 · 还有 " + unresolved + " 条产品待匹配。"), 12);
        for (ProductionDailyReportData.Section section : report.sections) {
            for (ProductionDailyReportData.Group group : section.groups) {
                for (ProductionDailyReportData.Row row : group.products) {
                    if (!row.hasContent() || row.productId != null) continue;
                    LinearLayout link = row();
                    link.setPadding(dp(12), dp(9), dp(12), dp(9));
                    link.setBackgroundResource(R.drawable.bg_card);
                    link.addView(text(section.name + " · " + group.name + "  /  " + row.name,
                            12, getColor(R.color.text_primary), true),
                            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    link.addView(icon(R.drawable.ic_daily_next, getColor(R.color.primary), 18));
                    link.setOnClickListener(view -> {
                        sectionCode = section.code;
                        groupCode = group.code;
                        level = 2;
                        field = 0;
                        render(true);
                    });
                    append(contentView, link, 6);
                }
            }
        }
    }

    private void renderUnmatchedGroup(ProductionDailyReportData.Group group) {
        int unresolved = 0;
        for (ProductionDailyReportData.Row row : group.products) {
            if (!row.hasContent() || row.productId != null) continue;
            if (unresolved++ == 0) {
                append(contentView, importNotice("请先为以下产品选择系统中的产品资料："), 12);
            }
            TextView pick = text(row.name + "  ›  选择产品", 12, getColor(R.color.primary), true);
            pick.setPadding(dp(12), dp(9), dp(12), dp(9));
            pick.setBackgroundResource(R.drawable.bg_card);
            pick.setOnClickListener(view -> chooseProduct(group, row));
            append(contentView, pick, 6);
        }
    }

    private void refreshImportMessage() {
        if (report == null || !report.importPending) return;
        int unresolved = unresolvedImportCount();
        if (unresolved == 0) {
            hideMessage();
            toast("产品匹配完成，可保存导入");
        } else {
            showMessage("还有 " + unresolved + " 条产品待匹配。请选择产品后再保存导入。", true);
        }
    }

    private void confirmImport() {
        if (busy || report == null || !report.importPending || unresolvedImportCount() > 0) return;
        if (hasConflict()) { showConflictDialog(); return; }
        for (ProductionDailyReportData.Section section : report.sections) {
            String error = ProductionDailyReportData.validateSection(section);
            if (error != null) { showMessage(error, true); return; }
        }
        try {
            JSONObject body = ProductionDailyReportData.importConfirmPayload(report);
            busy = true;
            render(false);
            api.confirmProductionDailyImport(body, new ApiClient.Callback<>() {
                @Override public void onSuccess(JSONObject value) {
                    drafts.removeImportPreview(preferences.userName(), date);
                    report.importPending = false;
                    report.headerDirty = false;
                    for (ProductionDailyReportData.Section section : report.sections) section.dirty = false;
                    busy = false;
                    applyServer(value, false, null);
                    level = 0;
                    render(true);
                    hideMessage();
                    toast("导入已保存");
                }
                @Override public void onError(String message, int httpStatus) {
                    handleApiError(message, httpStatus);
                    renderActions();
                }
            });
        } catch (JSONException | NumberFormatException exception) {
            showMessage("导入内容无法保存，请检查产品与数字格式", true);
        }
    }

    private void cancelImport() {
        if (busy || report == null || !report.importPending) return;
        new AlertDialog.Builder(this)
                .setTitle("取消本次导入？")
                .setMessage("尚未保存的 Excel 预览内容将被清除。")
                .setNegativeButton("继续匹配", null)
                .setPositiveButton("取消导入", (dialog, which) -> {
                    String reloadDate = date;
                    drafts.removeImportPreview(preferences.userName(), date);
                    report = null;
                    loadReport(reloadDate, false);
                }).show();
    }

    private void fetchServerForConflict() {
        api.loadProductionDailyReport(date, new ApiClient.Callback<>() {
            @Override public void onSuccess(JSONObject value) {
                try {
                    serverCopy = ProductionDailyReportData.parseReport(value);
                    if (report != null) {
                        if (report.importPending) checkImportPreviewConflict();
                        else {
                            report.headerConflict = report.headerDirty
                                    && report.version != serverCopy.version;
                            for (ProductionDailyReportData.Section section : report.sections) {
                                ProductionDailyReportData.Section latest = serverCopy.section(section.code);
                                section.conflict = section.dirty && latest != null
                                        && section.version != latest.version;
                            }
                        }
                    }
                    render(false);
                    showConflictDialog();
                } catch (JSONException exception) {
                    showMessage("无法读取服务器最新日报，本机填写已保留", true);
                }
            }
            @Override public void onError(String message, int httpStatus) {
                showMessage("服务器最新内容暂时无法读取，本机填写已保留：" + message, true);
            }
        });
    }

    private void checkImportPreviewConflict() {
        if (report == null || serverCopy == null || !report.importPending) return;
        report.headerConflict = report.version != serverCopy.version;
        for (ProductionDailyReportData.Section section : report.sections) {
            ProductionDailyReportData.Section latest = serverCopy.section(section.code);
            section.conflict = latest == null || section.version != latest.version;
        }
    }

    private void showConflictDialog() {
        if (serverCopy == null || report == null || isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("日报已由他人更新")
                .setMessage("本机未保存的填写内容仍保留。请先查看服务器最新内容，再决定如何处理。")
                .setNegativeButton("保留本机草稿", null)
                .setPositiveButton("查看服务器版本", (dialog, which) -> viewServerCopy())
                .show();
    }

    private void viewServerCopy() {
        if (serverCopy == null) return;
        persistDraft();
        report = serverCopy;
        showingServerCopy = true;
        showMessage("正在查看服务器最新内容。本机草稿仍在，可从右上角恢复。", false);
        render(true);
    }

    private void restoreLocalDraft(boolean adoptLatestVersion) {
        if (serverCopy == null) return;
        try {
            ProductionDailyReportData.Report local =
                    drafts.loadImportPreview(preferences.userName(), date);
            if (local == null) local = drafts.load(preferences.userName(), date);
            if (local == null) {
                toast("没有可恢复的本机草稿");
                return;
            }
            if (adoptLatestVersion) {
                if (local.headerDirty) local.version = serverCopy.version;
                for (ProductionDailyReportData.Section section : local.sections) {
                    ProductionDailyReportData.Section latest = serverCopy.section(section.code);
                    if (section.dirty && latest != null) section.version = latest.version;
                }
            }
            if (local.importPending) {
                report = local;
                checkImportPreviewConflict();
            } else {
                report = ProductionDailyReportData.mergeUnsaved(
                        ProductionDailyReportData.parseReport(
                                ProductionDailyReportData.localSnapshot(serverCopy)), local);
            }
            showingServerCopy = false;
            if (adoptLatestVersion) {
                report.headerConflict = false;
                for (ProductionDailyReportData.Section section : report.sections) section.conflict = false;
                scheduleDraft();
            }
            hideMessage();
            render(true);
            if (report.importPending) refreshImportMessage();
        } catch (JSONException exception) {
            showMessage("本机草稿无法恢复，请联系管理员", true);
        }
    }

    private void confirmAdoptLocalDraft() {
        if (serverCopy == null) return;
        new AlertDialog.Builder(this)
                .setTitle("使用本机填写继续？")
                .setMessage("保存时会用本机填写覆盖服务器上这些已更新分区的内容。请确认已经核对过服务器版本。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认使用本机填写", (dialog, which) -> restoreLocalDraft(true))
                .show();
    }

    private void confirmDiscardLocalDraft() {
        if (serverCopy == null) return;
        boolean importPreview = drafts.hasImportPreview(preferences.userName(), date);
        new AlertDialog.Builder(this)
                .setTitle(importPreview ? "舍弃导入预览？" : "舍弃本机草稿？")
                .setMessage(importPreview
                        ? "本次 Excel 导入预览将被清除。导入前的普通日报草稿将重新载入。"
                        : "本机尚未保存的填写内容将被清除，随后可以编辑服务器最新版本。")
                .setNegativeButton("取消", null)
                .setPositiveButton("舍弃并继续", (dialog, which) -> {
                    if (importPreview) {
                        drafts.removeImportPreview(preferences.userName(), date);
                        report = null;
                        showingServerCopy = false;
                        loadReport(date, false);
                        return;
                    } else {
                        drafts.remove(preferences.userName(), date);
                    }
                    report = serverCopy;
                    showingServerCopy = false;
                    hideMessage();
                    render(true);
                }).show();
    }

    private Dialog bottomSheet(String title, String subtitle, LinearLayout body,
                               View footer, boolean tall) {
        Dialog dialog = new Dialog(this);
        LinearLayout root = column();
        root.setBackgroundResource(R.drawable.bg_daily_sheet);
        LinearLayout header = row();
        header.setPadding(dp(16), dp(12), dp(16), dp(11));
        LinearLayout heading = column();
        heading.addView(text(title, 18, getColor(R.color.text_primary), true));
        if (subtitle != null && !subtitle.isEmpty()) {
            append(heading, text(subtitle, 11, getColor(R.color.text_muted), false), 3);
        }
        header.addView(heading, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView close = text("×", 25, getColor(R.color.text_secondary), false);
        close.setGravity(Gravity.CENTER);
        close.setBackground(shape(Color.rgb(243, 246, 250), 0, 12));
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        close.setContentDescription("关闭");
        close.setOnClickListener(view -> dialog.dismiss());
        root.addView(header);
        View line = new View(this);
        line.setBackgroundColor(getColor(R.color.line));
        root.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        body.setPadding(dp(16), dp(14), dp(16), dp(18));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                tall ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT, tall ? 1 : 0));
        if (footer != null) {
            LinearLayout footerWrap = column();
            footerWrap.setPadding(dp(16), dp(10), dp(16), dp(14));
            View border = new View(this);
            border.setBackgroundColor(getColor(R.color.line));
            footerWrap.addView(border, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            append(footerWrap, footer, 9);
            root.addView(footerWrap);
        }
        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            int height = Math.round(getResources().getDisplayMetrics().heightPixels * .9f);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    tall ? height : ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        currentDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (currentDialog == dialog) currentDialog = null;
            if (!isFinishing()) render(false);
        });
        return dialog;
    }

    private Button actionButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(primary ? Color.WHITE : getColor(R.color.primary));
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setStateListAnimator(null);
        button.setBackgroundResource(primary ? R.drawable.bg_primary : R.drawable.bg_feedback_info);
        button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        return button;
    }

    private TextView detailLabel(String label) {
        return text(label, 12, getColor(R.color.text_secondary), true);
    }

    private EditText detailInput(String value, boolean numeric, boolean multiline) {
        EditText input = new EditText(this);
        input.setTextSize(15);
        input.setTextColor(getColor(R.color.text_primary));
        input.setHintTextColor(Color.rgb(129, 145, 168));
        input.setHint("可留空");
        input.setBackgroundResource(R.drawable.bg_input);
        input.setPadding(dp(12), dp(9), dp(12), dp(9));
        input.setInputType(multiline ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                : numeric ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED : InputType.TYPE_CLASS_TEXT);
        input.setGravity(multiline ? Gravity.TOP | Gravity.START
                : numeric ? Gravity.CENTER_VERTICAL | Gravity.RIGHT : Gravity.CENTER_VERTICAL | Gravity.START);
        input.setMinHeight(dp(multiline ? 90 : 48));
        if (!multiline) input.setSingleLine(true);
        input.setText(value == null ? "" : value);
        input.setEnabled(canEdit() && !busy && !showingServerCopy);
        return input;
    }

    private void editHeader() {
        if (!canEdit() || busy || report == null || report.importPending || showingServerCopy) return;
        LinearLayout body = column();
        body.addView(detailLabel("制表日期"));
        LinearLayout dateRow = row();
        TextView dateValue = text(report.preparedDate.isEmpty() ? "选择日期（可留空）"
                : report.preparedDate, 14, getColor(R.color.text_primary), false);
        dateValue.setPadding(dp(12), 0, dp(12), 0);
        dateValue.setMinHeight(dp(48));
        dateValue.setBackgroundResource(R.drawable.bg_input);
        dateRow.addView(dateValue, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView clear = text("清除", 12, getColor(R.color.primary), true);
        clear.setGravity(Gravity.CENTER);
        dateRow.addView(clear, new LinearLayout.LayoutParams(dp(52), dp(48)));
        clear.setOnClickListener(view -> {
            report.preparedDate = "";
            dateValue.setText("选择日期（可留空）");
            markDirty(true);
        });
        dateValue.setOnClickListener(view -> {
            Calendar chosen = ProductionDailyDates.parse(report.preparedDate.isEmpty()
                    ? date : report.preparedDate);
            new DatePickerDialog(this, (picker, year, month, day) -> {
                report.preparedDate = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day);
                dateValue.setText(report.preparedDate);
                markDirty(true);
            }, chosen.get(Calendar.YEAR), chosen.get(Calendar.MONTH),
                    chosen.get(Calendar.DAY_OF_MONTH)).show();
        });
        append(body, dateRow, 7);
        append(body, detailLabel("制表人"), 16);
        EditText name = detailInput(report.preparedBy, false, false);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});
        append(body, name, 7);
        name.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                report.preparedBy = s.toString();
                markDirty(true);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        Button save = actionButton("保存制表信息", true);
        Dialog dialog = bottomSheet("制表信息", ProductionDailyDates.display(date), body, save, false);
        save.setOnClickListener(view -> {
            if (busy) return;
            busy = true;
            dialog.dismiss();
            render(false);
            saveHeaderIfDirty(() -> {
                busy = false;
                hideMessage();
                render(false);
                toast("制表信息已保存");
            });
        });
    }

    private void openItem(ProductionDailyReportData.Row item) {
        if (report == null || currentGroup() == null) return;
        LinearLayout body = column();
        if (item.product) {
            TextView choose = text(item.name.isEmpty() ? "请选择产品" : item.name,
                    14, getColor(R.color.text_primary), true);
            choose.setPadding(dp(12), 0, dp(12), 0);
            choose.setMinHeight(dp(52));
            choose.setBackgroundResource(R.drawable.bg_input);
            choose.setOnClickListener(view -> {
                if (!canEdit() || showingServerCopy) return;
                if (currentDialog != null) currentDialog.dismiss();
                chooseProduct(currentGroup(), item);
            });
            body.addView(detailLabel("产品"));
            append(body, choose, 7);
        }
        for (int index = 0; index < ProductionDailyReportData.FIELD_LABELS.length; index++) {
            final int selectedField = index;
            String label = ProductionDailyReportData.FIELD_LABELS[index]
                    + (index == 0 ? "  ·  单位：" + item.unit : "");
            append(body, detailLabel(label), index == 0 && !item.product ? 0 : 14);
            EditText input = detailInput(item.values[index], index < 5, index == 5);
            if (index == 5) input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});
            append(body, input, 6);
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    item.values[selectedField] = s.toString();
                    markDirty(false);
                }
                @Override public void afterTextChanged(Editable s) { }
            });
        }
        if (item.product && canEdit() && !showingServerCopy) {
            LinearLayout tools = row();
            TextView up = smallTool("上移", getColor(R.color.text_secondary));
            TextView down = smallTool("下移", getColor(R.color.text_secondary));
            TextView delete = smallTool("删除", getColor(R.color.danger));
            tools.addView(up, new LinearLayout.LayoutParams(0, dp(48), 1));
            tools.addView(down, new LinearLayout.LayoutParams(0, dp(48), 1));
            tools.addView(delete, new LinearLayout.LayoutParams(0, dp(48), 1));
            append(body, tools, 18);
            up.setOnClickListener(view -> moveProduct(item, -1));
            down.setOnClickListener(view -> moveProduct(item, 1));
            delete.setOnClickListener(view -> removeProduct(item));
        }
        Button complete = actionButton("完成", true);
        Dialog dialog = bottomSheet(item.product ? "产品行" : item.name,
                currentSection().name + " · " + currentGroup().name + " · 单位：" + item.unit,
                body, complete, true);
        complete.setOnClickListener(view -> dialog.dismiss());
    }

    private TextView smallTool(String label, int color) {
        TextView tool = text(label, 12, color, true);
        tool.setGravity(Gravity.CENTER);
        tool.setBackground(shape(Color.WHITE, getColor(R.color.line), 10));
        return tool;
    }

    private void moveProduct(ProductionDailyReportData.Row item, int direction) {
        ProductionDailyReportData.Group group = currentGroup();
        int before = group.products.indexOf(item);
        group.moveProduct(item, direction);
        if (before == group.products.indexOf(item)) {
            toast(direction < 0 ? "已经是第一条" : "已经是最后一条");
            return;
        }
        markDirty(false);
        if (currentDialog != null) currentDialog.dismiss();
        toast("产品顺序已调整");
    }

    private void removeProduct(ProductionDailyReportData.Row item) {
        new AlertDialog.Builder(this)
                .setTitle("移除这条产品行？")
                .setMessage("只会从当前日报移除，不会删除产品资料。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认移除", (dialog, which) -> {
                    currentGroup().products.remove(item);
                    markDirty(false);
                    if (currentDialog != null) currentDialog.dismiss();
                    toast("产品行已移除");
                }).show();
    }

    private void chooseProduct(ProductionDailyReportData.Group group,
                               ProductionDailyReportData.Row existing) {
        if (busy || !canEdit() || showingServerCopy) return;
        if (productOptions.isEmpty()) {
            showMessage("正在读取产品列表，请稍后重试", false);
            loadProducts();
            return;
        }
        LinearLayout body = column();
        EditText search = detailInput("", false, false);
        search.setHint("搜索产品名称");
        body.addView(search);
        LinearLayout list = column();
        append(body, list, 9);
        Dialog dialog = bottomSheet("选择产品", currentSection().name + " · " + group.name,
                body, null, true);
        Runnable populate = () -> {
            list.removeAllViews();
            String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            int count = 0;
            for (ProductionDailyReportData.ProductOption option : productOptions) {
                if (!group.allowedProductStatuses.contains(option.status)
                        || !option.name.toLowerCase(Locale.ROOT).contains(query)) continue;
                if (count++ >= 80) break;
                LinearLayout optionRow = row();
                optionRow.setMinimumHeight(dp(54));
                optionRow.addView(text(option.name, 14, getColor(R.color.text_primary), true),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                optionRow.addView(text(option.status, 11, getColor(R.color.text_muted), false));
                optionRow.setOnClickListener(view -> {
                    ProductionDailyReportData.Row selected = existing;
                    if (selected == null) {
                        selected = new ProductionDailyReportData.Row();
                        selected.product = true;
                        selected.categoryCode = group.code;
                        selected.unit = ProductionDailyReportData.PRODUCT_UNIT;
                        group.products.add(selected);
                    }
                    selected.productId = option.id;
                    selected.name = option.name;
                    selected.productStatus = option.status;
                    selected.importedName = "";
                    selected.importError = "";
                    markDirty(false);
                    refreshImportMessage();
                    dialog.dismiss();
                    render(false);
                    openItem(selected);
                });
                list.addView(optionRow);
                divider(list);
            }
            if (count == 0) list.addView(text("没有匹配的产品", 13,
                    getColor(R.color.text_muted), false));
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { populate.run(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        populate.run();
    }

    private void showMoreMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "日历与历史");
        if (canEdit()) popup.getMenu().add(0, 2, 1, "导入 Excel");
        if (canExport()) popup.getMenu().add(0, 3, 2, "导出 Excel");
        if (showingServerCopy) {
            popup.getMenu().add(0, 4, 3, "恢复本机草稿");
            popup.getMenu().add(0, 5, 4, "舍弃本机草稿，编辑服务器版本");
            popup.getMenu().add(0, 6, 5, "核对后使用本机填写");
        } else if (hasConflict()) {
            popup.getMenu().add(0, 7, 3, "查看服务器版本");
            popup.getMenu().add(0, 6, 4, "核对后使用本机填写");
        }
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1 -> showCalendar();
                case 2 -> chooseImportFile();
                case 3 -> exportExcel();
                case 4 -> restoreLocalDraft(false);
                case 5 -> confirmDiscardLocalDraft();
                case 6 -> confirmAdoptLocalDraft();
                case 7 -> viewServerCopy();
                default -> { return false; }
            }
            return true;
        });
        popup.show();
    }

    private void showCalendar() {
        if (report == null || busy) return;
        calendarMonth = date.substring(0, 7);
        LinearLayout body = column();
        Dialog dialog = bottomSheet("日报日历", "选择日期 · 查看历史日报", body, null, true);
        fillCalendar(body, dialog);
        loadCalendarHistory(body, dialog);
    }

    private void loadCalendarHistory(LinearLayout body, Dialog dialog) {
        final int requestSequence = ++calendarSequence;
        final String month = calendarMonth;
        String first = month + "-01";
        String last = month + "-" + String.format(Locale.ROOT, "%02d",
                ProductionDailyDates.daysInMonth(month));
        String start = ProductionDailyDates.shiftDay(first, -14);
        String end = ProductionDailyDates.shiftDay(last, 14);
        api.loadProductionDailyHistory(start, end, new ApiClient.Callback<>() {
            @Override public void onSuccess(JSONObject value) {
                if (!dialog.isShowing() || requestSequence != calendarSequence) return;
                try {
                    for (ProductionDailyReportData.History history
                            : ProductionDailyReportData.parseHistory(value)) {
                        calendarHistory.put(history.date, history);
                    }
                    fillCalendar(body, dialog);
                } catch (JSONException exception) {
                    showMessage("历史日报格式不正确", true);
                }
            }
            @Override public void onError(String message, int httpStatus) {
                if (!dialog.isShowing() || requestSequence != calendarSequence) return;
                handleApiError(message, httpStatus);
            }
        });
    }

    private void fillCalendar(LinearLayout body, Dialog dialog) {
        body.removeAllViews();
        String month = calendarMonth;
        LinearLayout monthRow = row();
        TextView previous = calendarNav("‹", "上个月");
        TextView next = calendarNav("›", "下个月");
        monthRow.addView(previous, new LinearLayout.LayoutParams(dp(46), dp(46)));
        String title = Integer.parseInt(month.substring(0, 4)) + " 年 "
                + Integer.parseInt(month.substring(5)) + " 月";
        TextView titleView = text(title, 16, getColor(R.color.text_primary), true);
        titleView.setGravity(Gravity.CENTER);
        monthRow.addView(titleView, new LinearLayout.LayoutParams(0, dp(46), 1));
        monthRow.addView(next, new LinearLayout.LayoutParams(dp(46), dp(46)));
        previous.setOnClickListener(view -> {
            calendarMonth = ProductionDailyDates.shiftMonth(calendarMonth, -1);
            fillCalendar(body, dialog);
            loadCalendarHistory(body, dialog);
        });
        next.setOnClickListener(view -> {
            calendarMonth = ProductionDailyDates.shiftMonth(calendarMonth, 1);
            fillCalendar(body, dialog);
            loadCalendarHistory(body, dialog);
        });
        body.addView(monthRow);
        LinearLayout weekdays = row();
        for (char day : "一二三四五六日".toCharArray()) {
            TextView view = text(String.valueOf(day), 11, getColor(R.color.text_muted), true);
            view.setGravity(Gravity.CENTER);
            weekdays.addView(view, new LinearLayout.LayoutParams(0, dp(28), 1));
        }
        append(body, weekdays, 8);
        int offset = ProductionDailyDates.mondayOffset(month);
        int days = ProductionDailyDates.daysInMonth(month);
        int cells = ((offset + days + 6) / 7) * 7;
        String firstDate = month + "-01";
        for (int week = 0; week < cells / 7; week++) {
            LinearLayout weekRow = row();
            for (int day = 0; day < 7; day++) {
                String cellDate = ProductionDailyDates.shiftDay(firstDate, week * 7 + day - offset);
                ProductionDailyReportData.History history = calendarHistory.get(cellDate);
                boolean selected = cellDate.equals(date);
                boolean adjacent = !cellDate.startsWith(month);
                LinearLayout cell = column();
                cell.setGravity(Gravity.CENTER);
                TextView number = text(String.valueOf(Integer.parseInt(cellDate.substring(8))), 13,
                        selected ? Color.WHITE : adjacent ? Color.rgb(170, 180, 194)
                                : getColor(R.color.text_primary), true);
                number.setGravity(Gravity.CENTER);
                if (selected) number.setBackground(shape(getColor(R.color.primary), 0, 18));
                else if (cellDate.equals(ProductionDailyDates.today())) {
                    number.setBackground(shape(Color.WHITE, getColor(R.color.primary), 18));
                }
                cell.addView(number, new LinearLayout.LayoutParams(dp(32), dp(32)));
                TextView dot = text(history == null ? " " : "•", 14,
                        history != null && "SUBMITTED".equals(history.status)
                                ? getColor(R.color.success) : getColor(R.color.warning), true);
                dot.setGravity(Gravity.CENTER);
                cell.addView(dot, new LinearLayout.LayoutParams(dp(14), dp(9)));
                cell.setContentDescription(ProductionDailyDates.display(cellDate)
                        + (history == null ? "，待填写" : "SUBMITTED".equals(history.status)
                        ? "，已提交" : "，草稿"));
                cell.setOnClickListener(view -> {
                    if (report.importPending) { toast("请先处理或取消当前导入"); return; }
                    dialog.dismiss();
                    chooseDate(cellDate);
                });
                weekRow.addView(cell, new LinearLayout.LayoutParams(0, dp(45), 1));
            }
            body.addView(weekRow);
        }
        LinearLayout legend = row();
        legend.setGravity(Gravity.CENTER);
        legend.addView(text("● ", 13, getColor(R.color.warning), true));
        legend.addView(text("草稿    ", 10, getColor(R.color.text_muted), false));
        legend.addView(text("● ", 13, getColor(R.color.success), true));
        legend.addView(text("已提交    ", 10, getColor(R.color.text_muted), false));
        legend.addView(text("无标记为待填写", 10, getColor(R.color.text_muted), false));
        append(body, legend, 10);
        divider(body);
        int historyCount = 0;
        for (ProductionDailyReportData.History history : calendarHistory.values()) {
            if (history.date.startsWith(month)) historyCount++;
        }
        append(body, heading("本月历史日报", historyCount + " 条已保存"), 4);
        List<ProductionDailyReportData.History> histories = new ArrayList<>();
        for (ProductionDailyReportData.History history : calendarHistory.values()) {
            if (history.date.startsWith(month)) histories.add(history);
        }
        histories.sort((a, b) -> b.date.compareTo(a.date));
        if (histories.isEmpty()) {
            append(body, text("本月暂无已保存的日报，可选择日期开始填写。", 11,
                    getColor(R.color.text_muted), false), 11);
        }
        for (ProductionDailyReportData.History history : histories) {
            LinearLayout historyRow = row();
            historyRow.setMinimumHeight(dp(56));
            String label = Integer.parseInt(history.date.substring(5, 7)) + "月"
                    + Integer.parseInt(history.date.substring(8)) + "日";
            historyRow.addView(text(label + "  星期" + ProductionDailyDates.weekday(history.date), 13,
                    getColor(R.color.text_primary), true),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            historyRow.addView(statusBadge(history.status, false));
            historyRow.addView(icon(R.drawable.ic_daily_next, getColor(R.color.primary), 17));
            historyRow.setOnClickListener(view -> {
                if (report.importPending) { toast("请先处理或取消当前导入"); return; }
                dialog.dismiss();
                chooseDate(history.date);
            });
            append(body, historyRow, 4);
            divider(body);
        }
        if (!date.equals(ProductionDailyDates.today())
                || !calendarMonth.equals(ProductionDailyDates.today().substring(0, 7))) {
            Button todayButton = actionButton("回到今天", false);
            todayButton.setOnClickListener(view -> {
                dialog.dismiss();
                chooseDate(ProductionDailyDates.today());
            });
            append(body, todayButton, 10);
        }
    }

    private TextView calendarNav(String label, String description) {
        TextView control = text(label, 25, getColor(R.color.primary), false);
        control.setGravity(Gravity.CENTER);
        control.setBackground(shape(getColor(R.color.primary_soft), 0, 12));
        control.setContentDescription(description);
        return control;
    }

    private void chooseImportFile() {
        if (busy || !canEdit() || report == null || report.importPending || showingServerCopy) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel", "application/octet-stream"
        });
        startActivityForResult(intent, PICK_IMPORT);
    }

    private void exportExcel() {
        if (busy || report == null || !canExport()) return;
        if (report.dirty()) {
            new AlertDialog.Builder(this)
                    .setTitle("导出已保存的日报？")
                    .setMessage("当前有未保存修改。导出的 Excel 将以服务器上已保存的内容为准。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("继续导出", (dialog, which) -> downloadExcel())
                    .show();
        } else {
            downloadExcel();
        }
    }

    private void downloadExcel() {
        busy = true;
        renderActions();
        api.exportProductionDailyReport(date, new ApiClient.Callback<>() {
            @Override public void onSuccess(byte[] value) {
                busy = false;
                renderActions();
                pendingExport = value;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                intent.putExtra(Intent.EXTRA_TITLE, "来冰" + date.replace("-", "") + "生产日报表.xlsx");
                startActivityForResult(intent, SAVE_EXPORT);
            }
            @Override public void onError(String message, int httpStatus) {
                handleApiError(message, httpStatus);
                renderActions();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SAVE_EXPORT) {
            byte[] bytes = pendingExport;
            pendingExport = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && bytes != null) {
                Uri target = data.getData();
                new Thread(() -> {
                    try (OutputStream output = getContentResolver().openOutputStream(target)) {
                        if (output == null) throw new IOException("无法打开保存位置");
                        output.write(bytes);
                        handler.post(() -> toast("Excel 已保存"));
                    } catch (IOException exception) {
                        handler.post(() -> showMessage("Excel 保存失败：" + exception.getMessage(), true));
                    }
                }).start();
            }
            return;
        }
        if (requestCode != PICK_IMPORT || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        Uri source = data.getData();
        String filename = fileName(source);
        if (!filename.toLowerCase(Locale.ROOT).matches(".*\\.(xlsx|xls)")) {
            showMessage("请选择 .xlsx 或 .xls 文件", true);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("导入生产日报？")
                .setMessage("Excel 中的日报日期可能与当前日期不同。导入将覆盖该日期的整张日报。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认导入", (dialog, which) -> readAndImport(source, filename))
                .show();
    }

    private String fileName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (RuntimeException ignored) {
            // The document provider may omit a display name.
        }
        return "生产日报.xlsx";
    }

    private void readAndImport(Uri uri, String filename) {
        busy = true;
        renderActions();
        showMessage("正在读取 Excel 文件…", false);
        new Thread(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (input == null) throw new IOException("无法打开所选文件");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > MAX_IMPORT_BYTES) {
                        throw new IOException("Excel 文件不能超过 10MB");
                    }
                    output.write(buffer, 0, count);
                }
                byte[] bytes = output.toByteArray();
                handler.post(() -> uploadExcel(filename, bytes));
            } catch (IOException exception) {
                handler.post(() -> {
                    busy = false;
                    showMessage("读取 Excel 失败：" + exception.getMessage(), true);
                    renderActions();
                });
            }
        }).start();
    }

    private void uploadExcel(String filename, byte[] bytes) {
        showMessage("正在导入生产日报…", false);
        api.importProductionDailyReport(filename, bytes, new ApiClient.Callback<>() {
            @Override public void onSuccess(JSONObject value) {
                try {
                    ProductionDailyReportData.Report preview =
                            ProductionDailyReportData.parseReport(value.getJSONObject("report"));
                    boolean saved = value.optBoolean("saved", false);
                    int unmatched = value.optInt("unmatchedCount", 0);
                    persistDraft();
                    report = preview;
                    date = preview.date;
                    calendarMonth = date.substring(0, 7);
                    report.importPending = !saved;
                    showingServerCopy = false;
                    busy = false;
                    if (saved) {
                        drafts.removeImportPreview(preferences.userName(), date);
                        drafts.remove(preferences.userName(), date);
                        serverCopy = ProductionDailyReportData.parseReport(value.getJSONObject("report"));
                        rememberHistory(serverCopy);
                        level = 0;
                        hideMessage();
                        toast("导入成功");
                    } else {
                        persistDraft();
                        focusFirstUnmatched();
                        showMessage("有 " + unmatched + " 个产品未找到匹配。请选择产品后再保存导入。", true);
                    }
                    render(true);
                } catch (JSONException exception) {
                    busy = false;
                    showMessage("导入结果格式不正确，请重新打开日报", true);
                    renderActions();
                }
            }
            @Override public void onError(String message, int httpStatus) {
                handleApiError(message, httpStatus);
                renderActions();
            }
        });
    }

    private void focusFirstUnmatched() {
        level = 0;
        if (report == null) return;
        for (ProductionDailyReportData.Section section : report.sections) {
            for (ProductionDailyReportData.Group group : section.groups) {
                for (ProductionDailyReportData.Row row : group.products) {
                    if (row.productId == null && row.hasContent()) {
                        sectionCode = section.code;
                        groupCode = group.code;
                        level = 2;
                        field = 0;
                        return;
                    }
                }
            }
        }
    }
}

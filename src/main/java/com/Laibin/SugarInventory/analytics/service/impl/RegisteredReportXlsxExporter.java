package com.Laibin.SugarInventory.analytics.service.impl;

import com.Laibin.SugarInventory.analytics.domain.po.RegisteredReportRunPO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RegisteredReportXlsxExporter {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("outputRecordCount", "产出记录数"),
            Map.entry("productionOrderCount", "生产订单数"),
            Map.entry("totalWeightKg", "登记重量（kg）"),
            Map.entry("totalBoardCount", "整板数"),
            Map.entry("loosePieceCount", "散件数"),
            Map.entry("totalPieces", "折算总件数"),
            Map.entry("requiredQrCount", "所需二维码数"),
            Map.entry("boundQrCount", "已绑定二维码数"),
            Map.entry("inboundQrCount", "已入库二维码数"),
            Map.entry("metricLabel", "对比指标"),
            Map.entry("currentValue", "本期值"),
            Map.entry("comparisonValue", "对比期值"),
            Map.entry("absoluteChange", "变化量"),
            Map.entry("percentChange", "变化率（%）"),
            Map.entry("currentDailyAverage", "本期日均"),
            Map.entry("comparisonDailyAverage", "对比期日均"),
            Map.entry("dailyAverageAbsoluteChange", "日均变化量"),
            Map.entry("dailyAveragePercentChange", "日均变化率（%）"),
            Map.entry("note", "说明"),
            Map.entry("businessDate", "业务日期"),
            Map.entry("productName", "产品"),
            Map.entry("productStatus", "产品状态"),
            Map.entry("periodLabel", "统计期间"),
            Map.entry("assayRecordCount", "化验记录数"),
            Map.entry("passCount", "合格数"),
            Map.entry("failCount", "不合格数"),
            Map.entry("noStandardCount", "无标准数"),
            Map.entry("multipleCandidatesCount", "标准多候选数"),
            Map.entry("unknownJudgementCount", "未形成受控判定数"),
            Map.entry("judgedRecordCount", "明确判定数"),
            Map.entry("passRatePercent", "明确判定合格率（%）"),
            Map.entry("standardLabel", "历史采用标准"),
            Map.entry("metricName", "指标"),
            Map.entry("unit", "单位"),
            Map.entry("sampleCount", "实测样本数"),
            Map.entry("missingValueCount", "缺失实测值数"),
            Map.entry("comparableStandardCount", "可比较标准样本数"),
            Map.entry("withinStandardCount", "标准范围内样本数"),
            Map.entry("outOfStandardCount", "超出标准样本数"),
            Map.entry("withoutComparableStandardCount", "无可比较标准样本数"),
            Map.entry("withinStandardRatePercent", "历史标准下达标率（%）"),
            Map.entry("averageValue", "均值"),
            Map.entry("medianValue", "中位数"),
            Map.entry("minimumValue", "最小值"),
            Map.entry("maximumValue", "最大值"),
            Map.entry("p10Value", "P10"),
            Map.entry("p90Value", "P90"),
            Map.entry("rangeLabel", "历史标准范围"),
            Map.entry("materialInputRecordCount", "实际领料记录数"),
            Map.entry("materialInputOrderCount", "实际领料订单数"),
            Map.entry("materialInputPalletCount", "领料托盘数"),
            Map.entry("materialInputBoardCount", "领料整板数"),
            Map.entry("materialInputLoosePieceCount", "领料散件数"),
            Map.entry("materialInputTotalPieces", "领料折算件数"),
            Map.entry("materialInputWeightKg", "实际领料重量（kg）"),
            Map.entry("stableOutputRecordCount", "稳定登记产出记录数"),
            Map.entry("stableOutputOrderCount", "稳定产出订单数"),
            Map.entry("stableOutputBoardCount", "稳定产出整板数"),
            Map.entry("stableOutputLoosePieceCount", "稳定产出散件数"),
            Map.entry("stableOutputTotalPieces", "稳定产出折算件数"),
            Map.entry("stableOutputWeightKg", "稳定登记产出重量（kg）"),
            Map.entry("cohortOrderCount", "订单队列数"),
            Map.entry("completedOrderCount", "已完成订单数"),
            Map.entry("completedOrdersWithInputCount", "已完成且有投入事实订单数"),
            Map.entry("completedOrdersMissingInputCount", "已完成但缺投入事实订单数"),
            Map.entry("completedOrdersWithStableOutputCount", "已完成且有稳定产出订单数"),
            Map.entry("completedOrdersMissingOutputCount", "已完成但缺稳定产出订单数"),
            Map.entry("ordersWithInputCount", "有投入事实订单数"),
            Map.entry("ordersMissingInputCount", "缺投入事实订单数"),
            Map.entry("ordersWithStableOutputCount", "有稳定产出订单数"),
            Map.entry("ordersMissingOutputCount", "缺稳定产出订单数"),
            Map.entry("cohortMaterialInputWeightKg", "订单归属领料重量（kg）"),
            Map.entry("cohortBoilingInputWeightKg", "订单归属煮糖确认使用重量（kg）"),
            Map.entry("cohortStableOutputWeightKg", "订单归属稳定产出重量（kg）"),
            Map.entry("orderNo", "生产订单"),
            Map.entry("orderTypeLabel", "订单类型"),
            Map.entry("orderStatusLabel", "订单状态"),
            Map.entry("productionDate", "生产日期"),
            Map.entry("inputSourceLabel", "投入事实来源"),
            Map.entry("inputRecordCount", "投入记录数"),
            Map.entry("inputPalletCount", "投入托盘数"),
            Map.entry("inputTotalPieces", "投入折算件数"),
            Map.entry("inputWeightKg", "投入重量（kg）"),
            Map.entry("outputProductNames", "稳定产出产品"),
            Map.entry("completenessLabel", "事实完整性"),
            Map.entry("cohortTaskCount", "任务队列数"),
            Map.entry("completedTaskCount", "已完成任务数"),
            Map.entry("inProgressTaskCount", "进行中任务数"),
            Map.entry("canceledTaskCount", "已取消任务数"),
            Map.entry("invalidTaskCount", "无效时间记录数"),
            Map.entry("taskCount", "任务数"),
            Map.entry("averageDurationSeconds", "完成耗时均值（秒）"),
            Map.entry("medianDurationSeconds", "完成耗时中位数（秒）"),
            Map.entry("p90DurationSeconds", "完成耗时 P90（秒）"),
            Map.entry("maximumDurationSeconds", "完成耗时最大值（秒）"),
            Map.entry("medianWaitingSeconds", "进行中等待中位数（秒）"),
            Map.entry("p90WaitingSeconds", "进行中等待 P90（秒）"),
            Map.entry("maximumWaitingSeconds", "进行中等待最大值（秒）"),
            Map.entry("taskTypeLabel", "任务类型"),
            Map.entry("palletCode", "托盘码"),
            Map.entry("createdAt", "任务创建时间"),
            Map.entry("waitingSeconds", "已等待时长（秒）"),
            Map.entry("targetWarehouseName", "目标库位"),
            Map.entry("partial", "数据是否不完整"),
            Map.entry("rowsMissingWeight", "缺少重量记录数"),
            Map.entry("rowsMissingPieceConversion", "缺少件数换算记录数"),
            Map.entry("rowsMissingProductName", "缺少产品名称记录数"),
            Map.entry("rowsWithoutControlledJudgement", "缺少受控判定记录数"),
            Map.entry("rowsWithoutComparableMetricStandard", "无可比较指标标准记录数"),
            Map.entry("rowsWithUnexpectedMetricUnit", "指标单位不一致记录数"),
            Map.entry("draftOutputExcludedCount", "排除草稿产出数"),
            Map.entry("canceledOutputExcludedCount", "排除已取消产出数"),
            Map.entry("crossDayInboundCount", "跨生产日期入库码数"),
            Map.entry("unattributedOrderCount", "无法按产品归属订单数"),
            Map.entry("orderBreakdownTruncated", "订单明细是否截断"),
            Map.entry("invalidDurationCount", "无效耗时记录数"),
            Map.entry("canceledWithoutTerminalTimeCount", "缺少取消时间的已取消任务数"),
            Map.entry("tasksWithoutFlowRecordCount", "无关联流转记录任务数"),
            Map.entry("tasksWithoutOperationBatchCount", "无操作批次号任务数"),
            Map.entry("observationDayCount", "观察天数"),
            Map.entry("openingPieces", "期初库存（件）"),
            Map.entry("closingPieces", "期末库存（件）"),
            Map.entry("netChangePieces", "库存净变化（件）"),
            Map.entry("openingWeightKg", "期初库存重量（kg）"),
            Map.entry("closingWeightKg", "期末库存重量（kg）"),
            Map.entry("netChangeWeightKg", "库存重量净变化（kg）"),
            Map.entry("increaseDayCount", "库存增加天数"),
            Map.entry("decreaseDayCount", "库存减少天数"),
            Map.entry("unchangedDayCount", "库存不变天数"),
            Map.entry("pieceChange", "较前一日变化（件）"),
            Map.entry("weightChangeKg", "较前一日重量变化（kg）"),
            Map.entry("movementRecordCount", "当日回放流水数"),
            Map.entry("simulationData", "是否为历史回放模拟"),
            Map.entry("replayMovementRecordCount", "回放流水记录数"),
            Map.entry("replayAnchorReconciled", "回放锚点是否守恒"),
            Map.entry("trustedSnapshotDayCount", "可信快照天数"),
            Map.entry("requiredSnapshotDayCount", "查询要求天数"),
            Map.entry("productCount", "当前库存产品数"),
            Map.entry("warehouseCount", "当前有库存库位数"),
            Map.entry("palletCount", "当前库存托盘数"),
            Map.entry("totalEquivalentPieces", "当前库存折算件数"),
            Map.entry("totalStockText", "当前库存数量"),
            Map.entry("totalWeightText", "当前库存重量"),
            Map.entry("currentPendingTaskCount", "当前待处理任务数")
    );

    private final ObjectMapper objectMapper;

    public byte[] export(
            RegisteredReportRunVO report,
            RegisteredReportRunPO stored,
            String exporterDisplayName,
            String auditRef,
            LocalDateTime exportedAt) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            writeSummary(workbook, styles, report, stored, exporterDisplayName, auditRef, exportedAt);
            writeReportData(workbook, styles, report);
            writeQualityAndLimitations(workbook, styles, report);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("生成报表导出文件失败", exception);
        }
    }

    private void writeSummary(
            Workbook workbook,
            Styles styles,
            RegisteredReportRunVO report,
            RegisteredReportRunPO stored,
            String exporterDisplayName,
            String auditRef,
            LocalDateTime exportedAt) {
        Sheet sheet = workbook.createSheet("报表说明");
        int row = 0;
        row = keyValue(sheet, styles, row, "报表名称", report.getReportName());
        row = keyValue(sheet, styles, row, "报表版本", report.getReportVersion());
        row = keyValue(sheet, styles, row, "统计范围", report.getDateRangeLabel());
        row = keyValue(sheet, styles, row, "数据截止时间", report.getDataAsOf());
        row = keyValue(sheet, styles, row, "最新业务记录时间", report.getLatestRecordAt());
        row = keyValue(sheet, styles, row, "报表生成时间", stored.getGeneratedAt());
        row = keyValue(sheet, styles, row, "导出人", exporterDisplayName);
        row = keyValue(sheet, styles, row, "导出时间", exportedAt);
        row = keyValue(sheet, styles, row, "报表运行编号", stored.getReportRunId());
        row = keyValue(sheet, styles, row, "导出审计编号", auditRef);
        row = keyValue(sheet, styles, row, "内容校验值（SHA-256）", stored.getContentSha256());
        if (report.getComparison() != null) {
            row = keyValue(sheet, styles, row, "对比方式", report.getComparison().getComparisonLabel());
            row = keyValue(sheet, styles, row, "本期范围", report.getComparison().getCurrentDateRangeLabel());
            row = keyValue(sheet, styles, row, "对比期范围", report.getComparison().getComparisonDateRangeLabel());
        }
        if (report.getFiltersApplied() != null) {
            for (Map.Entry<String, String> entry : report.getFiltersApplied().entrySet()) {
                row = keyValue(sheet, styles, row, filterLabel(entry.getKey()), entry.getValue());
            }
        }
        sheet.setColumnWidth(0, 24 * 256);
        sheet.setColumnWidth(1, 72 * 256);
    }

    private void writeReportData(Workbook workbook, Styles styles, RegisteredReportRunVO report) {
        if (report.getOperationsOverview() != null) {
            writeVertical(workbook, styles, "今日产出",
                    toMap(report.getOperationsOverview().getProductionOutput()), List.of(
                            "outputRecordCount", "productionOrderCount", "totalWeightKg",
                            "totalBoardCount", "loosePieceCount", "totalPieces"));
            writeVertical(workbook, styles, "今日化验",
                    toMap(report.getOperationsOverview().getAssayQuality()), List.of(
                            "assayRecordCount", "passCount", "failCount", "noStandardCount",
                            "multipleCandidatesCount", "judgedRecordCount", "passRatePercent"));
            writeVertical(workbook, styles, "今日领用与产出",
                    toMap(report.getOperationsOverview().getProductionFlow()), List.of(
                            "materialInputRecordCount", "materialInputOrderCount",
                            "materialInputPalletCount", "materialInputTotalPieces",
                            "materialInputWeightKg", "stableOutputRecordCount",
                            "stableOutputOrderCount", "stableOutputTotalPieces",
                            "stableOutputWeightKg"));
            writeVertical(workbook, styles, "当前库存",
                    toMap(report.getOperationsOverview().getCurrentInventory()), List.of(
                            "productCount", "warehouseCount", "palletCount",
                            "totalEquivalentPieces", "totalStockText", "totalWeightText"));
            Map<String, Object> taskMetrics = toMap(
                    report.getOperationsOverview().getTodayPalletTasks());
            taskMetrics.put("currentPendingTaskCount",
                    report.getOperationsOverview().getCurrentPendingTaskCount());
            writeVertical(workbook, styles, "今日托盘任务", taskMetrics, List.of(
                    "cohortTaskCount", "completedTaskCount", "inProgressTaskCount",
                    "canceledTaskCount", "currentPendingTaskCount"));
            return;
        }
        if (report.getComparison() != null) {
            writeTable(workbook, styles, "跨期比较", report.getComparison().getMetrics(), List.of(
                    "metricLabel", "unit", "currentValue", "comparisonValue",
                    "absoluteChange", "percentChange", "currentDailyAverage",
                    "comparisonDailyAverage", "dailyAverageAbsoluteChange",
                    "dailyAveragePercentChange", "note"));
        }
        if (report.getMetrics() != null) {
            writeVertical(workbook, styles, "核心指标", toMap(report.getMetrics()), List.of(
                    "outputRecordCount", "productionOrderCount", "totalWeightKg", "totalBoardCount",
                    "loosePieceCount", "totalPieces", "requiredQrCount", "boundQrCount", "inboundQrCount"));
            writeTable(workbook, styles, "按日序列", report.getDailySeries(), List.of(
                    "businessDate", "outputRecordCount", "productionOrderCount", "totalWeightKg",
                    "totalBoardCount", "loosePieceCount", "totalPieces"));
            writeTable(workbook, styles, "产品分布", report.getProductBreakdowns(), List.of(
                    "productName", "productStatus", "outputRecordCount", "productionOrderCount",
                    "totalWeightKg", "totalBoardCount", "loosePieceCount", "totalPieces"));
            return;
        }
        if (report.getInventoryTrendMetrics() != null) {
            writeVertical(workbook, styles, "核心指标", toMap(report.getInventoryTrendMetrics()), List.of(
                    "observationDayCount", "openingPieces", "closingPieces", "netChangePieces",
                    "openingWeightKg", "closingWeightKg", "netChangeWeightKg",
                    "increaseDayCount", "decreaseDayCount", "unchangedDayCount"));
            writeTable(workbook, styles, "每日库存水平", report.getInventoryTrendDailySeries(), List.of(
                    "businessDate", "totalPieces", "totalWeightKg", "pieceChange",
                    "weightChangeKg", "movementRecordCount"));
            writeTable(workbook, styles, "产品库存变化", report.getInventoryTrendProductBreakdowns(), List.of(
                    "productName", "openingPieces", "closingPieces", "netChangePieces",
                    "openingWeightKg", "closingWeightKg", "netChangeWeightKg"));
            return;
        }
        if (report.getQualityMetrics() != null) {
            writeVertical(workbook, styles, "核心指标", toMap(report.getQualityMetrics()), List.of(
                    "assayRecordCount", "passCount", "failCount", "noStandardCount",
                    "multipleCandidatesCount", "unknownJudgementCount",
                    "judgedRecordCount", "passRatePercent"));
            writeTable(workbook, styles, "质量序列", report.getQualitySeries(), List.of(
                    "periodLabel", "assayRecordCount", "passCount", "failCount",
                    "noStandardCount", "multipleCandidatesCount", "unknownJudgementCount",
                    "judgedRecordCount", "passRatePercent"));
            writeTable(workbook, styles, "产品分布", report.getQualityProductBreakdowns(), List.of(
                    "productName", "assayRecordCount", "passCount", "failCount",
                    "noStandardCount", "multipleCandidatesCount", "unknownJudgementCount",
                    "judgedRecordCount", "passRatePercent"));
            writeTable(workbook, styles, "历史标准", report.getStandardBreakdowns(), List.of(
                    "standardLabel", "assayRecordCount", "passCount", "failCount",
                    "judgedRecordCount", "passRatePercent"));
            return;
        }
        if (report.getMetricTrendSummary() != null) {
            writeVertical(workbook, styles, "核心指标", toMap(report.getMetricTrendSummary()), List.of(
                    "metricName", "unit", "assayRecordCount", "sampleCount", "missingValueCount",
                    "comparableStandardCount", "withinStandardCount", "outOfStandardCount",
                    "withoutComparableStandardCount", "withinStandardRatePercent", "averageValue",
                    "medianValue", "minimumValue", "maximumValue", "p10Value", "p90Value"));
            writeTable(workbook, styles, "指标序列", report.getMetricSeries(), List.of(
                    "periodLabel", "sampleCount", "averageValue", "medianValue",
                    "minimumValue", "maximumValue"));
            writeTable(workbook, styles, "产品分布", report.getMetricProductBreakdowns(), List.of(
                    "productName", "sampleCount", "averageValue", "medianValue",
                    "minimumValue", "maximumValue"));
            writeTable(workbook, styles, "历史标准", report.getMetricStandardBreakdowns(), List.of(
                    "standardLabel", "rangeLabel", "unit", "sampleCount",
                    "withinStandardCount", "outOfStandardCount", "withinStandardRatePercent"));
            return;
        }
        if (report.getProductionFlowMetrics() != null) {
            writeVertical(workbook, styles, "核心指标", toMap(report.getProductionFlowMetrics()), List.of(
                    "materialInputRecordCount", "materialInputOrderCount", "materialInputPalletCount",
                    "materialInputBoardCount", "materialInputLoosePieceCount", "materialInputTotalPieces",
                    "materialInputWeightKg", "stableOutputRecordCount", "stableOutputOrderCount",
                    "stableOutputBoardCount", "stableOutputLoosePieceCount", "stableOutputTotalPieces",
                    "stableOutputWeightKg", "cohortOrderCount", "completedOrderCount",
                    "completedOrdersWithInputCount", "completedOrdersMissingInputCount",
                    "completedOrdersWithStableOutputCount", "completedOrdersMissingOutputCount",
                    "ordersWithInputCount", "ordersMissingInputCount", "ordersWithStableOutputCount",
                    "ordersMissingOutputCount", "cohortMaterialInputWeightKg",
                    "cohortBoilingInputWeightKg", "cohortStableOutputWeightKg"));
            writeTable(workbook, styles, "每日独立序列", report.getProductionFlowDailySeries(), List.of(
                    "businessDate", "materialInputRecordCount", "materialInputOrderCount",
                    "materialInputPalletCount", "materialInputTotalPieces", "materialInputWeightKg",
                    "stableOutputRecordCount", "stableOutputOrderCount", "stableOutputTotalPieces",
                    "stableOutputWeightKg"));
            writeTable(workbook, styles, "订单归属事实", report.getProductionFlowOrderBreakdowns(), List.of(
                    "orderNo", "orderTypeLabel", "orderStatusLabel", "productionDate",
                    "inputSourceLabel", "inputRecordCount", "inputPalletCount", "inputTotalPieces",
                    "inputWeightKg", "stableOutputRecordCount", "stableOutputTotalPieces",
                    "stableOutputWeightKg", "outputProductNames", "completenessLabel"));
            return;
        }
        if (report.getPalletTaskCycleMetrics() != null) {
            writeVertical(workbook, styles, "核心指标", toMap(report.getPalletTaskCycleMetrics()), List.of(
                    "cohortTaskCount", "completedTaskCount", "inProgressTaskCount",
                    "canceledTaskCount", "invalidTaskCount", "averageDurationSeconds",
                    "medianDurationSeconds", "p90DurationSeconds", "maximumDurationSeconds",
                    "medianWaitingSeconds", "p90WaitingSeconds", "maximumWaitingSeconds"));
            writeTable(workbook, styles, "按日任务周期", report.getPalletTaskCycleDailySeries(), List.of(
                    "businessDate", "taskCount", "completedTaskCount", "inProgressTaskCount",
                    "canceledTaskCount", "averageDurationSeconds", "medianDurationSeconds",
                    "p90DurationSeconds", "maximumDurationSeconds", "maximumWaitingSeconds"));
            writeTable(workbook, styles, "任务类型分布", report.getPalletTaskCycleTypeBreakdowns(), List.of(
                    "taskTypeLabel", "taskCount", "completedTaskCount", "inProgressTaskCount",
                    "canceledTaskCount", "invalidTaskCount", "averageDurationSeconds",
                    "medianDurationSeconds", "p90DurationSeconds", "maximumDurationSeconds",
                    "maximumWaitingSeconds"));
            writeTable(workbook, styles, "进行中任务", report.getPalletTaskPendingItems(), List.of(
                    "palletCode", "taskTypeLabel", "productName", "createdAt",
                    "waitingSeconds", "targetWarehouseName"));
        }
    }

    private void writeQualityAndLimitations(
            Workbook workbook,
            Styles styles,
            RegisteredReportRunVO report) {
        if (report.getDataQuality() != null) {
            Map<String, Object> quality = toMap(report.getDataQuality());
            writeVertical(workbook, styles, "数据质量", quality, List.of(
                    "partial", "rowsMissingWeight", "rowsMissingPieceConversion",
                    "rowsMissingProductName", "rowsWithoutControlledJudgement",
                    "rowsWithoutComparableMetricStandard", "rowsWithUnexpectedMetricUnit",
                    "completedOrderCount", "completedOrdersWithInputCount",
                    "completedOrdersMissingInputCount", "completedOrdersWithStableOutputCount",
                    "completedOrdersMissingOutputCount", "draftOutputExcludedCount",
                    "canceledOutputExcludedCount", "crossDayInboundCount",
                    "unattributedOrderCount", "orderBreakdownTruncated", "invalidDurationCount",
                    "canceledWithoutTerminalTimeCount", "tasksWithoutFlowRecordCount",
                    "tasksWithoutOperationBatchCount", "simulationData",
                    "replayMovementRecordCount", "replayAnchorReconciled",
                    "trustedSnapshotDayCount", "requiredSnapshotDayCount"));
            writeTextList(workbook, styles, "数据质量提示", report.getDataQuality().getNotes());
        }
        writeTextList(workbook, styles, "口径与限制", report.getLimitations());
    }

    private void writeVertical(
            Workbook workbook,
            Styles styles,
            String sheetName,
            Map<String, Object> values,
            List<String> keys) {
        Sheet sheet = workbook.createSheet(sheetName);
        int row = 0;
        for (String key : keys) {
            if (!values.containsKey(key) || values.get(key) == null) {
                continue;
            }
            row = keyValue(sheet, styles, row, label(key), values.get(key));
        }
        sheet.setColumnWidth(0, 30 * 256);
        sheet.setColumnWidth(1, 28 * 256);
    }

    private void writeTable(
            Workbook workbook,
            Styles styles,
            String sheetName,
            List<?> records,
            List<String> keys) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Sheet sheet = workbook.createSheet(sheetName);
        Row header = sheet.createRow(0);
        for (int column = 0; column < keys.size(); column++) {
            cell(header, column, label(keys.get(column)), styles.header());
        }
        for (int index = 0; index < records.size(); index++) {
            Map<String, Object> values = toMap(records.get(index));
            Row row = sheet.createRow(index + 1);
            for (int column = 0; column < keys.size(); column++) {
                setValue(cell(row, column, null, styles.body()), values.get(keys.get(column)));
            }
        }
        for (int column = 0; column < keys.size(); column++) {
            sheet.setColumnWidth(column, 20 * 256);
        }
    }

    private void writeTextList(
            Workbook workbook,
            Styles styles,
            String sheetName,
            List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        Sheet sheet = workbook.createSheet(sheetName);
        cell(sheet.createRow(0), 0, "内容", styles.header());
        for (int index = 0; index < values.size(); index++) {
            cell(sheet.createRow(index + 1), 0, values.get(index), styles.body());
        }
        sheet.setColumnWidth(0, 90 * 256);
    }

    private int keyValue(
            Sheet sheet,
            Styles styles,
            int rowIndex,
            String key,
            Object value) {
        Row row = sheet.createRow(rowIndex);
        cell(row, 0, key, styles.key());
        setValue(cell(row, 1, null, styles.body()), value);
        return rowIndex + 1;
    }

    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, MAP_TYPE);
    }

    private static String filterLabel(String key) {
        return switch (key) {
            case "businessDate" -> "业务日期筛选";
            case "productScope" -> "产品范围";
            case "calendarInputTime" -> "日历投入时间口径";
            case "calendarOutputTime" -> "日历产出时间口径";
            case "taskScope" -> "任务类型范围";
            case "inventoryScope" -> "库存范围";
            case "dataSource" -> "数据来源";
            default -> "筛选条件";
        };
    }

    private static String label(String key) {
        return LABELS.getOrDefault(key, "登记字段");
    }

    private static Cell cell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellStyle(style);
        if (value != null) {
            cell.setCellValue(value);
        }
        return cell;
    }

    private static void setValue(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof Boolean bool) {
            cell.setCellValue(bool ? "是" : "否");
        } else if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(dateTime.format(DATE_TIME));
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private record Styles(CellStyle header, CellStyle key, CellStyle body) {
        private Styles(Workbook workbook) {
            this(headerStyle(workbook), keyStyle(workbook), bodyStyle(workbook));
        }

        private static CellStyle headerStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            style.setFillForegroundColor((short) 42);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            return style;
        }

        private static CellStyle keyStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            return style;
        }

        private static CellStyle bodyStyle(Workbook workbook) {
            return workbook.createCellStyle();
        }
    }
}

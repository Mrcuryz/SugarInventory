package com.Laibin.SugarInventory.analytics.service.impl;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.vo.ProductionFlowCalendarSummaryRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.ProductionFlowCohortSummaryRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.ProductionFlowDailyInputRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.ProductionFlowDailyOutputRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.ProductionFlowOrderRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.ProductionFlowOutputQualityRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportDataQualityVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportProductionFlowDailyPointVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportProductionFlowMetricsVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportProductionFlowOrderBreakdownVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;
import com.Laibin.SugarInventory.analytics.mapper.ProductionInputOutputReportMapper;
import com.Laibin.SugarInventory.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductionInputOutputRegisteredReportService {
    public static final String REPORT_ID = "production_input_output_flow_v1";
    public static final int REPORT_VERSION = 1;
    public static final String METRIC_DEFINITION_VERSION = "production_input_output_flow_facts_v1";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_RANGE_DAYS = 366;
    private static final int ORDER_BREAKDOWN_LIMIT = 200;

    private final ProductionInputOutputReportMapper mapper;

    public RegisteredReportRunVO run(RegisteredReportRunQueryDTO query) {
        ValidatedQuery validated = validate(query);
        ProductionFlowCalendarSummaryRowVO calendar = mapper.selectCalendarSummary(
                validated.startDate(), validated.endDate(), validated.productQuery());
        ProductionFlowCohortSummaryRowVO cohort = mapper.selectCohortSummary(
                validated.startDate(), validated.endDate(), validated.productQuery());
        ProductionFlowOutputQualityRowVO outputQuality = mapper.selectOutputQuality(
                validated.startDate(), validated.endDate(), validated.productQuery());
        List<ProductionFlowDailyInputRowVO> inputRows = safeList(mapper.listDailyMaterialInputs(
                validated.startDate(), validated.endDate(), validated.productQuery()));
        List<ProductionFlowDailyOutputRowVO> outputRows = safeList(mapper.listDailyStableOutputs(
                validated.startDate(), validated.endDate(), validated.productQuery()));
        List<ProductionFlowOrderRowVO> queriedOrders = safeList(mapper.listCohortOrderBreakdowns(
                validated.startDate(), validated.endDate(), validated.productQuery(),
                ORDER_BREAKDOWN_LIMIT + 1));

        ProductionFlowCalendarSummaryRowVO safeCalendar =
                calendar == null ? new ProductionFlowCalendarSummaryRowVO() : calendar;
        ProductionFlowCohortSummaryRowVO safeCohort =
                cohort == null ? new ProductionFlowCohortSummaryRowVO() : cohort;
        ProductionFlowOutputQualityRowVO safeOutputQuality =
                outputQuality == null ? new ProductionFlowOutputQualityRowVO() : outputQuality;

        boolean orderBreakdownTruncated = queriedOrders.size() > ORDER_BREAKDOWN_LIMIT;
        List<RegisteredReportProductionFlowOrderBreakdownVO> orderBreakdowns = queriedOrders.stream()
                .limit(ORDER_BREAKDOWN_LIMIT)
                .map(ProductionInputOutputRegisteredReportService::toOrderBreakdown)
                .toList();

        Map<LocalDate, ProductionFlowDailyInputRowVO> inputByDate = new LinkedHashMap<>();
        for (ProductionFlowDailyInputRowVO row : inputRows) {
            if (row != null && row.getBusinessDate() != null) {
                inputByDate.put(row.getBusinessDate(), row);
            }
        }
        Map<LocalDate, ProductionFlowDailyOutputRowVO> outputByDate = new LinkedHashMap<>();
        for (ProductionFlowDailyOutputRowVO row : outputRows) {
            if (row != null && row.getBusinessDate() != null) {
                outputByDate.put(row.getBusinessDate(), row);
            }
        }
        List<RegisteredReportProductionFlowDailyPointVO> dailySeries = new ArrayList<>();
        for (LocalDate day = validated.startDate(); !day.isAfter(validated.endDate()); day = day.plusDays(1)) {
            ProductionFlowDailyInputRowVO input = inputByDate.get(day);
            ProductionFlowDailyOutputRowVO output = outputByDate.get(day);
            dailySeries.add(RegisteredReportProductionFlowDailyPointVO.builder()
                    .businessDate(day)
                    .materialInputRecordCount(intValue(input == null ? null : input.getInputRecordCount()))
                    .materialInputOrderCount(intValue(input == null ? null : input.getInputOrderCount()))
                    .materialInputPalletCount(intValue(input == null ? null : input.getInputPalletCount()))
                    .materialInputTotalPieces(intValue(input == null ? null : input.getInputTotalPieces()))
                    .materialInputWeightKg(decimalValue(input == null ? null : input.getInputWeightKg()))
                    .stableOutputRecordCount(intValue(output == null ? null : output.getOutputRecordCount()))
                    .stableOutputOrderCount(intValue(output == null ? null : output.getOutputOrderCount()))
                    .stableOutputTotalPieces(intValue(output == null ? null : output.getOutputTotalPieces()))
                    .stableOutputWeightKg(decimalValue(output == null ? null : output.getOutputWeightKg()))
                    .build());
        }

        int completedOrdersMissingInput = intValue(safeCohort.getCompletedOrdersMissingInputCount());
        int completedOrdersMissingOutput = intValue(safeCohort.getCompletedOrdersMissingOutputCount());
        int rowsMissingWeight = intValue(safeCalendar.getRowsMissingWeight());
        int rowsMissingConversion = intValue(safeCalendar.getRowsMissingPieceConversion());
        int draftExcluded = intValue(safeOutputQuality.getDraftOutputExcludedCount());
        int canceledExcluded = intValue(safeOutputQuality.getCanceledOutputExcludedCount());
        int crossDayInbound = intValue(safeOutputQuality.getCrossDayInboundCount());
        int unattributedOrders = validated.productQuery() == null
                ? 0
                : intValue(safeOutputQuality.getUnattributedOrderCount());

        List<String> qualityNotes = new ArrayList<>();
        if (completedOrdersMissingInput > 0) {
            qualityNotes.add(completedOrdersMissingInput
                    + " 个已完成订单缺少已登记投入事实，投入合计不能代表全部已完成订单。");
        }
        if (completedOrdersMissingOutput > 0) {
            qualityNotes.add(completedOrdersMissingOutput
                    + " 个已完成订单缺少稳定登记产出，产出合计不能代表这些订单。");
        }
        if (rowsMissingWeight > 0) {
            qualityNotes.add(rowsMissingWeight + " 条投入或产出记录缺少重量，重量合计未包含缺失值。");
        }
        if (rowsMissingConversion > 0) {
            qualityNotes.add(rowsMissingConversion + " 条投入或产出记录缺少件数换算。");
        }
        if (draftExcluded > 0 || canceledExcluded > 0) {
            qualityNotes.add("已排除 " + draftExcluded + " 条草稿产出和 "
                    + canceledExcluded + " 条已取消产出。");
        }
        if (crossDayInbound > 0) {
            qualityNotes.add(crossDayInbound
                    + " 个产出码在生产日期之后的其他日期入库，入库时间未并入生产日期产出。");
        }
        if (unattributedOrders > 0) {
            qualityNotes.add("产品筛选下有 " + unattributedOrders
                    + " 个无稳定产出的订单无法归属到具体产品，未进入产品订单队列。");
        }
        if (orderBreakdownTruncated) {
            qualityNotes.add("订单明细超过 " + ORDER_BREAKDOWN_LIMIT
                    + " 条，卡片和导出仅列出前 " + ORDER_BREAKDOWN_LIMIT + " 条；核心合计覆盖完整范围。");
        }

        boolean partial = completedOrdersMissingInput > 0
                || completedOrdersMissingOutput > 0
                || rowsMissingWeight > 0
                || rowsMissingConversion > 0
                || unattributedOrders > 0
                || orderBreakdownTruncated;

        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("businessDate", dateRangeLabel(validated.startDate(), validated.endDate()));
        filters.put("productScope", validated.productQuery() == null
                ? "全部产品"
                : "稳定登记产出产品名称包含“" + validated.productQuery() + "”");
        filters.put("calendarInputTime", "实际领料时间（北京时间）");
        filters.put("calendarOutputTime", "产出生产日期");

        LocalDateTime latestRecordAt = latestRecordAt(inputRows, outputRows, queriedOrders);

        return RegisteredReportRunVO.builder()
                .dataScope("REGISTERED_PRODUCTION_INPUT_OUTPUT_FLOW")
                .reportRunId("report_run_" + UUID.randomUUID().toString()
                        .replace("-", "").substring(0, 16))
                .reportDefinitionId(REPORT_ID)
                .reportVersion(REPORT_VERSION)
                .reportName("生产领料—登记产出趋势")
                .metricDefinitionVersion(METRIC_DEFINITION_VERSION)
                .startDate(validated.startDate())
                .endDate(validated.endDate())
                .dateRangeLabel(dateRangeLabel(validated.startDate(), validated.endDate()))
                .dataAsOf(LocalDateTime.now(BUSINESS_ZONE))
                .latestRecordAt(latestRecordAt)
                .filtersApplied(filters)
                .seriesGranularity("DAY")
                .productionFlowMetrics(toMetrics(safeCalendar, safeCohort))
                .productionFlowDailySeries(List.copyOf(dailySeries))
                .productionFlowOrderBreakdowns(orderBreakdowns)
                .dataQuality(RegisteredReportDataQualityVO.builder()
                        .partial(partial)
                        .rowsMissingWeight(rowsMissingWeight)
                        .rowsMissingPieceConversion(rowsMissingConversion)
                        .rowsMissingProductName(0)
                        .completedOrderCount(intValue(safeCohort.getCompletedOrderCount()))
                        .completedOrdersWithInputCount(
                                intValue(safeCohort.getCompletedOrdersWithInputCount()))
                        .completedOrdersMissingInputCount(completedOrdersMissingInput)
                        .completedOrdersWithStableOutputCount(
                                intValue(safeCohort.getCompletedOrdersWithStableOutputCount()))
                        .completedOrdersMissingOutputCount(completedOrdersMissingOutput)
                        .draftOutputExcludedCount(draftExcluded)
                        .canceledOutputExcludedCount(canceledExcluded)
                        .crossDayInboundCount(crossDayInbound)
                        .unattributedOrderCount(unattributedOrders)
                        .orderBreakdownTruncated(orderBreakdownTruncated)
                        .notes(List.copyOf(qualityNotes))
                        .build())
                .limitations(List.of(
                        "“投入”仅表示已登记实际领料；半成品订单的煮糖批次仅在订单归属视图中按最终确认使用展示，不代表真实投料时刻。",
                        "每日领料按领料发生时间统计，每日产出按生产日期统计；两条序列不是同一订单队列的分子和分母。",
                        "本报表只展示现有领料与登记产出字段，不计算两条序列的比例、差额含义或未登记业务事实。",
                        "产品筛选依据稳定登记产出归属订单，不使用计划产出或模型推断尚无产出的订单产品。"
                ))
                .build();
    }

    private static RegisteredReportProductionFlowMetricsVO toMetrics(
            ProductionFlowCalendarSummaryRowVO calendar,
            ProductionFlowCohortSummaryRowVO cohort) {
        return RegisteredReportProductionFlowMetricsVO.builder()
                .materialInputRecordCount(intValue(calendar.getMaterialInputRecordCount()))
                .materialInputOrderCount(intValue(calendar.getMaterialInputOrderCount()))
                .materialInputPalletCount(intValue(calendar.getMaterialInputPalletCount()))
                .materialInputBoardCount(intValue(calendar.getMaterialInputBoardCount()))
                .materialInputLoosePieceCount(intValue(calendar.getMaterialInputLoosePieceCount()))
                .materialInputTotalPieces(intValue(calendar.getMaterialInputTotalPieces()))
                .materialInputWeightKg(decimalValue(calendar.getMaterialInputWeightKg()))
                .stableOutputRecordCount(intValue(calendar.getStableOutputRecordCount()))
                .stableOutputOrderCount(intValue(calendar.getStableOutputOrderCount()))
                .stableOutputBoardCount(intValue(calendar.getStableOutputBoardCount()))
                .stableOutputLoosePieceCount(intValue(calendar.getStableOutputLoosePieceCount()))
                .stableOutputTotalPieces(intValue(calendar.getStableOutputTotalPieces()))
                .stableOutputWeightKg(decimalValue(calendar.getStableOutputWeightKg()))
                .cohortOrderCount(intValue(cohort.getCohortOrderCount()))
                .completedOrderCount(intValue(cohort.getCompletedOrderCount()))
                .completedOrdersWithInputCount(intValue(cohort.getCompletedOrdersWithInputCount()))
                .completedOrdersMissingInputCount(intValue(cohort.getCompletedOrdersMissingInputCount()))
                .completedOrdersWithStableOutputCount(
                        intValue(cohort.getCompletedOrdersWithStableOutputCount()))
                .completedOrdersMissingOutputCount(intValue(cohort.getCompletedOrdersMissingOutputCount()))
                .ordersWithInputCount(intValue(cohort.getOrdersWithInputCount()))
                .ordersMissingInputCount(intValue(cohort.getOrdersMissingInputCount()))
                .ordersWithStableOutputCount(intValue(cohort.getOrdersWithStableOutputCount()))
                .ordersMissingOutputCount(intValue(cohort.getOrdersMissingOutputCount()))
                .cohortMaterialInputWeightKg(decimalValue(cohort.getCohortMaterialInputWeightKg()))
                .cohortBoilingInputWeightKg(decimalValue(cohort.getCohortBoilingInputWeightKg()))
                .cohortStableOutputWeightKg(decimalValue(cohort.getCohortStableOutputWeightKg()))
                .build();
    }

    private static RegisteredReportProductionFlowOrderBreakdownVO toOrderBreakdown(
            ProductionFlowOrderRowVO row) {
        boolean semi = "SEMI".equalsIgnoreCase(row.getOrderType());
        int inputRecordCount = semi
                ? intValue(row.getBoilingInputUsageCount())
                : intValue(row.getMaterialInputRecordCount());
        int inputPalletCount = semi ? 0 : intValue(row.getMaterialInputPalletCount());
        int inputTotalPieces = semi ? 0 : intValue(row.getMaterialInputTotalPieces());
        BigDecimal inputWeight = semi
                ? decimalValue(row.getBoilingInputWeightKg())
                : decimalValue(row.getMaterialInputWeightKg());
        int outputRecordCount = intValue(row.getStableOutputRecordCount());
        boolean completed = "COMPLETED".equalsIgnoreCase(row.getOrderStatus());
        String completenessLabel;
        if (inputRecordCount == 0 && outputRecordCount == 0) {
            completenessLabel = completed ? "已完成但缺投入和产出事实" : "进行中，暂无投入和产出事实";
        } else if (inputRecordCount == 0) {
            completenessLabel = completed ? "已完成但缺投入事实" : "进行中，暂无投入事实";
        } else if (outputRecordCount == 0) {
            completenessLabel = completed ? "已完成但缺产出事实" : "进行中，暂无稳定产出";
        } else {
            completenessLabel = completed ? "投入、产出事实齐全" : "进行中，已有投入和产出事实";
        }
        return RegisteredReportProductionFlowOrderBreakdownVO.builder()
                .orderNo(safeText(row.getOrderNo(), "订单号未登记"))
                .orderTypeLabel(orderTypeLabel(row.getOrderType()))
                .orderStatusLabel(orderStatusLabel(row.getOrderStatus()))
                .productionDate(row.getProductionDate())
                .inputSourceLabel(semi ? "煮糖批次确认使用" : "实际领料")
                .inputRecordCount(inputRecordCount)
                .inputPalletCount(inputPalletCount)
                .inputTotalPieces(inputTotalPieces)
                .inputWeightKg(inputWeight)
                .stableOutputRecordCount(outputRecordCount)
                .stableOutputTotalPieces(intValue(row.getStableOutputTotalPieces()))
                .stableOutputWeightKg(decimalValue(row.getStableOutputWeightKg()))
                .outputProductNames(safeText(row.getOutputProductNames(), "暂无稳定登记产出"))
                .completenessLabel(completenessLabel)
                .build();
    }

    private static LocalDateTime latestRecordAt(
            List<ProductionFlowDailyInputRowVO> inputs,
            List<ProductionFlowDailyOutputRowVO> outputs,
            List<ProductionFlowOrderRowVO> orders) {
        LocalDateTime latest = null;
        for (ProductionFlowDailyInputRowVO row : inputs) {
            latest = later(latest, row == null ? null : row.getLatestRecordedAt());
        }
        for (ProductionFlowDailyOutputRowVO row : outputs) {
            latest = later(latest, row == null ? null : row.getLatestRecordedAt());
        }
        for (ProductionFlowOrderRowVO row : orders) {
            latest = later(latest, row == null ? null : row.getLatestRecordedAt());
        }
        return latest;
    }

    private ValidatedQuery validate(RegisteredReportRunQueryDTO query) {
        if (query == null) {
            throw new BusinessException(400, "报表查询条件不能为空");
        }
        String reportId = trimToNull(query.getReportDefinitionId());
        if (!REPORT_ID.equals(reportId)) {
            throw new BusinessException(400, "当前报表定义不受支持");
        }
        if (query.getReportVersion() == null || query.getReportVersion() != REPORT_VERSION) {
            throw new BusinessException(400, "当前报表版本不受支持");
        }
        if (query.getStartDate() == null || query.getEndDate() == null) {
            throw new BusinessException(400, "报表开始日期和结束日期不能为空");
        }
        if (query.getStartDate().isAfter(query.getEndDate())) {
            throw new BusinessException(400, "报表开始日期不能晚于结束日期");
        }
        long rangeDays = ChronoUnit.DAYS.between(query.getStartDate(), query.getEndDate()) + 1;
        if (rangeDays > MAX_RANGE_DAYS) {
            throw new BusinessException(400, "生产领料—登记产出趋势单次最多查询 366 天");
        }
        String productQuery = trimToNull(query.getProductQuery());
        if (productQuery != null && productQuery.length() > 100) {
            throw new BusinessException(400, "产品筛选条件不能超过 100 个字符");
        }
        if (trimToNull(query.getMetricKey()) != null) {
            throw new BusinessException(400, "生产领料—登记产出趋势不支持化验指标参数");
        }
        return new ValidatedQuery(query.getStartDate(), query.getEndDate(), productQuery);
    }

    private static String orderTypeLabel(String value) {
        return switch (value == null ? "" : value.trim().toUpperCase()) {
            case "SEMI" -> "半成品生产";
            case "FINISH" -> "成品生产";
            default -> "订单类型未登记";
        };
    }

    private static String orderStatusLabel(String value) {
        return switch (value == null ? "" : value.trim().toUpperCase()) {
            case "DRAFT" -> "草稿";
            case "ISSUED" -> "已下发";
            case "PREPRINTED" -> "已预打印";
            case "WAIT_INBOUND" -> "待入库";
            case "COMPLETED" -> "已完成";
            case "CANCELED" -> "已取消";
            default -> "状态未登记";
        };
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String safeText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private static String dateRangeLabel(LocalDate startDate, LocalDate endDate) {
        return startDate.equals(endDate) ? startDate.toString() : startDate + " 至 " + endDate;
    }

    private static int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal decimalValue(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static LocalDateTime later(LocalDateTime current, LocalDateTime candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isAfter(current) ? candidate : current;
    }

    private static <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private record ValidatedQuery(LocalDate startDate, LocalDate endDate, String productQuery) {
    }
}

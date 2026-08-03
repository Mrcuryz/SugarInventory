package com.Laibin.SugarInventory.analytics.service.impl;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportDailyPointVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportCurrentInventoryMetricsVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportDataQualityVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportMetricsVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportOperationsOverviewVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportProductBreakdownVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;
import com.Laibin.SugarInventory.analytics.service.RegisteredReportService;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.InventoryDistributionQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PalletTaskAgentQueryDTO;
import com.Laibin.SugarInventory.domain.vo.InventoryDistributionVO;
import com.Laibin.SugarInventory.domain.vo.PalletTasksAgentVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionReportOutputRowVO;
import com.Laibin.SugarInventory.production.mapper.ProductionOrderOutputMapper;
import com.Laibin.SugarInventory.service.InventoryDistributionService;
import com.Laibin.SugarInventory.service.LogisticsAgentReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegisteredReportServiceImpl implements RegisteredReportService {
    public static final String DAILY_PRODUCTION_REPORT_ID = "daily_production_overview_v1";
    public static final int DAILY_PRODUCTION_REPORT_VERSION = 1;
    public static final String METRIC_DEFINITION_VERSION = "production_registered_output_v1";
    public static final String TODAY_OPERATIONS_OVERVIEW_REPORT_ID = "today_operations_overview_v1";
    public static final int TODAY_OPERATIONS_OVERVIEW_REPORT_VERSION = 1;
    public static final String TODAY_OPERATIONS_METRIC_DEFINITION_VERSION =
            "today_registered_operations_snapshot_v1";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_RANGE_DAYS = 31;

    private final ProductionOrderOutputMapper outputMapper;
    private final QualityAssayRegisteredReportService qualityAssayReportService;
    private final QualityMetricRegisteredReportService qualityMetricReportService;
    private final ProductionInputOutputRegisteredReportService productionInputOutputReportService;
    private final PalletTaskCycleRegisteredReportService palletTaskCycleReportService;
    private final InventoryTrendRegisteredReportService inventoryTrendReportService;
    private final InventoryDistributionService inventoryDistributionService;
    private final LogisticsAgentReadService logisticsAgentReadService;
    private final RegisteredReportComparisonAssembler comparisonAssembler;

    @Override
    @PreAuthorize("""
            (#query != null and #query.reportDefinitionId == 'daily_production_overview_v1'
                and hasAuthority('production:order:view'))
            or
            (#query != null and #query.reportDefinitionId == 'quality_assay_result_trend_v1'
                and hasAuthority('assay:view'))
            or
            (#query != null and #query.reportDefinitionId == 'quality_metric_trend_v1'
                and hasAuthority('assay:view'))
            or
            (#query != null and #query.reportDefinitionId == 'production_input_output_flow_v1'
                and hasAuthority('production:order:view')
                and hasAuthority('production:material:view'))
            or
            (#query != null and #query.reportDefinitionId == 'pallet_task_cycle_time_v1'
                and hasAuthority('task:view'))
            or
            (#query != null and #query.reportDefinitionId == 'inventory_level_trend_v1'
                and hasAuthority('inventory:view'))
            or
            (#query != null and #query.reportDefinitionId == 'today_operations_overview_v1'
                and hasAuthority('production:order:view')
                and hasAuthority('production:material:view')
                and hasAuthority('assay:view')
                and hasAuthority('inventory:view')
                and hasAuthority('task:view'))
            """)
    public RegisteredReportRunVO run(RegisteredReportRunQueryDTO query) {
        RegisteredReportRunVO current = runSingle(query);
        return comparisonAssembler.attachComparison(query, current, this::runSingle);
    }

    private RegisteredReportRunVO runSingle(RegisteredReportRunQueryDTO query) {
        if (query != null
                && TODAY_OPERATIONS_OVERVIEW_REPORT_ID.equals(
                trimToNull(query.getReportDefinitionId()))) {
            return runTodayOperationsOverview(query);
        }
        if (query != null
                && InventoryTrendRegisteredReportService.REPORT_ID.equals(
                trimToNull(query.getReportDefinitionId()))) {
            return inventoryTrendReportService.run(query);
        }
        if (query != null
                && PalletTaskCycleRegisteredReportService.REPORT_ID.equals(
                trimToNull(query.getReportDefinitionId()))) {
            return palletTaskCycleReportService.run(query);
        }
        if (query != null
                && ProductionInputOutputRegisteredReportService.REPORT_ID.equals(
                trimToNull(query.getReportDefinitionId()))) {
            return productionInputOutputReportService.run(query);
        }
        if (query != null
                && QualityMetricRegisteredReportService.REPORT_ID.equals(
                trimToNull(query.getReportDefinitionId()))) {
            return qualityMetricReportService.run(query);
        }
        if (query != null
                && QualityAssayRegisteredReportService.REPORT_ID.equals(
                trimToNull(query.getReportDefinitionId()))) {
            return qualityAssayReportService.run(query);
        }
        ValidatedQuery validated = validate(query);
        List<ProductionReportOutputRowVO> queriedRows = outputMapper.listRegisteredOutputsForReport(
                validated.startDate(), validated.endDate(), validated.productQuery());
        List<ProductionReportOutputRowVO> rows = queriedRows == null ? List.of() : queriedRows;

        MutableAggregate total = new MutableAggregate();
        Map<LocalDate, MutableAggregate> daily = new LinkedHashMap<>();
        for (LocalDate day = validated.startDate(); !day.isAfter(validated.endDate()); day = day.plusDays(1)) {
            daily.put(day, new MutableAggregate());
        }
        Map<String, MutableProductAggregate> products = new LinkedHashMap<>();
        int rowsMissingWeight = 0;
        int rowsMissingPieceConversion = 0;
        int rowsMissingProductName = 0;
        LocalDateTime latestRecordAt = null;

        for (ProductionReportOutputRowVO row : rows) {
            total.add(row);
            MutableAggregate dailyAggregate = daily.get(row.getProductionDate());
            if (dailyAggregate != null) {
                dailyAggregate.add(row);
            }
            String productName = cleanProductName(row.getProductName());
            if (row.getProductName() == null || row.getProductName().isBlank()) {
                rowsMissingProductName++;
            }
            String productKey = String.valueOf(row.getProductId()) + "|" + productName + "|"
                    + String.valueOf(row.getProductStatus());
            products.computeIfAbsent(productKey,
                            ignored -> new MutableProductAggregate(productName, row.getProductStatus()))
                    .add(row);

            if (row.getTotalWeight() == null) {
                rowsMissingWeight++;
            }
            if (row.getTotalPieces() == null) {
                rowsMissingPieceConversion++;
            }
            LocalDateTime recordedAt = row.getUpdatedAt() != null ? row.getUpdatedAt() : row.getCreatedAt();
            if (recordedAt != null && (latestRecordAt == null || recordedAt.isAfter(latestRecordAt))) {
                latestRecordAt = recordedAt;
            }
        }

        List<String> qualityNotes = new ArrayList<>();
        if (rowsMissingWeight > 0) {
            qualityNotes.add(rowsMissingWeight + " 条产出记录缺少重量，重量合计未包含这些记录。");
        }
        if (rowsMissingPieceConversion > 0) {
            qualityNotes.add(rowsMissingPieceConversion + " 条产出记录缺少折算总件数，件数合计未包含这些记录。");
        }
        if (rowsMissingProductName > 0) {
            qualityNotes.add(rowsMissingProductName + " 条产出记录缺少产品名称，已归入“产品名称未登记”。");
        }

        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("businessDate", dateRangeLabel(validated.startDate(), validated.endDate()));
        filters.put("productScope", validated.productQuery() == null
                ? "全部产品"
                : "产品名称包含“" + validated.productQuery() + "”");

        return RegisteredReportRunVO.builder()
                .dataScope("REGISTERED_PRODUCTION_OUTPUT")
                .reportRunId("report_run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .reportDefinitionId(DAILY_PRODUCTION_REPORT_ID)
                .reportVersion(DAILY_PRODUCTION_REPORT_VERSION)
                .reportName("生产登记产出日报")
                .metricDefinitionVersion(METRIC_DEFINITION_VERSION)
                .startDate(validated.startDate())
                .endDate(validated.endDate())
                .dateRangeLabel(dateRangeLabel(validated.startDate(), validated.endDate()))
                .dataAsOf(LocalDateTime.now(BUSINESS_ZONE))
                .latestRecordAt(latestRecordAt)
                .filtersApplied(filters)
                .metrics(total.toMetrics())
                .dailySeries(daily.entrySet().stream()
                        .map(entry -> entry.getValue().toDailyPoint(entry.getKey()))
                        .toList())
                .productBreakdowns(products.values().stream()
                        .map(MutableProductAggregate::toBreakdown)
                        .toList())
                .dataQuality(RegisteredReportDataQualityVO.builder()
                        .partial(!qualityNotes.isEmpty())
                        .rowsMissingWeight(rowsMissingWeight)
                        .rowsMissingPieceConversion(rowsMissingPieceConversion)
                        .rowsMissingProductName(rowsMissingProductName)
                        .notes(List.copyOf(qualityNotes))
                        .build())
                .limitations(List.of(
                        "产量仅统计状态为已绑定、部分入库或已入库的稳定登记产出；草稿和已取消记录不计入。",
                        "标签生成、二维码绑定和入库数量仅作为后续流程进度，不计入产量。",
                        "当前数据库没有正式日产计划版本、班次归属及返工、报废或损耗事实，因此不计算计划达成率、班次对比或良率。"
                ))
                .build();
    }

    private RegisteredReportRunVO runTodayOperationsOverview(
            RegisteredReportRunQueryDTO query) {
        LocalDate businessDate = validateTodayOperationsOverview(query);
        RegisteredReportRunVO production = runSingle(childQuery(
                DAILY_PRODUCTION_REPORT_ID, businessDate));
        RegisteredReportRunVO quality = qualityAssayReportService.run(childQuery(
                QualityAssayRegisteredReportService.REPORT_ID, businessDate));
        RegisteredReportRunVO productionFlow = productionInputOutputReportService.run(childQuery(
                ProductionInputOutputRegisteredReportService.REPORT_ID, businessDate));
        RegisteredReportRunVO palletTasks = palletTaskCycleReportService.run(childQuery(
                PalletTaskCycleRegisteredReportService.REPORT_ID, businessDate));

        InventoryDistributionVO inventory = inventoryDistributionService.getDistribution(
                currentInventoryQuery());
        PalletTaskAgentQueryDTO pendingQuery = new PalletTaskAgentQueryDTO();
        pendingQuery.setStatus("PENDING");
        pendingQuery.setPage(1);
        pendingQuery.setSize(1);
        PalletTasksAgentVO pendingTasks = logisticsAgentReadService.queryPalletTasks(pendingQuery);

        List<String> qualityNotes = new ArrayList<>();
        appendQualityNotes(qualityNotes, "生产产出", production.getDataQuality());
        appendQualityNotes(qualityNotes, "化验", quality.getDataQuality());
        appendQualityNotes(qualityNotes, "生产领用与产出", productionFlow.getDataQuality());
        appendQualityNotes(qualityNotes, "托盘任务", palletTasks.getDataQuality());

        LocalDateTime dataAsOf = LocalDateTime.now(BUSINESS_ZONE);
        LocalDateTime latestRecordAt = latest(
                production.getLatestRecordAt(),
                quality.getLatestRecordAt(),
                productionFlow.getLatestRecordAt(),
                palletTasks.getLatestRecordAt(),
                latestPendingCreatedAt(pendingTasks));
        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("businessDate", businessDate.toString());
        filters.put("productScope", "全部产品");
        filters.put("taskScope", "当日创建任务与当前待处理任务");
        filters.put("inventoryScope", "当前全部在库库存");

        RegisteredReportCurrentInventoryMetricsVO currentInventory =
                RegisteredReportCurrentInventoryMetricsVO.builder()
                        .productCount(inventory == null ? 0 : inventory.getProductCount())
                        .warehouseCount(inventory == null ? 0 : inventory.getWarehouseCount())
                        .palletCount(inventory == null ? 0 : inventory.getPalletCount())
                        .totalEquivalentPieces(
                                inventory == null ? 0 : inventory.getTotalEquivalentPieces())
                        .totalStockText(inventory == null ? null : inventory.getTotalStockText())
                        .totalWeightText(inventory == null ? null : inventory.getTotalWeightText())
                        .build();

        return RegisteredReportRunVO.builder()
                .dataScope("REGISTERED_TODAY_OPERATIONS_SNAPSHOT")
                .reportRunId("report_run_"
                        + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .reportDefinitionId(TODAY_OPERATIONS_OVERVIEW_REPORT_ID)
                .reportVersion(TODAY_OPERATIONS_OVERVIEW_REPORT_VERSION)
                .reportName("今日运营概览")
                .metricDefinitionVersion(TODAY_OPERATIONS_METRIC_DEFINITION_VERSION)
                .startDate(businessDate)
                .endDate(businessDate)
                .dateRangeLabel(businessDate.toString())
                .dataAsOf(dataAsOf)
                .latestRecordAt(latestRecordAt)
                .filtersApplied(filters)
                .operationsOverview(RegisteredReportOperationsOverviewVO.builder()
                        .businessDate(businessDate)
                        .productionOutput(production.getMetrics())
                        .assayQuality(quality.getQualityMetrics())
                        .productionFlow(productionFlow.getProductionFlowMetrics())
                        .currentInventory(currentInventory)
                        .todayPalletTasks(palletTasks.getPalletTaskCycleMetrics())
                        .currentPendingTaskCount(
                                pendingTasks == null ? 0 : pendingTasks.getTotal())
                        .build())
                .dataQuality(RegisteredReportDataQualityVO.builder()
                        .partial(!qualityNotes.isEmpty())
                        .notes(List.copyOf(qualityNotes))
                        .build())
                .limitations(List.of(
                        "概览只支持北京时间今天；产出和化验按业务日期统计，当前库存与当前待处理任务是报表生成时快照。",
                        "确认领用按领料发生时间统计，稳定登记产出按生产日期统计，两条序列独立展示，不能直接相除。",
                        "当前库存只表示生成时的在库水平，不表示今天的库存变化；库存变化必须使用已通过发布门禁的库存趋势报表。",
                        "当日托盘任务按创建日期形成队列；当前待处理任务是全量当前状态，两者不是同一范围。",
                        "数据库没有正式日产计划、返工、报废、损耗或 SLA 事实，本报表不计算计划达成率、良率、收率、损耗率、逾期或预测。"
                ))
                .build();
    }

    private static LocalDate validateTodayOperationsOverview(
            RegisteredReportRunQueryDTO query) {
        if (query.getReportVersion() == null
                || query.getReportVersion() != TODAY_OPERATIONS_OVERVIEW_REPORT_VERSION) {
            throw new BusinessException(400, "当前报表版本不受支持");
        }
        if (query.getStartDate() == null || query.getEndDate() == null
                || !query.getStartDate().equals(query.getEndDate())) {
            throw new BusinessException(400, "今日运营概览只支持同一个业务日期");
        }
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        if (!today.equals(query.getStartDate())) {
            throw new BusinessException(400, "今日运营概览只支持查询北京时间今天");
        }
        if (trimToNull(query.getProductQuery()) != null
                || trimToNull(query.getMetricKey()) != null
                || trimToNull(query.getTaskType()) != null) {
            throw new BusinessException(400, "今日运营概览首版只支持全部产品和全部任务");
        }
        if (trimToNull(query.getComparisonMode()) != null
                || query.getComparisonStartDate() != null
                || query.getComparisonEndDate() != null) {
            throw new BusinessException(400, "今日运营概览不支持跨期比较");
        }
        return today;
    }

    private static RegisteredReportRunQueryDTO childQuery(
            String definitionId,
            LocalDate businessDate) {
        RegisteredReportRunQueryDTO child = new RegisteredReportRunQueryDTO();
        child.setReportDefinitionId(definitionId);
        child.setReportVersion(1);
        child.setStartDate(businessDate);
        child.setEndDate(businessDate);
        return child;
    }

    private static InventoryDistributionQueryDTO currentInventoryQuery() {
        InventoryDistributionQueryDTO query = new InventoryDistributionQueryDTO();
        InventoryDistributionQueryDTO.ProductScope productScope =
                new InventoryDistributionQueryDTO.ProductScope();
        productScope.setType("ALL");
        InventoryDistributionQueryDTO.WarehouseScope warehouseScope =
                new InventoryDistributionQueryDTO.WarehouseScope();
        warehouseScope.setType("ALL");
        query.setProductScope(productScope);
        query.setWarehouseScope(warehouseScope);
        query.setGroupBy("product");
        query.setLimit(1);
        return query;
    }

    private static void appendQualityNotes(
            List<String> target,
            String section,
            RegisteredReportDataQualityVO quality) {
        if (quality == null || quality.getNotes() == null) {
            return;
        }
        quality.getNotes().stream()
                .filter(value -> value != null && !value.isBlank())
                .forEach(value -> target.add(section + "：" + value.trim()));
    }

    private static LocalDateTime latestPendingCreatedAt(PalletTasksAgentVO pendingTasks) {
        if (pendingTasks == null || pendingTasks.getRecords() == null) {
            return null;
        }
        return pendingTasks.getRecords().stream()
                .map(PalletTasksAgentVO.Row::getCreatedAt)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private static LocalDateTime latest(LocalDateTime... values) {
        LocalDateTime result = null;
        for (LocalDateTime value : values) {
            if (value != null && (result == null || value.isAfter(result))) {
                result = value;
            }
        }
        return result;
    }

    private ValidatedQuery validate(RegisteredReportRunQueryDTO query) {
        if (query == null) {
            throw new BusinessException(400, "报表查询条件不能为空");
        }
        String definitionId = trimToNull(query.getReportDefinitionId());
        if (!DAILY_PRODUCTION_REPORT_ID.equals(definitionId)) {
            throw new BusinessException(400, "当前仅支持生产登记产出日报");
        }
        if (query.getReportVersion() == null
                || query.getReportVersion() != DAILY_PRODUCTION_REPORT_VERSION) {
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
            throw new BusinessException(400, "生产登记产出日报单次最多查询 31 天");
        }
        String productQuery = trimToNull(query.getProductQuery());
        if (productQuery != null && productQuery.length() > 100) {
            throw new BusinessException(400, "产品筛选条件不能超过 100 个字符");
        }
        return new ValidatedQuery(query.getStartDate(), query.getEndDate(), productQuery);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String cleanProductName(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? "产品名称未登记" : trimmed;
    }

    private static String dateRangeLabel(LocalDate startDate, LocalDate endDate) {
        return startDate.equals(endDate) ? startDate.toString() : startDate + " 至 " + endDate;
    }

    private record ValidatedQuery(LocalDate startDate, LocalDate endDate, String productQuery) {
    }

    private static class MutableAggregate {
        private int outputRecordCount;
        private final Set<Long> productionOrderIds = new LinkedHashSet<>();
        private BigDecimal totalWeightKg = BigDecimal.ZERO;
        private int totalBoardCount;
        private int loosePieceCount;
        private int totalPieces;
        private int requiredQrCount;
        private int boundQrCount;
        private int inboundQrCount;

        void add(ProductionReportOutputRowVO row) {
            outputRecordCount++;
            if (row.getProductionOrderId() != null) {
                productionOrderIds.add(row.getProductionOrderId());
            }
            totalWeightKg = totalWeightKg.add(orZero(row.getTotalWeight()));
            totalBoardCount += orZero(row.getBoardCount());
            loosePieceCount += orZero(row.getPieceCount());
            totalPieces += orZero(row.getTotalPieces());
            requiredQrCount += orZero(row.getRequiredQrCount());
            boundQrCount += orZero(row.getBoundQrCount());
            inboundQrCount += orZero(row.getInboundQrCount());
        }

        RegisteredReportMetricsVO toMetrics() {
            return RegisteredReportMetricsVO.builder()
                    .outputRecordCount(outputRecordCount)
                    .productionOrderCount(productionOrderIds.size())
                    .totalWeightKg(totalWeightKg)
                    .totalBoardCount(totalBoardCount)
                    .loosePieceCount(loosePieceCount)
                    .totalPieces(totalPieces)
                    .requiredQrCount(requiredQrCount)
                    .boundQrCount(boundQrCount)
                    .inboundQrCount(inboundQrCount)
                    .build();
        }

        RegisteredReportDailyPointVO toDailyPoint(LocalDate businessDate) {
            return RegisteredReportDailyPointVO.builder()
                    .businessDate(businessDate)
                    .outputRecordCount(outputRecordCount)
                    .productionOrderCount(productionOrderIds.size())
                    .totalWeightKg(totalWeightKg)
                    .totalBoardCount(totalBoardCount)
                    .loosePieceCount(loosePieceCount)
                    .totalPieces(totalPieces)
                    .build();
        }

        private static BigDecimal orZero(BigDecimal value) {
            return value == null ? BigDecimal.ZERO : value;
        }

        private static int orZero(Integer value) {
            return value == null ? 0 : value;
        }
    }

    private static final class MutableProductAggregate extends MutableAggregate {
        private final String productName;
        private final String productStatus;

        private MutableProductAggregate(String productName, String productStatus) {
            this.productName = productName;
            this.productStatus = productStatus;
        }

        RegisteredReportProductBreakdownVO toBreakdown() {
            RegisteredReportMetricsVO metrics = toMetrics();
            return RegisteredReportProductBreakdownVO.builder()
                    .productName(productName)
                    .productStatus(productStatus)
                    .outputRecordCount(metrics.getOutputRecordCount())
                    .productionOrderCount(metrics.getProductionOrderCount())
                    .totalWeightKg(metrics.getTotalWeightKg())
                    .totalBoardCount(metrics.getTotalBoardCount())
                    .loosePieceCount(metrics.getLoosePieceCount())
                    .totalPieces(metrics.getTotalPieces())
                    .build();
        }
    }
}

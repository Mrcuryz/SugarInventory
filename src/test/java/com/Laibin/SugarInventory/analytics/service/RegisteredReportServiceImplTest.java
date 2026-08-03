package com.Laibin.SugarInventory.analytics.service;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportDataQualityVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportPalletTaskCycleMetricsVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportProductionFlowMetricsVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportQualityMetricsVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;
import com.Laibin.SugarInventory.analytics.service.impl.QualityAssayRegisteredReportService;
import com.Laibin.SugarInventory.analytics.service.impl.QualityMetricRegisteredReportService;
import com.Laibin.SugarInventory.analytics.service.impl.ProductionInputOutputRegisteredReportService;
import com.Laibin.SugarInventory.analytics.service.impl.PalletTaskCycleRegisteredReportService;
import com.Laibin.SugarInventory.analytics.service.impl.InventoryTrendRegisteredReportService;
import com.Laibin.SugarInventory.analytics.service.impl.RegisteredReportServiceImpl;
import com.Laibin.SugarInventory.analytics.service.impl.RegisteredReportComparisonAssembler;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.vo.InventoryDistributionVO;
import com.Laibin.SugarInventory.domain.vo.PalletTasksAgentVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionReportOutputRowVO;
import com.Laibin.SugarInventory.production.mapper.ProductionOrderOutputMapper;
import com.Laibin.SugarInventory.service.InventoryDistributionService;
import com.Laibin.SugarInventory.service.LogisticsAgentReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegisteredReportServiceImplTest {
    private ProductionOrderOutputMapper outputMapper;
    private QualityAssayRegisteredReportService qualityAssayReportService;
    private QualityMetricRegisteredReportService qualityMetricReportService;
    private ProductionInputOutputRegisteredReportService productionInputOutputReportService;
    private PalletTaskCycleRegisteredReportService palletTaskCycleReportService;
    private InventoryTrendRegisteredReportService inventoryTrendReportService;
    private InventoryDistributionService inventoryDistributionService;
    private LogisticsAgentReadService logisticsAgentReadService;
    private RegisteredReportServiceImpl service;

    @BeforeEach
    void setUp() {
        outputMapper = mock(ProductionOrderOutputMapper.class);
        qualityAssayReportService = mock(QualityAssayRegisteredReportService.class);
        qualityMetricReportService = mock(QualityMetricRegisteredReportService.class);
        productionInputOutputReportService = mock(ProductionInputOutputRegisteredReportService.class);
        palletTaskCycleReportService = mock(PalletTaskCycleRegisteredReportService.class);
        inventoryTrendReportService = mock(InventoryTrendRegisteredReportService.class);
        inventoryDistributionService = mock(InventoryDistributionService.class);
        logisticsAgentReadService = mock(LogisticsAgentReadService.class);
        service = new RegisteredReportServiceImpl(
                outputMapper,
                qualityAssayReportService,
                qualityMetricReportService,
                productionInputOutputReportService,
                palletTaskCycleReportService,
                inventoryTrendReportService,
                inventoryDistributionService,
                logisticsAgentReadService,
                new RegisteredReportComparisonAssembler());
    }

    @Test
    void aggregatesRegisteredProductionOutputWithoutTreatingQrProgressAsProduction() {
        LocalDate day = LocalDate.of(2026, 7, 27);
        ProductionReportOutputRowVO first = row(
                11L, 1, "黄冰糖（袋）", day, 2, 3, 83,
                "2075.00", 3, 2, 1);
        ProductionReportOutputRowVO second = row(
                12L, 1, "黄冰糖（袋）", day, 1, 0, 40,
                "1000.00", 1, 1, 0);
        when(outputMapper.listRegisteredOutputsForReport(day, day, null))
                .thenReturn(List.of(first, second));

        var report = service.run(query(day, day, null));

        assertThat(report.getReportDefinitionId()).isEqualTo("daily_production_overview_v1");
        assertThat(report.getMetricDefinitionVersion()).isEqualTo("production_registered_output_v1");
        assertThat(report.getMetrics().getOutputRecordCount()).isEqualTo(2);
        assertThat(report.getMetrics().getProductionOrderCount()).isEqualTo(2);
        assertThat(report.getMetrics().getTotalWeightKg()).isEqualByComparingTo("3075.00");
        assertThat(report.getMetrics().getTotalBoardCount()).isEqualTo(3);
        assertThat(report.getMetrics().getLoosePieceCount()).isEqualTo(3);
        assertThat(report.getMetrics().getTotalPieces()).isEqualTo(123);
        assertThat(report.getMetrics().getRequiredQrCount()).isEqualTo(4);
        assertThat(report.getMetrics().getBoundQrCount()).isEqualTo(3);
        assertThat(report.getMetrics().getInboundQrCount()).isEqualTo(1);
        assertThat(report.getLimitations())
                .anyMatch(value -> value.contains("二维码绑定和入库数量") && value.contains("不计入产量"));
        assertThat(report.getProductBreakdowns()).singleElement().satisfies(product -> {
            assertThat(product.getProductName()).isEqualTo("黄冰糖（袋）");
            assertThat(product.getTotalWeightKg()).isEqualByComparingTo("3075.00");
        });
    }

    @Test
    void returnsZeroDailyPointsAndMakesMissingConversionsVisible() {
        LocalDate start = LocalDate.of(2026, 7, 26);
        LocalDate end = LocalDate.of(2026, 7, 27);
        ProductionReportOutputRowVO incomplete = row(
                11L, 1, null, end, 1, 0, null,
                null, 1, 0, 0);
        when(outputMapper.listRegisteredOutputsForReport(start, end, "黄冰糖"))
                .thenReturn(List.of(incomplete));

        var report = service.run(query(start, end, " 黄冰糖 "));

        assertThat(report.getDailySeries()).hasSize(2);
        assertThat(report.getDailySeries().get(0).getOutputRecordCount()).isZero();
        assertThat(report.getDailySeries().get(1).getOutputRecordCount()).isEqualTo(1);
        assertThat(report.getDataQuality().isPartial()).isTrue();
        assertThat(report.getDataQuality().getRowsMissingWeight()).isEqualTo(1);
        assertThat(report.getDataQuality().getRowsMissingPieceConversion()).isEqualTo(1);
        assertThat(report.getDataQuality().getRowsMissingProductName()).isEqualTo(1);
        assertThat(report.getFiltersApplied().get("productScope")).contains("黄冰糖");
    }

    @Test
    void rejectsUnknownDefinitionsAndOverlongRanges() {
        RegisteredReportRunQueryDTO unknown = query(
                LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 27), null);
        unknown.setReportDefinitionId("arbitrary_report");

        assertThatThrownBy(() -> service.run(unknown))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅支持");

        RegisteredReportRunQueryDTO tooLong = query(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 27), null);
        assertThatThrownBy(() -> service.run(tooLong))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("31 天");
    }

    @Test
    void composesTodayOverviewFromRegisteredFactsAndCurrentSnapshots() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        when(outputMapper.listRegisteredOutputsForReport(today, today, null))
                .thenReturn(List.of(row(
                        21L, 2, "白冰糖", today, 2, 0, 80,
                        "1980.00", 2, 2, 1)));
        when(qualityAssayReportService.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(RegisteredReportRunVO.builder()
                        .qualityMetrics(RegisteredReportQualityMetricsVO.builder()
                                .assayRecordCount(3)
                                .judgedRecordCount(2)
                                .passCount(2)
                                .noStandardCount(1)
                                .passRatePercent(new BigDecimal("100.00"))
                                .build())
                        .dataQuality(RegisteredReportDataQualityVO.builder().notes(List.of()).build())
                        .latestRecordAt(today.atTime(15, 0))
                        .build());
        when(productionInputOutputReportService.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(RegisteredReportRunVO.builder()
                        .productionFlowMetrics(RegisteredReportProductionFlowMetricsVO.builder()
                                .materialInputRecordCount(2)
                                .materialInputOrderCount(1)
                                .materialInputWeightKg(new BigDecimal("2000.00"))
                                .stableOutputRecordCount(1)
                                .stableOutputOrderCount(1)
                                .stableOutputWeightKg(new BigDecimal("1980.00"))
                                .build())
                        .dataQuality(RegisteredReportDataQualityVO.builder().notes(List.of()).build())
                        .latestRecordAt(today.atTime(15, 5))
                        .build());
        when(palletTaskCycleReportService.run(org.mockito.ArgumentMatchers.any()))
                .thenReturn(RegisteredReportRunVO.builder()
                        .palletTaskCycleMetrics(RegisteredReportPalletTaskCycleMetricsVO.builder()
                                .cohortTaskCount(4)
                                .completedTaskCount(2)
                                .inProgressTaskCount(1)
                                .canceledTaskCount(1)
                                .build())
                        .dataQuality(RegisteredReportDataQualityVO.builder().notes(List.of()).build())
                        .latestRecordAt(today.atTime(15, 10))
                        .build());
        when(inventoryDistributionService.getDistribution(org.mockito.ArgumentMatchers.any()))
                .thenReturn(InventoryDistributionVO.builder()
                        .productCount(5)
                        .warehouseCount(3)
                        .palletCount(12)
                        .totalEquivalentPieces(550)
                        .totalStockText("13 板 30 件")
                        .totalWeightText("13750 kg")
                        .groups(List.of())
                        .notes(List.of())
                        .build());
        when(logisticsAgentReadService.queryPalletTasks(org.mockito.ArgumentMatchers.any()))
                .thenReturn(PalletTasksAgentVO.builder()
                        .total(7)
                        .page(1)
                        .size(1)
                        .records(List.of(PalletTasksAgentVO.Row.builder()
                                .createdAt(today.atTime(15, 15))
                                .build()))
                        .limitations(List.of())
                        .build());

        RegisteredReportRunQueryDTO query = query(today, today, null);
        query.setReportDefinitionId(RegisteredReportServiceImpl.TODAY_OPERATIONS_OVERVIEW_REPORT_ID);
        RegisteredReportRunVO report = service.run(query);

        assertThat(report.getReportName()).isEqualTo("今日运营概览");
        assertThat(report.getDateRangeLabel()).isEqualTo(today.toString());
        assertThat(report.getOperationsOverview().getProductionOutput().getTotalWeightKg())
                .isEqualByComparingTo("1980.00");
        assertThat(report.getOperationsOverview().getAssayQuality().getAssayRecordCount())
                .isEqualTo(3);
        assertThat(report.getOperationsOverview().getProductionFlow().getMaterialInputWeightKg())
                .isEqualByComparingTo("2000.00");
        assertThat(report.getOperationsOverview().getCurrentInventory().getTotalEquivalentPieces())
                .isEqualTo(550);
        assertThat(report.getOperationsOverview().getTodayPalletTasks().getCohortTaskCount())
                .isEqualTo(4);
        assertThat(report.getOperationsOverview().getCurrentPendingTaskCount()).isEqualTo(7);
        assertThat(report.getLimitations())
                .anyMatch(value -> value.contains("不能直接相除"))
                .anyMatch(value -> value.contains("不计算计划达成率"));
    }

    @Test
    void rejectsHistoricalFilteredOrComparedTodayOverview() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        RegisteredReportRunQueryDTO historical = query(today.minusDays(1), today.minusDays(1), null);
        historical.setReportDefinitionId(RegisteredReportServiceImpl.TODAY_OPERATIONS_OVERVIEW_REPORT_ID);
        assertThatThrownBy(() -> service.run(historical))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("北京时间今天");

        RegisteredReportRunQueryDTO filtered = query(today, today, "黄冰糖");
        filtered.setReportDefinitionId(RegisteredReportServiceImpl.TODAY_OPERATIONS_OVERVIEW_REPORT_ID);
        assertThatThrownBy(() -> service.run(filtered))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("全部产品");

        RegisteredReportRunQueryDTO compared = query(today, today, null);
        compared.setReportDefinitionId(RegisteredReportServiceImpl.TODAY_OPERATIONS_OVERVIEW_REPORT_ID);
        compared.setComparisonMode("PREVIOUS_PERIOD");
        assertThatThrownBy(() -> service.run(compared))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持跨期比较");
    }

    private static RegisteredReportRunQueryDTO query(
            LocalDate startDate, LocalDate endDate, String productQuery) {
        RegisteredReportRunQueryDTO query = new RegisteredReportRunQueryDTO();
        query.setReportDefinitionId(RegisteredReportServiceImpl.DAILY_PRODUCTION_REPORT_ID);
        query.setReportVersion(RegisteredReportServiceImpl.DAILY_PRODUCTION_REPORT_VERSION);
        query.setStartDate(startDate);
        query.setEndDate(endDate);
        query.setProductQuery(productQuery);
        return query;
    }

    private static ProductionReportOutputRowVO row(
            Long orderId,
            Integer productId,
            String productName,
            LocalDate productionDate,
            Integer boardCount,
            Integer pieceCount,
            Integer totalPieces,
            String totalWeight,
            Integer requiredQrCount,
            Integer boundQrCount,
            Integer inboundQrCount) {
        ProductionReportOutputRowVO row = new ProductionReportOutputRowVO();
        row.setProductionOrderId(orderId);
        row.setProductId(productId);
        row.setProductName(productName);
        row.setProductStatus("成品");
        row.setProductionDate(productionDate);
        row.setBoardCount(boardCount);
        row.setPieceCount(pieceCount);
        row.setTotalPieces(totalPieces);
        row.setTotalWeight(totalWeight == null ? null : new BigDecimal(totalWeight));
        row.setRequiredQrCount(requiredQrCount);
        row.setBoundQrCount(boundQrCount);
        row.setInboundQrCount(inboundQrCount);
        row.setCreatedAt(LocalDateTime.of(productionDate, java.time.LocalTime.NOON));
        return row;
    }
}

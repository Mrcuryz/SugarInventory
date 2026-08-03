package com.Laibin.SugarInventory.analytics.service;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.vo.ProductionFlowCalendarSummaryRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.ProductionFlowCohortSummaryRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.ProductionFlowDailyInputRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.ProductionFlowDailyOutputRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.ProductionFlowOrderRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.ProductionFlowOutputQualityRowVO;
import com.Laibin.SugarInventory.analytics.mapper.ProductionInputOutputReportMapper;
import com.Laibin.SugarInventory.analytics.service.impl.ProductionInputOutputRegisteredReportService;
import com.Laibin.SugarInventory.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionInputOutputRegisteredReportServiceTest {
    private ProductionInputOutputReportMapper mapper;
    private ProductionInputOutputRegisteredReportService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProductionInputOutputReportMapper.class);
        service = new ProductionInputOutputRegisteredReportService(mapper);
    }

    @Test
    void returnsIndependentCalendarSeriesAndOrderCoverageWithoutYield() {
        LocalDate start = LocalDate.of(2026, 6, 30);
        LocalDate end = LocalDate.of(2026, 7, 1);
        when(mapper.selectCalendarSummary(start, end, null)).thenReturn(calendarSummary());
        when(mapper.selectCohortSummary(start, end, null)).thenReturn(cohortSummary());
        when(mapper.selectOutputQuality(start, end, null)).thenReturn(outputQuality());
        when(mapper.listDailyMaterialInputs(start, end, null))
                .thenReturn(List.of(dailyInput(start)));
        when(mapper.listDailyStableOutputs(start, end, null))
                .thenReturn(List.of(dailyOutput(end)));
        when(mapper.listCohortOrderBreakdowns(start, end, null, 201))
                .thenReturn(List.of(finishOrder(), semiOrder()));

        var report = service.run(query(start, end, null));

        assertThat(report.getReportDefinitionId()).isEqualTo("production_input_output_flow_v1");
        assertThat(report.getProductionFlowMetrics().getMaterialInputWeightKg())
                .isEqualByComparingTo("6840");
        assertThat(report.getProductionFlowMetrics().getStableOutputWeightKg())
                .isEqualByComparingTo("20265");
        assertThat(report.getProductionFlowMetrics().getCompletedOrdersMissingInputCount())
                .isEqualTo(5);
        assertThat(report.getProductionFlowDailySeries()).hasSize(2);
        assertThat(report.getProductionFlowDailySeries().get(0).getMaterialInputRecordCount())
                .isEqualTo(8);
        assertThat(report.getProductionFlowDailySeries().get(0).getStableOutputRecordCount())
                .isZero();
        assertThat(report.getProductionFlowDailySeries().get(1).getMaterialInputRecordCount())
                .isZero();
        assertThat(report.getProductionFlowDailySeries().get(1).getStableOutputRecordCount())
                .isEqualTo(11);
        assertThat(report.getProductionFlowOrderBreakdowns()).hasSize(2);
        assertThat(report.getProductionFlowOrderBreakdowns().get(0).getInputSourceLabel())
                .isEqualTo("实际领料");
        assertThat(report.getProductionFlowOrderBreakdowns().get(1).getInputSourceLabel())
                .isEqualTo("煮糖批次确认使用");
        assertThat(report.getDataQuality().isPartial()).isTrue();
        assertThat(report.getDataQuality().getDraftOutputExcludedCount()).isEqualTo(2);
        assertThat(report.getDataQuality().getCrossDayInboundCount()).isEqualTo(6);
        assertThat(report.getLimitations())
                .anyMatch(value -> value.contains("不计算两条序列的比例"));
    }

    @Test
    void explainsProductScopedOrdersWithoutStableOutputCannotBeAttributed() {
        LocalDate day = LocalDate.of(2026, 6, 30);
        String product = "黄冰糖（袋）";
        ProductionFlowCalendarSummaryRowVO calendar = new ProductionFlowCalendarSummaryRowVO();
        ProductionFlowCohortSummaryRowVO cohort = new ProductionFlowCohortSummaryRowVO();
        ProductionFlowOutputQualityRowVO quality = new ProductionFlowOutputQualityRowVO();
        quality.setUnattributedOrderCount(3);
        when(mapper.selectCalendarSummary(day, day, product)).thenReturn(calendar);
        when(mapper.selectCohortSummary(day, day, product)).thenReturn(cohort);
        when(mapper.selectOutputQuality(day, day, product)).thenReturn(quality);
        when(mapper.listDailyMaterialInputs(day, day, product)).thenReturn(List.of());
        when(mapper.listDailyStableOutputs(day, day, product)).thenReturn(List.of());
        when(mapper.listCohortOrderBreakdowns(day, day, product, 201)).thenReturn(List.of());

        var report = service.run(query(day, day, product));

        assertThat(report.getFiltersApplied().get("productScope")).contains(product);
        assertThat(report.getDataQuality().getUnattributedOrderCount()).isEqualTo(3);
        assertThat(report.getDataQuality().getNotes())
                .anyMatch(value -> value.contains("无法归属到具体产品"));
    }

    @Test
    void rejectsRangesLongerThan366Days() {
        LocalDate start = LocalDate.of(2025, 7, 30);
        LocalDate end = LocalDate.of(2026, 7, 31);

        assertThatThrownBy(() -> service.run(query(start, end, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最多查询 366 天");
    }

    private static RegisteredReportRunQueryDTO query(
            LocalDate start,
            LocalDate end,
            String productQuery) {
        RegisteredReportRunQueryDTO query = new RegisteredReportRunQueryDTO();
        query.setReportDefinitionId(ProductionInputOutputRegisteredReportService.REPORT_ID);
        query.setReportVersion(1);
        query.setStartDate(start);
        query.setEndDate(end);
        query.setProductQuery(productQuery);
        return query;
    }

    private static ProductionFlowCalendarSummaryRowVO calendarSummary() {
        ProductionFlowCalendarSummaryRowVO row = new ProductionFlowCalendarSummaryRowVO();
        row.setMaterialInputRecordCount(8);
        row.setMaterialInputOrderCount(4);
        row.setMaterialInputPalletCount(8);
        row.setMaterialInputBoardCount(8);
        row.setMaterialInputLoosePieceCount(0);
        row.setMaterialInputTotalPieces(342);
        row.setMaterialInputWeightKg(new BigDecimal("6840"));
        row.setStableOutputRecordCount(11);
        row.setStableOutputOrderCount(11);
        row.setStableOutputBoardCount(25);
        row.setStableOutputLoosePieceCount(15);
        row.setStableOutputTotalPieces(810);
        row.setStableOutputWeightKg(new BigDecimal("20265"));
        row.setRowsMissingWeight(0);
        row.setRowsMissingPieceConversion(0);
        return row;
    }

    private static ProductionFlowCohortSummaryRowVO cohortSummary() {
        ProductionFlowCohortSummaryRowVO row = new ProductionFlowCohortSummaryRowVO();
        row.setCohortOrderCount(9);
        row.setCompletedOrderCount(9);
        row.setCompletedOrdersWithInputCount(4);
        row.setCompletedOrdersMissingInputCount(5);
        row.setCompletedOrdersWithStableOutputCount(9);
        row.setCompletedOrdersMissingOutputCount(0);
        row.setOrdersWithInputCount(4);
        row.setOrdersMissingInputCount(5);
        row.setOrdersWithStableOutputCount(9);
        row.setOrdersMissingOutputCount(0);
        row.setCohortMaterialInputWeightKg(new BigDecimal("6840"));
        row.setCohortBoilingInputWeightKg(new BigDecimal("1656.8"));
        row.setCohortStableOutputWeightKg(new BigDecimal("19155"));
        return row;
    }

    private static ProductionFlowOutputQualityRowVO outputQuality() {
        ProductionFlowOutputQualityRowVO row = new ProductionFlowOutputQualityRowVO();
        row.setDraftOutputExcludedCount(2);
        row.setCanceledOutputExcludedCount(2);
        row.setCrossDayInboundCount(6);
        row.setUnattributedOrderCount(0);
        return row;
    }

    private static ProductionFlowDailyInputRowVO dailyInput(LocalDate day) {
        ProductionFlowDailyInputRowVO row = new ProductionFlowDailyInputRowVO();
        row.setBusinessDate(day);
        row.setInputRecordCount(8);
        row.setInputOrderCount(4);
        row.setInputPalletCount(8);
        row.setInputTotalPieces(342);
        row.setInputWeightKg(new BigDecimal("6840"));
        row.setLatestRecordedAt(LocalDateTime.of(2026, 6, 30, 14, 0));
        return row;
    }

    private static ProductionFlowDailyOutputRowVO dailyOutput(LocalDate day) {
        ProductionFlowDailyOutputRowVO row = new ProductionFlowDailyOutputRowVO();
        row.setBusinessDate(day);
        row.setOutputRecordCount(11);
        row.setOutputOrderCount(9);
        row.setOutputTotalPieces(810);
        row.setOutputWeightKg(new BigDecimal("20265"));
        row.setLatestRecordedAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        return row;
    }

    private static ProductionFlowOrderRowVO finishOrder() {
        ProductionFlowOrderRowVO row = new ProductionFlowOrderRowVO();
        row.setOrderNo("PO202606300003");
        row.setOrderType("FINISH");
        row.setOrderStatus("COMPLETED");
        row.setProductionDate(LocalDate.of(2026, 6, 30));
        row.setMaterialInputRecordCount(2);
        row.setMaterialInputPalletCount(2);
        row.setMaterialInputTotalPieces(82);
        row.setMaterialInputWeightKg(new BigDecimal("1640"));
        row.setStableOutputRecordCount(1);
        row.setStableOutputTotalPieces(80);
        row.setStableOutputWeightKg(new BigDecimal("2000"));
        row.setOutputProductNames("黄冰糖（袋）");
        return row;
    }

    private static ProductionFlowOrderRowVO semiOrder() {
        ProductionFlowOrderRowVO row = new ProductionFlowOrderRowVO();
        row.setOrderNo("PO202606300002");
        row.setOrderType("SEMI");
        row.setOrderStatus("COMPLETED");
        row.setProductionDate(LocalDate.of(2026, 6, 30));
        row.setBoilingInputUsageCount(1);
        row.setBoilingInputWeightKg(new BigDecimal("1656.8"));
        row.setStableOutputRecordCount(1);
        row.setStableOutputTotalPieces(82);
        row.setStableOutputWeightKg(new BigDecimal("1640"));
        row.setOutputProductNames("黄中冰");
        return row;
    }
}

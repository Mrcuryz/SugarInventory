package com.Laibin.SugarInventory.analytics.service;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportMetricSummaryVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportMetricsVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportPalletTaskCycleMetricsVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportProductionFlowMetricsVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportQualityMetricsVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;
import com.Laibin.SugarInventory.analytics.service.impl.PalletTaskCycleRegisteredReportService;
import com.Laibin.SugarInventory.analytics.service.impl.ProductionInputOutputRegisteredReportService;
import com.Laibin.SugarInventory.analytics.service.impl.QualityAssayRegisteredReportService;
import com.Laibin.SugarInventory.analytics.service.impl.QualityMetricRegisteredReportService;
import com.Laibin.SugarInventory.analytics.service.impl.RegisteredReportComparisonAssembler;
import com.Laibin.SugarInventory.analytics.service.impl.RegisteredReportServiceImpl;
import com.Laibin.SugarInventory.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegisteredReportComparisonAssemblerTest {
    private final RegisteredReportComparisonAssembler assembler = new RegisteredReportComparisonAssembler();

    @Test
    void comparesPreviousEqualLengthPeriodWithoutChangingTheRegisteredDefinition() {
        RegisteredReportRunQueryDTO query = query(
                RegisteredReportServiceImpl.DAILY_PRODUCTION_REPORT_ID,
                LocalDate.of(2026, 7, 8), LocalDate.of(2026, 7, 14));
        query.setComparisonMode("PREVIOUS_PERIOD");
        AtomicReference<RegisteredReportRunQueryDTO> baselineQuery = new AtomicReference<>();

        RegisteredReportRunVO result = assembler.attachComparison(
                query,
                dailyReport("700", 70),
                value -> {
                    baselineQuery.set(value);
                    return dailyReport("350", 35);
                });

        assertThat(baselineQuery.get().getStartDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(baselineQuery.get().getEndDate()).isEqualTo(LocalDate.of(2026, 7, 7));
        assertThat(baselineQuery.get().getReportDefinitionId())
                .isEqualTo(RegisteredReportServiceImpl.DAILY_PRODUCTION_REPORT_ID);
        assertThat(result.getComparison().getComparisonLabel()).isEqualTo("上一等长期间");
        assertThat(result.getComparison().isDifferentPeriodLengths()).isFalse();
        assertThat(result.getComparison().getMetrics().get(0).getAbsoluteChange())
                .isEqualByComparingTo("350");
        assertThat(result.getComparison().getMetrics().get(0).getPercentChange())
                .isEqualByComparingTo("100");
    }

    @Test
    void customDifferentLengthComparisonProvidesDailyAveragesAndRejectsOverlap() {
        RegisteredReportRunQueryDTO query = query(
                RegisteredReportServiceImpl.DAILY_PRODUCTION_REPORT_ID,
                LocalDate.of(2026, 7, 8), LocalDate.of(2026, 7, 14));
        query.setComparisonMode("CUSTOM");
        query.setComparisonStartDate(LocalDate.of(2026, 6, 24));
        query.setComparisonEndDate(LocalDate.of(2026, 7, 7));

        var result = assembler.attachComparison(
                query, dailyReport("700", 70), ignored -> dailyReport("700", 70));
        var weight = result.getComparison().getMetrics().get(0);

        assertThat(result.getComparison().isDifferentPeriodLengths()).isTrue();
        assertThat(weight.getCurrentDailyAverage()).isEqualByComparingTo("100");
        assertThat(weight.getComparisonDailyAverage()).isEqualByComparingTo("50");

        query.setComparisonStartDate(LocalDate.of(2026, 7, 7));
        query.setComparisonEndDate(LocalDate.of(2026, 7, 9));
        assertThatThrownBy(() -> assembler.attachComparison(
                query, dailyReport("700", 70), ignored -> dailyReport("100", 10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重叠");
    }

    @Test
    void allFiveRegisteredReportsUseTheCentralComparisonContract() {
        List<ReportPair> pairs = List.of(
                new ReportPair(dailyReport("20", 2), dailyReport("10", 1)),
                new ReportPair(qualityReport(10, "80"), qualityReport(8, "75")),
                new ReportPair(metricReport(10, "7.2"), metricReport(8, "7.0")),
                new ReportPair(flowReport("100", "80"), flowReport("90", "70")),
                new ReportPair(taskReport(10, 60L), taskReport(8, 70L))
        );
        for (ReportPair pair : pairs) {
            RegisteredReportRunQueryDTO query = query(
                    pair.current().getReportDefinitionId(),
                    LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 2));
            query.setComparisonMode("PREVIOUS_PERIOD");
            var result = assembler.attachComparison(query, pair.current(), ignored -> pair.baseline());

            assertThat(result.getComparison().getMetrics()).hasSize(4);
            assertThat(result.getComparison().getMetrics())
                    .allMatch(metric -> metric.getMetricLabel() != null && !metric.getMetricLabel().isBlank());
        }
    }

    private static RegisteredReportRunQueryDTO query(String definitionId, LocalDate start, LocalDate end) {
        RegisteredReportRunQueryDTO query = new RegisteredReportRunQueryDTO();
        query.setReportDefinitionId(definitionId);
        query.setReportVersion(1);
        query.setStartDate(start);
        query.setEndDate(end);
        query.setProductQuery("黄冰糖");
        return query;
    }

    private static RegisteredReportRunVO dailyReport(String weight, int records) {
        return base(RegisteredReportServiceImpl.DAILY_PRODUCTION_REPORT_ID)
                .metrics(RegisteredReportMetricsVO.builder()
                        .totalWeightKg(new BigDecimal(weight))
                        .outputRecordCount(records)
                        .productionOrderCount(records)
                        .totalPieces(records * 10)
                        .build())
                .build();
    }

    private static RegisteredReportRunVO qualityReport(int records, String rate) {
        return base(QualityAssayRegisteredReportService.REPORT_ID)
                .qualityMetrics(RegisteredReportQualityMetricsVO.builder()
                        .assayRecordCount(records)
                        .passRatePercent(new BigDecimal(rate))
                        .failCount(1)
                        .noStandardCount(1)
                        .build())
                .build();
    }

    private static RegisteredReportRunVO metricReport(int samples, String average) {
        return base(QualityMetricRegisteredReportService.REPORT_ID)
                .metricTrendSummary(RegisteredReportMetricSummaryVO.builder()
                        .sampleCount(samples)
                        .unit("-")
                        .averageValue(new BigDecimal(average))
                        .medianValue(new BigDecimal(average))
                        .withinStandardRatePercent(BigDecimal.valueOf(90))
                        .build())
                .build();
    }

    private static RegisteredReportRunVO flowReport(String input, String output) {
        return base(ProductionInputOutputRegisteredReportService.REPORT_ID)
                .productionFlowMetrics(RegisteredReportProductionFlowMetricsVO.builder()
                        .materialInputWeightKg(new BigDecimal(input))
                        .stableOutputWeightKg(new BigDecimal(output))
                        .completedOrdersMissingInputCount(1)
                        .completedOrdersMissingOutputCount(2)
                        .build())
                .build();
    }

    private static RegisteredReportRunVO taskReport(int completed, Long median) {
        return base(PalletTaskCycleRegisteredReportService.REPORT_ID)
                .palletTaskCycleMetrics(RegisteredReportPalletTaskCycleMetricsVO.builder()
                        .completedTaskCount(completed)
                        .medianDurationSeconds(median)
                        .p90DurationSeconds(median + 20)
                        .inProgressTaskCount(2)
                        .build())
                .build();
    }

    private static RegisteredReportRunVO.RegisteredReportRunVOBuilder base(String definitionId) {
        return RegisteredReportRunVO.builder()
                .reportDefinitionId(definitionId)
                .reportVersion(1)
                .metricDefinitionVersion("metric_v1")
                .dataScope("REGISTERED_FACTS")
                .dataAsOf(LocalDateTime.of(2026, 7, 31, 20, 0));
    }

    private record ReportPair(RegisteredReportRunVO current, RegisteredReportRunVO baseline) {
    }
}

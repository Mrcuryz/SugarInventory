package com.Laibin.SugarInventory.analytics.service;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.vo.QualityAssayReportRowVO;
import com.Laibin.SugarInventory.analytics.mapper.QualityAssayReportMapper;
import com.Laibin.SugarInventory.analytics.service.impl.QualityAssayRegisteredReportService;
import com.Laibin.SugarInventory.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QualityAssayRegisteredReportServiceTest {
    private QualityAssayReportMapper mapper;
    private QualityAssayRegisteredReportService service;

    @BeforeEach
    void setUp() {
        mapper = mock(QualityAssayReportMapper.class);
        service = new QualityAssayRegisteredReportService(mapper);
    }

    @Test
    void aggregatesJudgementTrendWithControlledPassRateAndHistoricalStandards() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 3);
        when(mapper.listQualityAssaysForReport(start, end, "黄冰糖"))
                .thenReturn(List.of(
                        row(1, 10, "黄冰糖（袋）", start, "PASS", "黄冰糖", 1, start.atTime(10, 0)),
                        row(2, 10, "黄冰糖（袋）", start.plusDays(1), "FAIL", "黄冰糖", 1,
                                start.plusDays(2).atTime(9, 0)),
                        row(3, 11, "黄冰糖小颗粒（袋）", end, "NO_STANDARD", null, null,
                                end.atTime(11, 0)),
                        row(4, 11, "黄冰糖小颗粒（袋）", end, "MULTIPLE_CANDIDATES", "黄冰糖", 2,
                                end.atTime(12, 0))));

        var report = service.run(query(start, end, " 黄冰糖 "));

        assertThat(report.getReportDefinitionId()).isEqualTo("quality_assay_result_trend_v1");
        assertThat(report.getMetricDefinitionVersion()).isEqualTo("quality_assay_judgement_v1");
        assertThat(report.getSeriesGranularity()).isEqualTo("DAY");
        assertThat(report.getQualityMetrics().getAssayRecordCount()).isEqualTo(4);
        assertThat(report.getQualityMetrics().getJudgedRecordCount()).isEqualTo(2);
        assertThat(report.getQualityMetrics().getPassCount()).isEqualTo(1);
        assertThat(report.getQualityMetrics().getFailCount()).isEqualTo(1);
        assertThat(report.getQualityMetrics().getNoStandardCount()).isEqualTo(1);
        assertThat(report.getQualityMetrics().getMultipleCandidatesCount()).isEqualTo(1);
        assertThat(report.getQualityMetrics().getPassRatePercent()).isEqualByComparingTo("50.0");
        assertThat(report.getQualityMetrics().getDistinctProductCount()).isEqualTo(2);
        assertThat(report.getQualityMetrics().getDistinctStandardVersionCount()).isEqualTo(2);
        assertThat(report.getQualitySeries()).hasSize(3);
        assertThat(report.getQualityProductBreakdowns()).hasSize(2);
        assertThat(report.getStandardBreakdowns()).hasSize(2);
        assertThat(report.getDataQuality().getLateRecordedCount()).isEqualTo(1);
        assertThat(report.getLimitations())
                .anyMatch(value -> value.contains("无标准") && value.contains("不进入分母"));
        assertThat(report.getLimitations())
                .anyMatch(value -> value.contains("不能据此推断") && value.contains("缺少化验"));
    }

    @Test
    void usesMonthlySeriesForLongRangesAndRejectsMoreThanOneYear() {
        LocalDate start = LocalDate.of(2026, 1, 15);
        LocalDate end = LocalDate.of(2026, 7, 29);
        when(mapper.listQualityAssaysForReport(start, end, null)).thenReturn(List.of());

        var report = service.run(query(start, end, null));

        assertThat(report.getSeriesGranularity()).isEqualTo("MONTH");
        assertThat(report.getQualitySeries()).hasSize(7);
        assertThat(report.getQualitySeries().get(0).getPeriodStart()).isEqualTo(start);
        assertThat(report.getQualitySeries().get(6).getPeriodEnd()).isEqualTo(end);
        assertThat(report.getQualityMetrics().getPassRatePercent()).isNull();

        RegisteredReportRunQueryDTO overlong = query(
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2026, 7, 29),
                null);
        assertThatThrownBy(() -> service.run(overlong))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("366 天");
    }

    private static RegisteredReportRunQueryDTO query(
            LocalDate startDate,
            LocalDate endDate,
            String productQuery) {
        RegisteredReportRunQueryDTO query = new RegisteredReportRunQueryDTO();
        query.setReportDefinitionId(QualityAssayRegisteredReportService.REPORT_ID);
        query.setReportVersion(QualityAssayRegisteredReportService.REPORT_VERSION);
        query.setStartDate(startDate);
        query.setEndDate(endDate);
        query.setProductQuery(productQuery);
        return query;
    }

    private static QualityAssayReportRowVO row(
            int assayId,
            int productId,
            String productName,
            LocalDate sampleDate,
            String judgeResult,
            String standardName,
            Integer standardVersion,
            LocalDateTime createdAt) {
        QualityAssayReportRowVO row = new QualityAssayReportRowVO();
        row.setAssayId(assayId);
        row.setProductId(productId);
        row.setProductName(productName);
        row.setSampleDate(sampleDate);
        row.setJudgeResult(judgeResult);
        row.setAppliedStandardName(standardName);
        row.setAppliedStandardVersion(standardVersion);
        row.setCreatedAt(createdAt);
        return row;
    }
}

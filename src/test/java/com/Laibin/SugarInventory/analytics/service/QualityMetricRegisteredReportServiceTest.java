package com.Laibin.SugarInventory.analytics.service;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.vo.QualityAssayReportRowVO;
import com.Laibin.SugarInventory.analytics.mapper.QualityAssayReportMapper;
import com.Laibin.SugarInventory.analytics.service.impl.QualityMetricRegisteredReportService;
import com.Laibin.SugarInventory.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

class QualityMetricRegisteredReportServiceTest {
    private QualityAssayReportMapper mapper;
    private QualityMetricRegisteredReportService service;

    @BeforeEach
    void setUp() {
        mapper = mock(QualityAssayReportMapper.class);
        service = new QualityMetricRegisteredReportService(mapper, new ObjectMapper());
    }

    @Test
    void aggregatesRawMetricWithoutApplyingCurrentStandardToHistoricalRows() {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 7, 29);
        QualityAssayReportRowVO noStandard = row(
                1,
                84,
                "黄冰糖（袋）",
                LocalDate.of(2026, 4, 23),
                "1.0",
                null,
                null,
                null);
        QualityAssayReportRowVO inRange = row(
                2,
                84,
                "黄冰糖（袋）",
                LocalDate.of(2026, 7, 17),
                "7.2",
                "黄冰糖",
                1,
                """
                        {"standardName":"黄冰糖","version":1,"items":[
                          {"metricCode":"ph","compareType":"range","minValue":6,"maxValue":9,"unit":"-"}
                        ]}
                        """);
        QualityAssayReportRowVO missing = row(
                3,
                85,
                "黄冰糖小颗粒（袋）",
                LocalDate.of(2026, 7, 18),
                null,
                "黄冰糖",
                1,
                inRange.getStandardSnapshotJson());
        when(mapper.listQualityAssaysForReport(start, end, "黄冰糖"))
                .thenReturn(List.of(noStandard, inRange, missing));

        var report = service.run(query(start, end, " 黄冰糖 ", "ph"));

        assertThat(report.getReportDefinitionId()).isEqualTo("quality_metric_trend_v1");
        assertThat(report.getMetricDefinitionVersion())
                .isEqualTo("quality_assay_metric_statistics_v1");
        assertThat(report.getSeriesGranularity()).isEqualTo("MONTH");
        assertThat(report.getMetricTrendSummary().getMetricName()).isEqualTo("pH");
        assertThat(report.getMetricTrendSummary().getAssayRecordCount()).isEqualTo(3);
        assertThat(report.getMetricTrendSummary().getSampleCount()).isEqualTo(2);
        assertThat(report.getMetricTrendSummary().getMissingValueCount()).isEqualTo(1);
        assertThat(report.getMetricTrendSummary().getAverageValue()).isEqualByComparingTo("4.1");
        assertThat(report.getMetricTrendSummary().getMedianValue()).isEqualByComparingTo("4.1");
        assertThat(report.getMetricTrendSummary().getMinimumValue()).isEqualByComparingTo("1");
        assertThat(report.getMetricTrendSummary().getMaximumValue()).isEqualByComparingTo("7.2");
        assertThat(report.getMetricTrendSummary().getWithinStandardCount()).isEqualTo(1);
        assertThat(report.getMetricTrendSummary().getOutOfStandardCount()).isZero();
        assertThat(report.getMetricTrendSummary().getWithoutComparableStandardCount()).isEqualTo(1);
        assertThat(report.getMetricTrendSummary().getWithinStandardRatePercent())
                .isEqualByComparingTo("100.0");
        assertThat(report.getMetricSeries()).hasSize(4);
        assertThat(report.getMetricProductBreakdowns()).hasSize(1);
        assertThat(report.getMetricStandardBreakdowns()).singleElement().satisfies(standard -> {
            assertThat(standard.getStandardLabel()).isEqualTo("黄冰糖 v1");
            assertThat(standard.getRangeLabel()).isEqualTo("6 - 9");
            assertThat(standard.getWithinStandardCount()).isEqualTo(1);
        });
        assertThat(report.getDataQuality().getRowsMissingMetricValue()).isEqualTo(1);
        assertThat(report.getDataQuality().getRowsWithoutComparableMetricStandard()).isEqualTo(1);
        assertThat(report.getLimitations())
                .anyMatch(value -> value.contains("不使用当前标准重算"));
    }

    @Test
    void rejectsMissingMetricUnsupportedMetricAndOverlongRange() {
        RegisteredReportRunQueryDTO missingMetric = query(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 29),
                null,
                null);
        assertThatThrownBy(() -> service.run(missingMetric))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("化验指标");

        RegisteredReportRunQueryDTO unsupported = query(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 29),
                null,
                "arbitrary_metric");
        assertThatThrownBy(() -> service.run(unsupported))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("化验指标");

        RegisteredReportRunQueryDTO overlong = query(
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2026, 7, 29),
                null,
                "ph");
        assertThatThrownBy(() -> service.run(overlong))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("366 天");
    }

    private static RegisteredReportRunQueryDTO query(
            LocalDate startDate,
            LocalDate endDate,
            String productQuery,
            String metricKey) {
        RegisteredReportRunQueryDTO query = new RegisteredReportRunQueryDTO();
        query.setReportDefinitionId(QualityMetricRegisteredReportService.REPORT_ID);
        query.setReportVersion(QualityMetricRegisteredReportService.REPORT_VERSION);
        query.setStartDate(startDate);
        query.setEndDate(endDate);
        query.setProductQuery(productQuery);
        query.setMetricKey(metricKey);
        return query;
    }

    private static QualityAssayReportRowVO row(
            int assayId,
            int productId,
            String productName,
            LocalDate sampleDate,
            String phValue,
            String standardName,
            Integer standardVersion,
            String standardSnapshotJson) {
        QualityAssayReportRowVO row = new QualityAssayReportRowVO();
        row.setAssayId(assayId);
        row.setProductId(productId);
        row.setProductName(productName);
        row.setSampleDate(sampleDate);
        row.setCreatedAt(LocalDateTime.of(sampleDate, java.time.LocalTime.NOON));
        row.setPhValue(phValue == null ? null : new BigDecimal(phValue));
        row.setAppliedStandardName(standardName);
        row.setAppliedStandardVersion(standardVersion);
        row.setStandardSnapshotJson(standardSnapshotJson);
        return row;
    }
}

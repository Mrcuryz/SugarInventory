package com.Laibin.SugarInventory.analytics.service;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;
import com.Laibin.SugarInventory.analytics.service.impl.ProductionDailyRegisteredReportService;
import com.Laibin.SugarInventory.analytics.service.impl.RegisteredReportComparisonAssembler;
import com.Laibin.SugarInventory.analytics.service.impl.RegisteredReportServiceImpl;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyAggregateVO;
import com.Laibin.SugarInventory.production.service.ProductionDailyAggregateService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionDailyReportComparisonTest {
    private final RegisteredReportComparisonAssembler assembler = new RegisteredReportComparisonAssembler();

    @Test
    void previousPeriodPreservesDepartmentProductAndSourceAndUsesBackendNumbers() {
        var aggregate = mock(ProductionDailyAggregateService.class);
        when(aggregate.aggregate(any(), eq(true))).thenAnswer(call -> {
            var scope = (com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportAgentQueryDTO) call.getArgument(0);
            assertThat(scope.getDepartmentName()).isEqualTo("多晶车间");
            assertThat(scope.getItemQuery()).isEqualTo("黄冰糖");
            return data(series("same", scope.getStartDate().getDayOfMonth() == 8 ? "30" : "20", "2"));
        });
        var target = new RegisteredReportServiceImpl(null, null, null, null, null, null, null, null,
                assembler, new ProductionDailyRegisteredReportService(aggregate));
        var result = target.run(query()).getComparison();
        assertThat(result.getComparisonStartDate()).isEqualTo("2026-09-05");
        assertThat(result.getComparisonEndDate()).isEqualTo("2026-09-07");
        assertThat(result.getCurrentPeriodDays()).isEqualTo(3);
        assertThat(result.getMetrics()).hasSize(2);
        assertThat(result.getMetrics().get(0).getAbsoluteChange()).isEqualByComparingTo("10");
        assertThat(result.getMetrics().get(0).getPercentChange()).isEqualByComparingTo("50");
        verify(aggregate, times(2)).aggregate(any(), eq(true));
    }

    @Test
    void customEqualPeriodUsesRequestedDatesAndDoesNotRecursivelyCompare() {
        var query = query(); query.setComparisonMode("CUSTOM");
        query.setComparisonStartDate(LocalDate.of(2026, 8, 1)); query.setComparisonEndDate(LocalDate.of(2026, 8, 3));
        var captured = new AtomicReference<RegisteredReportRunQueryDTO>();
        var result = assembler.attachComparison(query, report(data(series("same", "20", "2"))), baseline -> {
            captured.set(baseline); return report(data(series("same", "10", "1")));
        });
        assertThat(captured.get().getStartDate()).isEqualTo(query.getComparisonStartDate());
        assertThat(captured.get().getComparisonMode()).isNull();
        assertThat(captured.get().getComparisonStartDate()).isNull();
        assertThat(result.getComparison().isDifferentPeriodLengths()).isFalse();
    }

    @Test
    void zeroNegativeAndMissingBaselineNeverYieldAnInventedRate() {
        for (String previous : new String[]{"0", "-10", null}) {
            var result = compare(data(series("same", "30", "3")), data(series("same", previous, null)));
            var metric = result.getComparison().getMetrics().get(0);
            assertThat(metric.getPercentChange()).isNull();
            assertThat(metric.getDailyAveragePercentChange()).isNull();
            if (previous == null) assertThat(metric.getAbsoluteChange()).isNull();
            else assertThat(metric.getAbsoluteChange()).isEqualByComparingTo(new BigDecimal("30").subtract(new BigDecimal(previous)));
        }
    }

    @Test
    void quantityAndTonnageHaveIndependentCoverageAndDraftDoesNotEraseSavedValues() {
        var current = series("same", "30", "3"); current.setDraftRows(1);
        var previous = series("same", "20", "2"); previous.setTonnageRows(2); previous.setTonnageObservedDays(2);
        var metrics = compare(data(current), data(previous)).getComparison().getMetrics();
        assertThat(metrics.get(0).getPercentChange()).isEqualByComparingTo("50");
        assertThat(metrics.get(0).getNote()).contains("未提交");
        assertThat(metrics.get(1).getPercentChange()).isNull();
        assertThat(metrics.get(1).getAbsoluteChange()).isEqualByComparingTo("1");
        assertThat(metrics.get(1).getCurrentDailyAverage()).isNull();
        assertThat(metrics.get(1).getNote()).contains("2/3行", "2/3天", "已记录值");
        current.setQuantityRows(2); current.setObservedDays(2);
        previous.setTonnageRows(3); previous.setTonnageObservedDays(3);
        metrics = compare(data(current), data(previous)).getComparison().getMetrics();
        assertThat(metrics.get(0).getPercentChange()).isNull();
        assertThat(metrics.get(1).getPercentChange()).isEqualByComparingTo("50");
    }

    @Test
    void missingDayOrNullRowSuppressesRateEvenIfTheOtherCoverageCountLooksComplete() {
        var current = series("same", "30", "3");
        var previous = series("same", "20", "2"); previous.setQuantityRows(2);
        assertThat(compare(data(current), data(previous)).getComparison().getMetrics().get(0).getPercentChange()).isNull();
        previous.setQuantityRows(3); previous.setObservedDays(2);
        assertThat(compare(data(current), data(previous)).getComparison().getMetrics().get(0).getPercentChange()).isNull();
    }

    @Test
    void unionKeepsOneSidedSeriesAndDoesNotMergeSameDisplayNamesOrDifferentUnits() {
        var previous = series("old-dimension", "10", "1"); previous.setUnit("箱");
        var metrics = compare(data(series("new-dimension", "30", "3")), data(previous)).getComparison().getMetrics();
        assertThat(metrics).hasSize(4);
        assertThat(metrics.get(0).getComparisonValue()).isNull();
        assertThat(metrics.get(2).getCurrentValue()).isNull();
        assertThat(metrics).allMatch(m -> m.getAbsoluteChange() == null && m.getPercentChange() == null);
        assertThat(metrics.get(2).getUnit()).isEqualTo("箱");
        assertThat(metrics.get(0).getNote()).contains("不按0处理");
    }

    @Test
    void truncationInEitherPeriodSuppressesAllRatesEvenForMatchedCompleteSeries() {
        for (boolean currentTruncated : new boolean[]{true, false}) {
            var current = data(series("same", "30", "3")); current.setTruncated(currentTruncated);
            var previous = data(series("same", "20", "2")); previous.setTruncated(!currentTruncated);
            var metrics = compare(current, previous).getComparison().getMetrics();
            assertThat(metrics).allMatch(m -> m.getPercentChange() == null && m.getCurrentDailyAverage() == null);
            assertThat(metrics.get(0).getNote()).contains("展示上限");
        }
    }

    @Test
    void energyUsesOriginalUnitWithoutInventingTonnage() {
        var current = series("power", "30", null); current.setTonnageApplicable(false); current.setUnit("kWh");
        var previous = series("power", "20", null); previous.setTonnageApplicable(false); previous.setUnit("kWh");
        var metrics = compare(data(current), data(previous)).getComparison().getMetrics();
        assertThat(metrics).hasSize(1); assertThat(metrics.get(0).getUnit()).isEqualTo("kWh");
        assertThat(metrics.get(0).getPercentChange()).isEqualByComparingTo("50");
    }

    @Test
    void emptyPeriodsAreNotZeroAndPreviousOnlySeriesArePreserved() {
        assertThat(compare(data(), data()).getComparison().getMetrics()).isEmpty();
        var metric = compare(data(), data(series("same", "20", "2"))).getComparison().getMetrics().get(0);
        assertThat(metric.getCurrentValue()).isNull();
        assertThat(metric.getComparisonValue()).isEqualByComparingTo("20");
        assertThat(metric.getPercentChange()).isNull();
    }

    @Test
    void previousPeriodDataQualityIsNotHiddenByACompleteCurrentPeriod() {
        var previous = report(data(series("same", "20", "2"))).toBuilder().dataQuality(
                com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportDataQualityVO.builder()
                        .partial(true).notes(List.of("缺少折吨记录")).build()).build();
        var result = assembler.attachComparison(query(), report(data(series("same", "30", "3"))), ignored -> previous);
        assertThat(result.getDataQuality().isPartial()).isTrue();
        assertThat(result.getDataQuality().getNotes()).containsExactly("对比期：缺少折吨记录");
    }

    @Test
    void invalidComparisonDatesRejectBeforeDatabaseReads() {
        var aggregate = mock(ProductionDailyAggregateService.class);
        var service = new ProductionDailyRegisteredReportService(aggregate);
        var query = query(); query.setComparisonMode("CUSTOM");
        query.setComparisonStartDate(LocalDate.of(2026, 8, 1)); query.setComparisonEndDate(LocalDate.of(2026, 8, 2));
        assertThatThrownBy(() -> service.run(query)).isInstanceOf(BusinessException.class).hasMessageContaining("等长");
        query.setComparisonStartDate(query.getStartDate()); query.setComparisonEndDate(query.getEndDate());
        assertThatThrownBy(() -> service.run(query)).isInstanceOf(BusinessException.class).hasMessageContaining("重叠");
        query.setComparisonStartDate(LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1));
        query.setComparisonEndDate(query.getComparisonStartDate().plusDays(2));
        assertThatThrownBy(() -> service.run(query)).isInstanceOf(BusinessException.class).hasMessageContaining("未来");
        query.setComparisonMode("PREVIOUS_PERIOD");
        assertThatThrownBy(() -> service.run(query)).isInstanceOf(BusinessException.class).hasMessageContaining("额外");
        query.setComparisonMode(null);
        assertThatThrownBy(() -> service.run(query)).isInstanceOf(BusinessException.class).hasMessageContaining("对比方式");
        query.setComparisonStartDate(null); query.setComparisonEndDate(null); query.setComparisonMode("CUSTOM");
        assertThatThrownBy(() -> service.run(query)).isInstanceOf(BusinessException.class).hasMessageContaining("同时提供");
        query.setComparisonMode("PREVIOUS_PERIOD"); query.setEndDate(query.getStartDate().plusDays(31));
        assertThatThrownBy(() -> service.run(query)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(aggregate);
    }

    @Test
    void mismatchedDimensionsFiltersAndDuplicateKeysFailClosed() {
        var previous = series("same", "20", "2"); previous.setUnit("箱");
        assertThatThrownBy(() -> compare(data(series("same", "30", "3")), data(previous)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("口径");
        assertThatThrownBy(() -> compare(data(series("same", "30", "3")), data(previous, previous)))
                .isInstanceOf(BusinessException.class);
        var report = report(data()).toBuilder().filtersApplied(Map.of("departmentScope", "其他车间")).build();
        assertThatThrownBy(() -> assembler.attachComparison(query(), report(data()), ignored -> report))
                .isInstanceOf(BusinessException.class).hasMessageContaining("口径");
    }

    private RegisteredReportRunVO compare(ProductionDailyAggregateVO current, ProductionDailyAggregateVO previous) {
        return assembler.attachComparison(query(), report(current), ignored -> report(previous));
    }

    private static RegisteredReportRunQueryDTO query() {
        var query = new RegisteredReportRunQueryDTO(); query.setReportDefinitionId(ProductionDailyRegisteredReportService.REPORT_ID);
        query.setReportVersion(1); query.setStartDate(LocalDate.of(2026, 9, 8)); query.setEndDate(LocalDate.of(2026, 9, 10));
        query.setDepartmentName("多晶车间"); query.setProductQuery("黄冰糖"); query.setComparisonMode("PREVIOUS_PERIOD");
        return query;
    }

    private static RegisteredReportRunVO report(ProductionDailyAggregateVO aggregate) {
        return RegisteredReportRunVO.builder().reportDefinitionId(ProductionDailyRegisteredReportService.REPORT_ID)
                .reportVersion(1).dataScope("SAVED_PRODUCTION_DAILY_REPORT").metricDefinitionVersion("saved_daily_actual_and_tonnage_v1")
                .filtersApplied(Map.of("departmentScope", "多晶车间", "productScope", "黄冰糖", "sourceLabel", "生产日报"))
                .productionDailyReport(aggregate).build();
    }

    private static ProductionDailyAggregateVO data(ProductionDailyAggregateVO.Series... series) {
        var result = new ProductionDailyAggregateVO(); result.setRequestedDays(3); result.setSeries(List.of(series)); return result;
    }

    private static ProductionDailyAggregateVO.Series series(String key, String quantity, String tons) {
        var result = new ProductionDailyAggregateVO.Series(); result.setSeriesKey(key);
        result.setSummaryType("产品分类汇总"); result.setDepartmentName("多晶车间"); result.setLabel("黄冰糖"); result.setUnit("件");
        result.setQuantity(quantity == null ? null : new BigDecimal(quantity)); result.setConvertedTons(tons == null ? null : new BigDecimal(tons));
        result.setRecordCount(3); result.setQuantityRows(quantity == null ? 0 : 3); result.setObservedDays(quantity == null ? 0 : 3);
        result.setTonnageRows(tons == null ? 0 : 3); result.setTonnageObservedDays(tons == null ? 0 : 3);
        result.setTonnageApplicable(true); result.setPoints(List.of()); return result;
    }
}

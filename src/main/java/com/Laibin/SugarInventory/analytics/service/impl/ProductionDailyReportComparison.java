package com.Laibin.SugarInventory.analytics.service.impl;

import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportMetricComparisonVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyAggregateVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Daily facts use the shared comparison wire format, but independent quantity/tonnage coverage gates. */
final class ProductionDailyReportComparison {
    private ProductionDailyReportComparison() { }

    static List<RegisteredReportMetricComparisonVO> metrics(RegisteredReportRunVO current,
                                                           RegisteredReportRunVO previous) {
        var now = current.getProductionDailyReport();
        var before = previous.getProductionDailyReport();
        if (now == null || before == null || now.getRequestedDays() != before.getRequestedDays()
                || now.getRequestedDays() < 1 || now.getRequestedDays() > 31) invalid();
        for (String filter : List.of("departmentScope", "productScope", "sourceLabel")) {
            if (current.getFiltersApplied() == null || previous.getFiltersApplied() == null
                    || !Objects.equals(current.getFiltersApplied().get(filter), previous.getFiltersApplied().get(filter))) invalid();
        }
        var left = index(now);
        var right = index(before);
        var keys = new LinkedHashSet<>(left.keySet());
        keys.addAll(right.keySet());
        List<RegisteredReportMetricComparisonVO> result = new ArrayList<>();
        for (String key : keys) {
            var a = left.get(key);
            var b = right.get(key);
            if (a != null && b != null && (!Objects.equals(a.getUnit(), b.getUnit())
                    || !Objects.equals(a.getSummaryType(), b.getSummaryType())
                    || !Objects.equals(a.getDepartmentName(), b.getDepartmentName())
                    || !Objects.equals(a.getLabel(), b.getLabel())
                    || a.isTonnageApplicable() != b.isTonnageApplicable())) invalid();
            var dimension = a == null ? b : a;
            boolean truncated = now.isTruncated() || before.isTruncated();
            result.add(compare(a, b, dimension, false, now.getRequestedDays(), truncated, result.size()));
            if (dimension.isTonnageApplicable()) {
                result.add(compare(a, b, dimension, true, now.getRequestedDays(), truncated, result.size()));
            }
        }
        return List.copyOf(result);
    }

    private static Map<String, ProductionDailyAggregateVO.Series> index(ProductionDailyAggregateVO report) {
        var result = new LinkedHashMap<String, ProductionDailyAggregateVO.Series>();
        if (report.getSeries() == null || report.getSeries().size() > 10) invalid();
        for (var series : report.getSeries()) {
            if (series == null || series.getSeriesKey() == null || series.getSeriesKey().isBlank()
                    || result.putIfAbsent(series.getSeriesKey(), series) != null) invalid();
        }
        return result;
    }

    private static RegisteredReportMetricComparisonVO compare(
            ProductionDailyAggregateVO.Series a, ProductionDailyAggregateVO.Series b,
            ProductionDailyAggregateVO.Series dimension, boolean tons, int days, boolean truncated, int index) {
        BigDecimal current = value(a, tons), previous = value(b, tons);
        boolean complete = !truncated && complete(a, tons, days) && complete(b, tons, days);
        BigDecimal difference = current == null || previous == null ? null : current.subtract(previous);
        BigDecimal percent = !complete || difference == null || previous.signum() <= 0 ? null
                : difference.multiply(BigDecimal.valueOf(100)).divide(previous, 4, RoundingMode.HALF_UP).stripTrailingZeros();
        List<String> notes = new ArrayList<>();
        notes.add(coverage("本期", a, tons, days));
        notes.add(coverage("对比期", b, tons, days));
        if (truncated) notes.add("至少一期系列超过展示上限，请缩小范围；未计算变化率");
        else if (!complete) notes.add("至少一期缺少系列、日期或数值；仅展示已记录值及可计算的差额，未计算变化率");
        else if (previous == null || previous.signum() <= 0) notes.add("对比期值不大于0，未计算变化率");
        if ((a != null && a.getDraftRows() > 0) || (b != null && b.getDraftRows() > 0)) notes.add("包含已保存但未提交的车间数据");
        return RegisteredReportMetricComparisonVO.builder()
                .metricCode("daily_" + index)
                .metricLabel(dimension.getSummaryType() + " · " + dimension.getDepartmentName() + " · "
                        + dimension.getLabel() + (tons ? "（折吨）" : "（数量）"))
                .unit(tons ? "吨" : dimension.getUnit()).additive(true)
                .currentValue(current).comparisonValue(previous).absoluteChange(difference).percentChange(percent)
                // No averages over an incomplete calendar; do not convert missing days to zero.
                .currentDailyAverage(complete ? average(current, days) : null)
                .comparisonDailyAverage(complete ? average(previous, days) : null)
                .dailyAverageAbsoluteChange(complete ? average(difference, days) : null)
                .dailyAveragePercentChange(percent).note(String.join("；", notes)).build();
    }

    private static BigDecimal value(ProductionDailyAggregateVO.Series series, boolean tons) {
        return series == null ? null : tons ? series.getConvertedTons() : series.getQuantity();
    }

    private static boolean complete(ProductionDailyAggregateVO.Series series, boolean tons, int days) {
        return series != null && value(series, tons) != null && series.getRecordCount() > 0
                && (tons ? series.getTonnageObservedDays() : series.getObservedDays()) == days
                && (tons ? series.getTonnageRows() : series.getQuantityRows()) == series.getRecordCount();
    }

    private static String coverage(String period, ProductionDailyAggregateVO.Series series, boolean tons, int days) {
        if (series == null) return period + "未返回该系列（不按0处理）";
        return period + "已保存" + series.getRecordCount() + "行，其中未提交" + series.getDraftRows()
                + "行；" + (tons ? "折吨" : "数量") + "已填" + (tons ? series.getTonnageRows() : series.getQuantityRows()) + "/"
                + series.getRecordCount() + "行，覆盖" + (tons ? series.getTonnageObservedDays() : series.getObservedDays())
                + "/" + days + "天";
    }

    private static BigDecimal average(BigDecimal value, int days) {
        return value == null ? null : value.divide(BigDecimal.valueOf(days), 4, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private static void invalid() { throw new BusinessException(409, "日报比较数据口径不一致，请重新生成报表"); }
}

package com.Laibin.SugarInventory.analytics.service.impl;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportComparisonVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportMetricComparisonVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;
import com.Laibin.SugarInventory.common.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Component
public class RegisteredReportComparisonAssembler {
    public static final String PREVIOUS_PERIOD = "PREVIOUS_PERIOD";
    public static final String CUSTOM = "CUSTOM";

    public RegisteredReportRunVO attachComparison(
            RegisteredReportRunQueryDTO query,
            RegisteredReportRunVO current,
            Function<RegisteredReportRunQueryDTO, RegisteredReportRunVO> reportRunner) {
        String mode = normalizeMode(query == null ? null : query.getComparisonMode());
        if (mode == null) {
            rejectOrphanComparisonDates(query);
            return current;
        }
        if (current == null || query == null || query.getStartDate() == null || query.getEndDate() == null) {
            throw new BusinessException(400, "跨期比较缺少本期报表范围");
        }

        Period comparisonPeriod = comparisonPeriod(query, mode);
        RegisteredReportRunQueryDTO comparisonQuery = copyForComparison(query, comparisonPeriod);
        RegisteredReportRunVO comparison = reportRunner.apply(comparisonQuery);
        assertCompatible(current, comparison);

        int currentDays = periodDays(query.getStartDate(), query.getEndDate());
        int comparisonDays = periodDays(comparisonPeriod.startDate(), comparisonPeriod.endDate());
        List<RegisteredReportMetricComparisonVO> metrics = metricSpecs(current).stream()
                .map(spec -> compareMetric(spec, current, comparison, currentDays, comparisonDays))
                .toList();
        List<String> notes = new ArrayList<>();
        notes.add("两期使用相同报表定义、指标版本和筛选条件。变化只描述登记数值差异，不代表改善、恶化或因果关系。");
        if (currentDays != comparisonDays) {
            notes.add("两期天数不同；可累加指标同时提供日均值，避免直接用期间总量判断变化。");
        }

        return current.toBuilder()
                .comparison(RegisteredReportComparisonVO.builder()
                        .comparisonMode(mode)
                        .comparisonLabel(PREVIOUS_PERIOD.equals(mode) ? "上一等长期间" : "指定对比期间")
                        .currentStartDate(query.getStartDate())
                        .currentEndDate(query.getEndDate())
                        .currentDateRangeLabel(dateRangeLabel(query.getStartDate(), query.getEndDate()))
                        .comparisonStartDate(comparisonPeriod.startDate())
                        .comparisonEndDate(comparisonPeriod.endDate())
                        .comparisonDateRangeLabel(dateRangeLabel(
                                comparisonPeriod.startDate(), comparisonPeriod.endDate()))
                        .currentPeriodDays(currentDays)
                        .comparisonPeriodDays(comparisonDays)
                        .differentPeriodLengths(currentDays != comparisonDays)
                        .comparisonDataAsOf(comparison.getDataAsOf())
                        .metrics(metrics)
                        .notes(List.copyOf(notes))
                        .build())
                .build();
    }

    private static String normalizeMode(String value) {
        if (value == null || value.isBlank() || "NONE".equalsIgnoreCase(value.trim())) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (!PREVIOUS_PERIOD.equals(normalized) && !CUSTOM.equals(normalized)) {
            throw new BusinessException(400, "当前跨期比较方式不受支持");
        }
        return normalized;
    }

    private static void rejectOrphanComparisonDates(RegisteredReportRunQueryDTO query) {
        if (query != null
                && (query.getComparisonStartDate() != null || query.getComparisonEndDate() != null)) {
            throw new BusinessException(400, "指定对比日期时必须选择自定义对比方式");
        }
    }

    private static Period comparisonPeriod(RegisteredReportRunQueryDTO query, String mode) {
        if (PREVIOUS_PERIOD.equals(mode)) {
            if (query.getComparisonStartDate() != null || query.getComparisonEndDate() != null) {
                throw new BusinessException(400, "上一等长期间不接受额外的自定义日期");
            }
            int days = periodDays(query.getStartDate(), query.getEndDate());
            LocalDate comparisonEnd = query.getStartDate().minusDays(1);
            return new Period(comparisonEnd.minusDays(days - 1L), comparisonEnd);
        }
        LocalDate comparisonStart = query.getComparisonStartDate();
        LocalDate comparisonEnd = query.getComparisonEndDate();
        if (comparisonStart == null || comparisonEnd == null) {
            throw new BusinessException(400, "自定义对比必须同时提供开始日期和结束日期");
        }
        if (comparisonStart.isAfter(comparisonEnd)) {
            throw new BusinessException(400, "对比期开始日期不能晚于结束日期");
        }
        boolean overlaps = !comparisonEnd.isBefore(query.getStartDate())
                && !comparisonStart.isAfter(query.getEndDate());
        if (overlaps) {
            throw new BusinessException(400, "本期与对比期不能重叠");
        }
        return new Period(comparisonStart, comparisonEnd);
    }

    private static RegisteredReportRunQueryDTO copyForComparison(
            RegisteredReportRunQueryDTO source,
            Period comparisonPeriod) {
        RegisteredReportRunQueryDTO copy = new RegisteredReportRunQueryDTO();
        copy.setReportDefinitionId(source.getReportDefinitionId());
        copy.setReportVersion(source.getReportVersion());
        copy.setStartDate(comparisonPeriod.startDate());
        copy.setEndDate(comparisonPeriod.endDate());
        copy.setProductQuery(source.getProductQuery());
        copy.setMetricKey(source.getMetricKey());
        copy.setTaskType(source.getTaskType());
        return copy;
    }

    private static void assertCompatible(RegisteredReportRunVO current, RegisteredReportRunVO comparison) {
        if (comparison == null
                || !Objects.equals(current.getReportDefinitionId(), comparison.getReportDefinitionId())
                || current.getReportVersion() != comparison.getReportVersion()
                || !Objects.equals(current.getMetricDefinitionVersion(), comparison.getMetricDefinitionVersion())
                || !Objects.equals(current.getDataScope(), comparison.getDataScope())) {
            throw new BusinessException(409, "本期与对比期的报表口径不一致，请重新生成");
        }
    }

    private static List<MetricSpec> metricSpecs(RegisteredReportRunVO report) {
        return switch (report.getReportDefinitionId()) {
            case RegisteredReportServiceImpl.DAILY_PRODUCTION_REPORT_ID -> List.of(
                    spec("totalWeightKg", "已登记产出重量", "kg", true,
                            value -> value.getMetrics() == null ? null : value.getMetrics().getTotalWeightKg()),
                    spec("outputRecordCount", "产出记录数", "条", true,
                            value -> value.getMetrics() == null ? null : decimal(value.getMetrics().getOutputRecordCount())),
                    spec("productionOrderCount", "有产出登记的生产订单数", "单", true,
                            value -> value.getMetrics() == null ? null : decimal(value.getMetrics().getProductionOrderCount())),
                    spec("totalPieces", "已登记折算总件数", "件", true,
                            value -> value.getMetrics() == null ? null : decimal(value.getMetrics().getTotalPieces())));
            case QualityAssayRegisteredReportService.REPORT_ID -> List.of(
                    spec("assayRecordCount", "化验记录数", "条", true,
                            value -> value.getQualityMetrics() == null ? null : decimal(value.getQualityMetrics().getAssayRecordCount())),
                    spec("passRatePercent", "明确判定合格率", "%", false,
                            value -> value.getQualityMetrics() == null ? null : value.getQualityMetrics().getPassRatePercent()),
                    spec("failCount", "不合格记录数", "条", true,
                            value -> value.getQualityMetrics() == null ? null : decimal(value.getQualityMetrics().getFailCount())),
                    spec("noStandardCount", "无适用标准记录数", "条", true,
                            value -> value.getQualityMetrics() == null ? null : decimal(value.getQualityMetrics().getNoStandardCount())));
            case QualityMetricRegisteredReportService.REPORT_ID -> {
                String unit = report.getMetricTrendSummary() == null
                        ? ""
                        : Objects.toString(report.getMetricTrendSummary().getUnit(), "");
                yield List.of(
                        spec("sampleCount", "指标实测样本数", "个", true,
                                value -> value.getMetricTrendSummary() == null ? null : decimal(value.getMetricTrendSummary().getSampleCount())),
                        spec("averageValue", "指标均值", unit, false,
                                value -> value.getMetricTrendSummary() == null ? null : value.getMetricTrendSummary().getAverageValue()),
                        spec("medianValue", "指标中位数", unit, false,
                                value -> value.getMetricTrendSummary() == null ? null : value.getMetricTrendSummary().getMedianValue()),
                        spec("withinStandardRatePercent", "历史标准下达标率", "%", false,
                                value -> value.getMetricTrendSummary() == null ? null : value.getMetricTrendSummary().getWithinStandardRatePercent()));
            }
            case ProductionInputOutputRegisteredReportService.REPORT_ID -> List.of(
                    spec("materialInputWeightKg", "按实际领料时间登记的领料重量", "kg", true,
                            value -> value.getProductionFlowMetrics() == null ? null : value.getProductionFlowMetrics().getMaterialInputWeightKg()),
                    spec("stableOutputWeightKg", "按生产日期登记的稳定产出重量", "kg", true,
                            value -> value.getProductionFlowMetrics() == null ? null : value.getProductionFlowMetrics().getStableOutputWeightKg()),
                    spec("completedOrdersMissingInputCount", "已完成但缺少输入事实的订单数", "单", true,
                            value -> value.getProductionFlowMetrics() == null ? null : decimal(value.getProductionFlowMetrics().getCompletedOrdersMissingInputCount())),
                    spec("completedOrdersMissingOutputCount", "已完成但缺少稳定产出的订单数", "单", true,
                            value -> value.getProductionFlowMetrics() == null ? null : decimal(value.getProductionFlowMetrics().getCompletedOrdersMissingOutputCount())));
            case PalletTaskCycleRegisteredReportService.REPORT_ID -> List.of(
                    spec("completedTaskCount", "已完成任务数", "条", true,
                            value -> value.getPalletTaskCycleMetrics() == null ? null : decimal(value.getPalletTaskCycleMetrics().getCompletedTaskCount())),
                    spec("medianDurationSeconds", "完成耗时中位数", "秒", false,
                            value -> value.getPalletTaskCycleMetrics() == null ? null : decimal(value.getPalletTaskCycleMetrics().getMedianDurationSeconds())),
                    spec("p90DurationSeconds", "完成耗时 P90", "秒", false,
                            value -> value.getPalletTaskCycleMetrics() == null ? null : decimal(value.getPalletTaskCycleMetrics().getP90DurationSeconds())),
                    spec("inProgressTaskCount", "进行中任务数", "条", true,
                            value -> value.getPalletTaskCycleMetrics() == null ? null : decimal(value.getPalletTaskCycleMetrics().getInProgressTaskCount())));
            case InventoryTrendRegisteredReportService.REPORT_ID -> List.of(
                    spec("closingPieces", "期末库存折算件数", "件", false,
                            value -> value.getInventoryTrendMetrics() == null ? null : decimal(value.getInventoryTrendMetrics().getClosingPieces())),
                    spec("closingWeightKg", "期末库存重量", "kg", false,
                            value -> value.getInventoryTrendMetrics() == null ? null : value.getInventoryTrendMetrics().getClosingWeightKg()),
                    spec("netChangePieces", "期间库存净变化", "件", false,
                            value -> value.getInventoryTrendMetrics() == null ? null : decimal(value.getInventoryTrendMetrics().getNetChangePieces())),
                    spec("netChangeWeightKg", "期间库存重量净变化", "kg", false,
                            value -> value.getInventoryTrendMetrics() == null ? null : value.getInventoryTrendMetrics().getNetChangeWeightKg()));
            default -> throw new BusinessException(400, "当前报表未登记跨期比较指标");
        };
    }

    private static RegisteredReportMetricComparisonVO compareMetric(
            MetricSpec spec,
            RegisteredReportRunVO current,
            RegisteredReportRunVO comparison,
            int currentDays,
            int comparisonDays) {
        BigDecimal currentValue = spec.extractor().apply(current);
        BigDecimal comparisonValue = spec.extractor().apply(comparison);
        BigDecimal absoluteChange = difference(currentValue, comparisonValue);
        BigDecimal percentChange = percentChange(currentValue, comparisonValue);
        BigDecimal currentDailyAverage = spec.additive() ? divide(currentValue, currentDays) : null;
        BigDecimal comparisonDailyAverage = spec.additive() ? divide(comparisonValue, comparisonDays) : null;
        BigDecimal dailyAverageChange = difference(currentDailyAverage, comparisonDailyAverage);
        BigDecimal dailyAveragePercentChange = percentChange(currentDailyAverage, comparisonDailyAverage);
        String note = null;
        if (currentValue == null || comparisonValue == null) {
            note = "至少一期缺少可比较值，未计算变化。";
        } else if (comparisonValue.compareTo(BigDecimal.ZERO) == 0) {
            note = "对比期为 0，未计算变化率。";
        }
        return RegisteredReportMetricComparisonVO.builder()
                .metricCode(spec.code())
                .metricLabel(spec.label())
                .unit(spec.unit())
                .additive(spec.additive())
                .currentValue(currentValue)
                .comparisonValue(comparisonValue)
                .absoluteChange(absoluteChange)
                .percentChange(percentChange)
                .currentDailyAverage(currentDailyAverage)
                .comparisonDailyAverage(comparisonDailyAverage)
                .dailyAverageAbsoluteChange(dailyAverageChange)
                .dailyAveragePercentChange(dailyAveragePercentChange)
                .note(note)
                .build();
    }

    private static MetricSpec spec(
            String code,
            String label,
            String unit,
            boolean additive,
            Function<RegisteredReportRunVO, BigDecimal> extractor) {
        return new MetricSpec(code, label, unit, additive, extractor);
    }

    private static BigDecimal decimal(Number value) {
        return value == null ? null : new BigDecimal(value.toString());
    }

    private static BigDecimal difference(BigDecimal current, BigDecimal comparison) {
        return current == null || comparison == null ? null : normalized(current.subtract(comparison));
    }

    private static BigDecimal percentChange(BigDecimal current, BigDecimal comparison) {
        if (current == null || comparison == null || comparison.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return normalized(current.subtract(comparison)
                .divide(comparison.abs(), 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)));
    }

    private static BigDecimal divide(BigDecimal value, int days) {
        return value == null ? null : normalized(value.divide(BigDecimal.valueOf(days), 8, RoundingMode.HALF_UP));
    }

    private static BigDecimal normalized(BigDecimal value) {
        BigDecimal rounded = value.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
        return rounded.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : rounded;
    }

    private static int periodDays(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new BusinessException(400, "报表日期范围无效");
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate) + 1);
    }

    private static String dateRangeLabel(LocalDate startDate, LocalDate endDate) {
        return startDate.equals(endDate) ? startDate.toString() : startDate + " 至 " + endDate;
    }

    private record Period(LocalDate startDate, LocalDate endDate) {
    }

    private record MetricSpec(
            String code,
            String label,
            String unit,
            boolean additive,
            Function<RegisteredReportRunVO, BigDecimal> extractor) {
    }
}

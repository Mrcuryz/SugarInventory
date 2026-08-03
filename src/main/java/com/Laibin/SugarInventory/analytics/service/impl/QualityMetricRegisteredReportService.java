package com.Laibin.SugarInventory.analytics.service.impl;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.vo.QualityAssayReportRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportDataQualityVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportMetricPeriodPointVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportMetricProductBreakdownVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportMetricStandardBreakdownVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportMetricSummaryVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;
import com.Laibin.SugarInventory.analytics.mapper.QualityAssayReportMapper;
import com.Laibin.SugarInventory.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QualityMetricRegisteredReportService {
    public static final String REPORT_ID = "quality_metric_trend_v1";
    public static final int REPORT_VERSION = 1;
    public static final String METRIC_DEFINITION_VERSION = "quality_assay_metric_statistics_v1";
    private static final int MAX_RANGE_DAYS = 366;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final QualityAssayReportMapper mapper;
    private final ObjectMapper objectMapper;

    public RegisteredReportRunVO run(RegisteredReportRunQueryDTO query) {
        ValidatedQuery validated = validate(query);
        List<QualityAssayReportRowVO> queriedRows = mapper.listQualityAssaysForReport(
                validated.startDate(), validated.endDate(), validated.productQuery());
        List<QualityAssayReportRowVO> rows = queriedRows == null ? List.of() : queriedRows;

        MetricAggregate totalValues = new MetricAggregate();
        Map<String, PeriodAggregate> periods = initializePeriods(
                validated.startDate(), validated.endDate(), validated.seriesGranularity());
        Map<String, ProductAggregate> products = new LinkedHashMap<>();
        Map<String, StandardAggregate> standards = new LinkedHashMap<>();

        int missingValueCount = 0;
        int withoutComparableStandardCount = 0;
        int unexpectedMetricUnitCount = 0;
        int withinStandardCount = 0;
        int outOfStandardCount = 0;
        int lateRecordedCount = 0;
        LocalDateTime latestRecordAt = null;

        for (QualityAssayReportRowVO row : rows) {
            BigDecimal value = validated.metric().value(row);
            if (value == null) {
                missingValueCount++;
            } else {
                totalValues.add(value);
                String periodKey = periodKey(row.getSampleDate(), validated.seriesGranularity());
                PeriodAggregate period = periods.get(periodKey);
                if (period != null) {
                    period.values().add(value);
                }
                String productName = cleanProductName(row.getProductName());
                String productKey = String.valueOf(row.getProductId()) + "|" + productName;
                products.computeIfAbsent(
                                productKey,
                                ignored -> new ProductAggregate(productName, new MetricAggregate()))
                        .values()
                        .add(value);

                StandardMetric standardMetric = historicalMetricStandard(row, validated.metric());
                if (standardMetric == null) {
                    withoutComparableStandardCount++;
                } else if (!sameUnit(validated.metric().unit(), standardMetric.unit())) {
                    withoutComparableStandardCount++;
                    unexpectedMetricUnitCount++;
                } else {
                    boolean within = standardMetric.matches(value);
                    if (within) {
                        withinStandardCount++;
                    } else {
                        outOfStandardCount++;
                    }
                    String standardKey = standardMetric.standardLabel() + "|" + standardMetric.rangeLabel();
                    standards.computeIfAbsent(
                                    standardKey,
                                    ignored -> new StandardAggregate(
                                            standardMetric.standardLabel(),
                                            standardMetric.rangeLabel(),
                                            validated.metric().unit()))
                            .add(within);
                }
            }

            if (row.getSampleDate() != null
                    && row.getCreatedAt() != null
                    && row.getCreatedAt().toLocalDate().isAfter(row.getSampleDate())) {
                lateRecordedCount++;
            }
            if (row.getCreatedAt() != null
                    && (latestRecordAt == null || row.getCreatedAt().isAfter(latestRecordAt))) {
                latestRecordAt = row.getCreatedAt();
            }
        }

        List<String> qualityNotes = new ArrayList<>();
        if (missingValueCount > 0) {
            qualityNotes.add(missingValueCount + " 条化验记录缺少“"
                    + validated.metric().displayName() + "”实测值，未进入数值统计。");
        }
        if (withoutComparableStandardCount > 0) {
            qualityNotes.add(withoutComparableStandardCount + " 个实测值没有可比较的历史适用标准，"
                    + "仍进入原始数值统计，但不进入指标达标率。");
        }
        if (unexpectedMetricUnitCount > 0) {
            qualityNotes.add(unexpectedMetricUnitCount + " 条历史标准的指标单位与报表登记单位不一致，"
                    + "已停止跨单位比较。");
        }
        if (lateRecordedCount > 0) {
            qualityNotes.add(lateRecordedCount + " 条化验记录晚于生产日期录入；趋势仍按生产日期统计。");
        }

        int comparableStandardCount = withinStandardCount + outOfStandardCount;
        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("businessDate", dateRangeLabel(validated.startDate(), validated.endDate()));
        filters.put("productScope", validated.productQuery() == null
                ? "全部产品"
                : "产品名称包含“" + validated.productQuery() + "”");
        filters.put("metricScope", validated.metric().displayName()
                + (validated.metric().unit().isBlank() ? "" : "（" + validated.metric().unit() + "）"));

        RegisteredReportMetricSummaryVO summary = RegisteredReportMetricSummaryVO.builder()
                .metricKey(validated.metric().key())
                .metricName(validated.metric().displayName())
                .unit(validated.metric().unit())
                .assayRecordCount(rows.size())
                .sampleCount(totalValues.size())
                .missingValueCount(missingValueCount)
                .comparableStandardCount(comparableStandardCount)
                .withinStandardCount(withinStandardCount)
                .outOfStandardCount(outOfStandardCount)
                .withoutComparableStandardCount(withoutComparableStandardCount)
                .withinStandardRatePercent(percent(withinStandardCount, comparableStandardCount))
                .averageValue(totalValues.average())
                .medianValue(totalValues.median())
                .minimumValue(totalValues.minimum())
                .maximumValue(totalValues.maximum())
                .p10Value(totalValues.percentile(0.10))
                .p90Value(totalValues.percentile(0.90))
                .build();

        return RegisteredReportRunVO.builder()
                .dataScope("REGISTERED_QUALITY_ASSAY_METRIC")
                .reportRunId(newReportRunId())
                .reportDefinitionId(REPORT_ID)
                .reportVersion(REPORT_VERSION)
                .reportName("化验单指标趋势")
                .metricDefinitionVersion(METRIC_DEFINITION_VERSION)
                .startDate(validated.startDate())
                .endDate(validated.endDate())
                .dateRangeLabel(dateRangeLabel(validated.startDate(), validated.endDate()))
                .dataAsOf(LocalDateTime.now(BUSINESS_ZONE))
                .latestRecordAt(latestRecordAt)
                .filtersApplied(filters)
                .dailySeries(List.of())
                .productBreakdowns(List.of())
                .qualitySeries(List.of())
                .qualityProductBreakdowns(List.of())
                .standardBreakdowns(List.of())
                .metricTrendSummary(summary)
                .seriesGranularity(validated.seriesGranularity())
                .metricSeries(periods.values().stream()
                        .map(PeriodAggregate::toPoint)
                        .toList())
                .metricProductBreakdowns(products.values().stream()
                        .filter(product -> product.values().size() > 0)
                        .map(ProductAggregate::toBreakdown)
                        .toList())
                .metricStandardBreakdowns(standards.values().stream()
                        .map(StandardAggregate::toBreakdown)
                        .toList())
                .dataQuality(RegisteredReportDataQualityVO.builder()
                        .partial(missingValueCount > 0
                                || withoutComparableStandardCount > 0
                                || unexpectedMetricUnitCount > 0)
                        .rowsMissingWeight(0)
                        .rowsMissingPieceConversion(0)
                        .rowsMissingProductName(0)
                        .rowsMissingMetricValue(missingValueCount)
                        .rowsWithoutComparableMetricStandard(withoutComparableStandardCount)
                        .rowsWithUnexpectedMetricUnit(unexpectedMetricUnitCount)
                        .lateRecordedCount(lateRecordedCount)
                        .notes(List.copyOf(qualityNotes))
                        .build())
                .limitations(List.of(
                        "生产日期采用 assay.sample_date；录入时间只用于识别晚录数据。",
                        "原始指标统计包含有实测值的记录；指标达标率只统计具有可比较历史标准的记录。",
                        "历史标准按每条化验记录保存的标准快照读取，不使用当前标准重算。",
                        "不同单位的数据不得合并或比较；发现单位不一致时停止标准比较并提示数据质量。",
                        "样本不足时只展示登记事实，不输出改善、恶化、原因或预测结论。"
                ))
                .build();
    }

    private ValidatedQuery validate(RegisteredReportRunQueryDTO query) {
        if (query == null) {
            throw new BusinessException(400, "报表查询条件不能为空");
        }
        if (!REPORT_ID.equals(trimToNull(query.getReportDefinitionId()))) {
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
            throw new BusinessException(400, "化验单指标趋势单次最多查询 366 天");
        }
        String productQuery = trimToNull(query.getProductQuery());
        if (productQuery != null && productQuery.length() > 100) {
            throw new BusinessException(400, "产品筛选条件不能超过 100 个字符");
        }
        MetricDefinition metric = MetricDefinition.fromKey(trimToNull(query.getMetricKey()));
        if (metric == null) {
            throw new BusinessException(400, "请选择受支持的化验指标");
        }
        return new ValidatedQuery(
                query.getStartDate(),
                query.getEndDate(),
                productQuery,
                metric,
                rangeDays <= 31 ? "DAY" : "MONTH");
    }

    private StandardMetric historicalMetricStandard(
            QualityAssayReportRowVO row,
            MetricDefinition metric) {
        String snapshot = trimToNull(row.getStandardSnapshotJson());
        if (snapshot == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(snapshot);
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                return null;
            }
            for (JsonNode item : items) {
                if (!metric.key().equals(item.path("metricCode").asText())) {
                    continue;
                }
                String compareType = item.path("compareType").asText("");
                BigDecimal minimum = decimalOrNull(item.get("minValue"));
                BigDecimal maximum = decimalOrNull(item.get("maxValue"));
                String unit = item.path("unit").asText("");
                String standardName = trimToNull(row.getAppliedStandardName());
                if (standardName == null) {
                    standardName = trimToNull(root.path("standardName").asText(null));
                }
                Integer version = row.getAppliedStandardVersion();
                if (version == null && root.path("version").canConvertToInt()) {
                    version = root.path("version").asInt();
                }
                String standardLabel = (standardName == null ? "历史标准" : standardName)
                        + (version == null ? "" : " v" + version);
                StandardMetric result = new StandardMetric(
                        standardLabel,
                        compareType,
                        minimum,
                        maximum,
                        unit);
                return result.isUsable() ? result : null;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static Map<String, PeriodAggregate> initializePeriods(
            LocalDate startDate,
            LocalDate endDate,
            String granularity) {
        Map<String, PeriodAggregate> periods = new LinkedHashMap<>();
        if ("DAY".equals(granularity)) {
            for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
                periods.put(
                        day.toString(),
                        new PeriodAggregate(day.toString(), day, day, new MetricAggregate()));
            }
            return periods;
        }
        for (YearMonth month = YearMonth.from(startDate);
             !month.isAfter(YearMonth.from(endDate));
             month = month.plusMonths(1)) {
            LocalDate periodStart = month.atDay(1).isBefore(startDate) ? startDate : month.atDay(1);
            LocalDate periodEnd = month.atEndOfMonth().isAfter(endDate) ? endDate : month.atEndOfMonth();
            periods.put(
                    month.toString(),
                    new PeriodAggregate(
                            month.toString(),
                            periodStart,
                            periodEnd,
                            new MetricAggregate()));
        }
        return periods;
    }

    private static String periodKey(LocalDate sampleDate, String granularity) {
        if (sampleDate == null) {
            return "";
        }
        return "DAY".equals(granularity)
                ? sampleDate.toString()
                : YearMonth.from(sampleDate).toString();
    }

    private static BigDecimal decimalOrNull(JsonNode node) {
        return node == null || node.isNull() || !node.isNumber()
                ? null
                : node.decimalValue();
    }

    private static BigDecimal percent(int numerator, int denominator) {
        if (denominator <= 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    private static boolean sameUnit(String registeredUnit, String historicalUnit) {
        return normalizeUnit(registeredUnit).equals(normalizeUnit(historicalUnit));
    }

    private static String normalizeUnit(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase();
        return normalized.equals("-")
                || normalized.equals("无")
                || normalized.equals("none")
                ? ""
                : normalized;
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

    private static String newReportRunId() {
        return "report_run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private enum MetricDefinition {
        COLOR_VALUE("color_value", "色值", "IU"),
        REDUCING_SUGAR("reducing_sugar", "还原糖分", "g/100g"),
        DRY_WEIGHT_LOSS("dry_weight_loss", "干燥失重", "g/100g"),
        CONDUCTIVITY_ASH("conductivity_ash", "电导灰分", "g/100g"),
        SUCROSE("sucrose", "蔗糖分", "g/100g"),
        INSOLUBLE_IMPURITY("insoluble_impurity", "不溶于水杂质", "mg/kg"),
        PH("ph", "pH", "");

        private final String key;
        private final String displayName;
        private final String unit;

        MetricDefinition(String key, String displayName, String unit) {
            this.key = key;
            this.displayName = displayName;
            this.unit = unit;
        }

        String key() {
            return key;
        }

        String displayName() {
            return displayName;
        }

        String unit() {
            return unit;
        }

        BigDecimal value(QualityAssayReportRowVO row) {
            return switch (this) {
                case COLOR_VALUE -> row.getColorValue();
                case REDUCING_SUGAR -> row.getReducingSugar();
                case DRY_WEIGHT_LOSS -> row.getDryWeight();
                case CONDUCTIVITY_ASH -> row.getConductivityAsh();
                case SUCROSE -> row.getSucrose();
                case INSOLUBLE_IMPURITY -> row.getInsolubleImpurity();
                case PH -> row.getPhValue();
            };
        }

        static MetricDefinition fromKey(String key) {
            if (key == null) {
                return null;
            }
            for (MetricDefinition value : values()) {
                if (value.key.equals(key)) {
                    return value;
                }
            }
            return null;
        }
    }

    private record ValidatedQuery(
            LocalDate startDate,
            LocalDate endDate,
            String productQuery,
            MetricDefinition metric,
            String seriesGranularity) {
    }

    private record PeriodAggregate(
            String label,
            LocalDate startDate,
            LocalDate endDate,
            MetricAggregate values) {
        RegisteredReportMetricPeriodPointVO toPoint() {
            return RegisteredReportMetricPeriodPointVO.builder()
                    .periodLabel(label)
                    .periodStart(startDate)
                    .periodEnd(endDate)
                    .sampleCount(values.size())
                    .averageValue(values.average())
                    .medianValue(values.median())
                    .minimumValue(values.minimum())
                    .maximumValue(values.maximum())
                    .build();
        }
    }

    private record ProductAggregate(String productName, MetricAggregate values) {
        RegisteredReportMetricProductBreakdownVO toBreakdown() {
            return RegisteredReportMetricProductBreakdownVO.builder()
                    .productName(productName)
                    .sampleCount(values.size())
                    .averageValue(values.average())
                    .medianValue(values.median())
                    .minimumValue(values.minimum())
                    .maximumValue(values.maximum())
                    .build();
        }
    }

    private static final class StandardAggregate {
        private final String standardLabel;
        private final String rangeLabel;
        private final String unit;
        private int sampleCount;
        private int withinStandardCount;
        private int outOfStandardCount;

        private StandardAggregate(String standardLabel, String rangeLabel, String unit) {
            this.standardLabel = standardLabel;
            this.rangeLabel = rangeLabel;
            this.unit = unit;
        }

        void add(boolean within) {
            sampleCount++;
            if (within) {
                withinStandardCount++;
            } else {
                outOfStandardCount++;
            }
        }

        RegisteredReportMetricStandardBreakdownVO toBreakdown() {
            return RegisteredReportMetricStandardBreakdownVO.builder()
                    .standardLabel(standardLabel)
                    .rangeLabel(rangeLabel)
                    .unit(unit)
                    .sampleCount(sampleCount)
                    .withinStandardCount(withinStandardCount)
                    .outOfStandardCount(outOfStandardCount)
                    .withinStandardRatePercent(percent(withinStandardCount, sampleCount))
                    .build();
        }
    }

    private record StandardMetric(
            String standardLabel,
            String compareType,
            BigDecimal minimum,
            BigDecimal maximum,
            String unit) {
        boolean isUsable() {
            return switch (compareType) {
                case "gte" -> minimum != null;
                case "lte" -> maximum != null;
                case "range" -> minimum != null && maximum != null;
                default -> false;
            };
        }

        boolean matches(BigDecimal value) {
            return switch (compareType) {
                case "gte" -> value.compareTo(minimum) >= 0;
                case "lte" -> value.compareTo(maximum) <= 0;
                case "range" -> value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
                default -> false;
            };
        }

        String rangeLabel() {
            return switch (compareType) {
                case "gte" -> "≥ " + plain(minimum);
                case "lte" -> "≤ " + plain(maximum);
                case "range" -> plain(minimum) + " - " + plain(maximum);
                default -> "未登记";
            };
        }
    }

    private static final class MetricAggregate {
        private final List<BigDecimal> values = new ArrayList<>();

        void add(BigDecimal value) {
            if (value != null) {
                values.add(value);
            }
        }

        int size() {
            return values.size();
        }

        BigDecimal average() {
            if (values.isEmpty()) {
                return null;
            }
            BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            return normalized(total.divide(BigDecimal.valueOf(values.size()), 3, RoundingMode.HALF_UP));
        }

        BigDecimal median() {
            if (values.isEmpty()) {
                return null;
            }
            List<BigDecimal> sorted = sorted();
            int middle = sorted.size() / 2;
            if (sorted.size() % 2 == 1) {
                return normalized(sorted.get(middle));
            }
            return normalized(sorted.get(middle - 1)
                    .add(sorted.get(middle))
                    .divide(BigDecimal.valueOf(2), 3, RoundingMode.HALF_UP));
        }

        BigDecimal minimum() {
            return values.stream().min(Comparator.naturalOrder()).map(QualityMetricRegisteredReportService::normalized).orElse(null);
        }

        BigDecimal maximum() {
            return values.stream().max(Comparator.naturalOrder()).map(QualityMetricRegisteredReportService::normalized).orElse(null);
        }

        BigDecimal percentile(double percentile) {
            if (values.isEmpty()) {
                return null;
            }
            List<BigDecimal> sorted = sorted();
            int index = Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1);
            return normalized(sorted.get(Math.min(index, sorted.size() - 1)));
        }

        private List<BigDecimal> sorted() {
            return values.stream().sorted().toList();
        }
    }

    private static BigDecimal normalized(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : normalized(value).toPlainString();
    }
}

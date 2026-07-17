package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayReportDetailQueryDTO;
import com.Laibin.SugarInventory.domain.vo.AssayFailedMetricVO;
import com.Laibin.SugarInventory.domain.vo.AssayReportDetailVO;
import com.Laibin.SugarInventory.domain.vo.AssayReportMetricVO;
import com.Laibin.SugarInventory.domain.vo.AssayVO;
import com.Laibin.SugarInventory.domain.vo.QualityStandardItemVO;
import com.Laibin.SugarInventory.service.AssayReportDetailService;
import com.Laibin.SugarInventory.service.AssayService;
import com.Laibin.SugarInventory.service.support.AssayReportRefCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssayReportDetailServiceImpl implements AssayReportDetailService {
    private static final List<MetricDefinition> METRICS = List.of(
            new MetricDefinition("color_value", "色值"),
            new MetricDefinition("reducing_sugar", "还原糖分"),
            new MetricDefinition("dry_weight_loss", "干燥失重"),
            new MetricDefinition("conductivity_ash", "电导灰分"),
            new MetricDefinition("sucrose", "蔗糖分"),
            new MetricDefinition("insoluble_impurity", "不溶于水杂质"),
            new MetricDefinition("ph", "pH")
    );

    private final AssayService assayService;
    private final AssayReportRefCodec reportRefCodec;

    @Override
    @Transactional(readOnly = true)
    public AssayReportDetailVO getReportDetail(AssayReportDetailQueryDTO query) {
        if (query == null || query.getReportRef() == null || query.getReportRef().isBlank()) {
            throw new BusinessException(400, "reportRef 不能为空");
        }
        AssayReportRefCodec.DecodedRef decodedRef = reportRefCodec.decode(query.getReportRef().trim());
        AssayVO assay = assayService.getAssayById(decodedRef.assayId());
        reportRefCodec.validate(decodedRef, assay.getProductId(), assay.getSampleDate());

        boolean includeMetrics = query.getIncludeMetrics() == null || query.getIncludeMetrics();
        boolean includeStandardSnapshot = query.getIncludeStandardSnapshot() == null || query.getIncludeStandardSnapshot();
        String productLabel = assay.getProductName() == null || assay.getProductName().isBlank()
                ? "未命名产品"
                : assay.getProductName();
        String standardLabel = standardLabel(assay);
        List<AssayReportMetricVO> metrics = includeMetrics ? metrics(assay, includeStandardSnapshot) : List.of();
        return AssayReportDetailVO.builder()
                .reportRef(query.getReportRef().trim())
                .reportLabel(assay.getSampleDate() + " " + productLabel + "化验")
                .productLabel(productLabel)
                .sampleDate(assay.getSampleDate())
                .createdAt(assay.getCreatedAt())
                .judgeStatus(assay.getJudgeResult())
                .judgeLabel(judgeLabel(assay.getJudgeResult(), assay.getIsQualified()))
                .judgeMessage(assay.getJudgeMessage())
                .standardLabel(standardLabel)
                .metrics(metrics)
                .matchedStandards(assay.getMatchedStandards() == null ? List.of() : assay.getMatchedStandards())
                .riskLabels(riskLabels(assay))
                .notes(notes(assay))
                .summaryText(summaryText(productLabel, assay, metrics))
                .build();
    }

    private List<AssayReportMetricVO> metrics(AssayVO assay, boolean includeStandardSnapshot) {
        Map<String, QualityStandardItemVO> standardItems = includeStandardSnapshot && assay.getStandardSnapshot() != null
                && assay.getStandardSnapshot().getItems() != null
                ? assay.getStandardSnapshot().getItems().stream()
                .filter(item -> item.getMetricCode() != null)
                .collect(Collectors.toMap(
                        item -> normalizeMetricCode(item.getMetricCode()),
                        Function.identity(),
                        (left, right) -> left
                ))
                : Map.of();
        Map<String, AssayFailedMetricVO> failedMetrics = assay.getFailedMetrics() == null
                ? Map.of()
                : assay.getFailedMetrics().stream()
                .filter(metric -> metric.getMetricCode() != null)
                .collect(Collectors.toMap(
                        metric -> normalizeMetricCode(metric.getMetricCode()),
                        Function.identity(),
                        (left, right) -> left
                ));

        List<AssayReportMetricVO> result = new ArrayList<>();
        for (MetricDefinition metric : METRICS) {
            BigDecimal actualValue = actualValue(assay, metric.code());
            QualityStandardItemVO standard = standardItems.get(metric.code());
            AssayFailedMetricVO failed = failedMetrics.get(metric.code());
            result.add(AssayReportMetricVO.builder()
                    .metricCode(metric.code())
                    .metricName(metric.name())
                    .actualValueText(valueText(actualValue, standard == null ? null : standard.getUnit()))
                    .standardRangeText(standard == null ? null : rangeText(standard))
                    .resultLabel(metricResultLabel(assay.getJudgeResult(), actualValue, standard, failed))
                    .reason(failed == null ? null : failed.getReason())
                    .build());
        }
        return result;
    }

    private BigDecimal actualValue(AssayVO assay, String metricCode) {
        return switch (metricCode) {
            case "color_value" -> assay.getColorValue();
            case "reducing_sugar" -> assay.getReducingSugar();
            case "dry_weight_loss" -> assay.getDryWeight();
            case "conductivity_ash" -> assay.getConductivityAsh();
            case "sucrose" -> assay.getSucrose();
            case "insoluble_impurity" -> assay.getInsolubleImpurity();
            case "ph" -> assay.getPhValue();
            default -> null;
        };
    }

    private String normalizeMetricCode(String metricCode) {
        if (metricCode == null) {
            return null;
        }
        return switch (metricCode) {
            case "dry_weight" -> "dry_weight_loss";
            case "ph_value" -> "ph";
            default -> metricCode;
        };
    }

    private String metricResultLabel(String judgeResult, BigDecimal actualValue, QualityStandardItemVO standard,
                                     AssayFailedMetricVO failed) {
        if (actualValue == null) {
            return "未填写";
        }
        if (failed != null) {
            return "不合格";
        }
        if ("NO_STANDARD".equals(judgeResult)) {
            return "无标准";
        }
        if ("MULTIPLE_CANDIDATES".equals(judgeResult)) {
            return "标准多候选";
        }
        if (standard == null) {
            return "未判定";
        }
        return "合格";
    }

    private String rangeText(QualityStandardItemVO item) {
        String unit = item.getUnit() == null ? "" : item.getUnit();
        String min = number(item.getMinValue());
        String max = number(item.getMaxValue());
        return switch (item.getCompareType() == null ? "" : item.getCompareType()) {
            case "lte" -> max == null ? null : "≤ " + max + unit;
            case "lt" -> max == null ? null : "< " + max + unit;
            case "gte" -> min == null ? null : "≥ " + min + unit;
            case "gt" -> min == null ? null : "> " + min + unit;
            default -> min != null && max != null ? min + " - " + max + unit : null;
        };
    }

    private String valueText(BigDecimal value, String unit) {
        if (value == null) {
            return null;
        }
        return number(value) + (unit == null ? "" : unit);
    }

    private String number(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String standardLabel(AssayVO assay) {
        if (assay.getAppliedStandardName() == null || assay.getAppliedStandardName().isBlank()) {
            return null;
        }
        return assay.getAppliedStandardVersion() == null
                ? assay.getAppliedStandardName()
                : assay.getAppliedStandardName() + " v" + assay.getAppliedStandardVersion();
    }

    private String judgeLabel(String judgeResult, String fallback) {
        return switch (judgeResult == null ? "" : judgeResult) {
            case "PASS" -> "合格";
            case "FAIL" -> "不合格";
            case "NO_STANDARD" -> "无标准";
            case "MULTIPLE_CANDIDATES" -> "标准多候选";
            default -> fallback == null || fallback.isBlank() ? "未知" : fallback;
        };
    }

    private List<String> riskLabels(AssayVO assay) {
        List<String> labels = new ArrayList<>();
        if ("FAIL".equals(assay.getJudgeResult())) {
            labels.add("存在不合格指标");
        }
        if ("NO_STANDARD".equals(assay.getJudgeResult())) {
            labels.add("无标准，无法自动判定");
        }
        if ("MULTIPLE_CANDIDATES".equals(assay.getJudgeResult())) {
            labels.add("标准多候选，需要人工确认");
        }
        return labels;
    }

    private List<String> notes(AssayVO assay) {
        List<String> notes = new ArrayList<>();
        if ("NO_STANDARD".equals(assay.getJudgeResult())) {
            notes.add("当前没有适用的化验标准，因此暂时无法自动判定；这不代表产品不合格。");
        }
        if (assay.getStandardSnapshot() == null) {
            notes.add("本次化验未保存可展示的标准信息。");
        }
        return notes;
    }

    private String summaryText(String productLabel, AssayVO assay, List<AssayReportMetricVO> metrics) {
        String judgeLabel = judgeLabel(assay.getJudgeResult(), assay.getIsQualified());
        if ("FAIL".equals(assay.getJudgeResult())) {
            String failedNames = metrics.stream()
                    .filter(metric -> "不合格".equals(metric.getResultLabel()))
                    .map(AssayReportMetricVO::getMetricName)
                    .limit(5)
                    .collect(Collectors.joining("、"));
            if (!failedNames.isBlank()) {
                return productLabel + "本次化验不合格，主要异常指标为" + failedNames + "。";
            }
        }
        return productLabel + "本次化验判定为" + judgeLabel + "。";
    }

    private record MetricDefinition(String code, String name) {
    }
}

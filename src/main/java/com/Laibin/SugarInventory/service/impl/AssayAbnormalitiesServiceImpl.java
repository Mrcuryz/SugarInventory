package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayAbnormalitiesQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssayRecordsQueryDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.vo.AssayAbnormalitiesVO;
import com.Laibin.SugarInventory.domain.vo.AssayAbnormalityGroupVO;
import com.Laibin.SugarInventory.mapper.AssayAbnormalitiesMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.model.AssayAbnormalityGroupRow;
import com.Laibin.SugarInventory.mapper.model.AssayAbnormalitySummaryRow;
import com.Laibin.SugarInventory.service.AssayAbnormalitiesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AssayAbnormalitiesServiceImpl implements AssayAbnormalitiesService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final List<String> DEFAULT_ABNORMAL_TYPES = List.of("FAILED", "NO_STANDARD", "MULTIPLE_CANDIDATES");

    private final AssayAbnormalitiesMapper assayAbnormalitiesMapper;
    private final ProductMapper productMapper;

    @Override
    @Transactional(readOnly = true)
    public AssayAbnormalitiesVO queryAbnormalities(AssayAbnormalitiesQueryDTO query) {
        normalizeAndValidate(query);
        Product singleProduct = resolveSingleProduct(query.getProductScope());
        AssayAbnormalitySummaryRow summary = assayAbnormalitiesMapper.selectSummary(query);
        if (summary == null) {
            summary = new AssayAbnormalitySummaryRow();
        }
        List<AssayAbnormalityGroupRow> groupRows = assayAbnormalitiesMapper.selectGroups(query);
        List<AssayAbnormalityGroupVO> groups = (groupRows == null
                ? List.<AssayAbnormalityGroupRow>of()
                : groupRows).stream()
                .map(row -> toGroup(row, query.getGroupBy()))
                .toList();
        String scopeLabel = scopeLabel(query.getProductScope(), singleProduct);
        String dateRangeLabel = dateRangeLabel(query);
        return AssayAbnormalitiesVO.builder()
                .scopeLabel(scopeLabel)
                .dateRangeLabel(dateRangeLabel)
                .groupBy(query.getGroupBy())
                .total(value(summary.getTotal()))
                .failedCount(value(summary.getFailedCount()))
                .noStandardCount(value(summary.getNoStandardCount()))
                .multipleCandidatesCount(value(summary.getMultipleCandidatesCount()))
                .latestSampleDate(summary.getLatestSampleDate())
                .summaryText(summaryText(scopeLabel, dateRangeLabel, summary))
                .groups(groups)
                .riskLabels(riskLabels(summary))
                .notes(notes(query))
                .build();
    }

    private void normalizeAndValidate(AssayAbnormalitiesQueryDTO query) {
        if (query == null || query.getProductScope() == null) {
            throw new BusinessException(400, "化验异常查询产品范围不能为空");
        }
        validateProductScope(query.getProductScope());
        if (query.getLimit() == null) {
            query.setLimit(DEFAULT_LIMIT);
        }
        if (query.getLimit() < 1 || query.getLimit() > MAX_LIMIT) {
            throw new BusinessException(400, "limit 必须在 1 到 100 之间");
        }
        if (query.getGroupBy() == null) {
            query.setGroupBy("product");
        }
        if (!Set.of("product", "date", "abnormal_type", "metric").contains(query.getGroupBy())) {
            throw new BusinessException(400, "不支持的化验异常分组方式");
        }
        if (query.getAbnormalTypes() == null || query.getAbnormalTypes().isEmpty()) {
            query.setAbnormalTypes(DEFAULT_ABNORMAL_TYPES);
        }
        if (!Set.of("FAILED", "NO_STANDARD", "MULTIPLE_CANDIDATES").containsAll(query.getAbnormalTypes())) {
            throw new BusinessException(400, "不支持的化验异常类型");
        }
        query.setResolvedJudgeResults(query.getAbnormalTypes().stream()
                .map(type -> "FAILED".equals(type) ? "FAIL" : type)
                .distinct()
                .toList());
        normalizeDateRange(query);
    }

    private void validateProductScope(AssayRecordsQueryDTO.ProductScope scope) {
        if (scope.getType() == null) {
            throw new BusinessException(400, "产品范围类型不能为空");
        }
        switch (scope.getType()) {
            case "SINGLE_PRODUCT" -> {
                if (scope.getProductId() == null || scope.getProductId() <= 0) {
                    throw new BusinessException(400, "SINGLE_PRODUCT 必须提供有效 productId");
                }
            }
            case "EXACT_PRODUCT_NAME_GROUP" -> requireText(scope.getProductName(), 100, "产品名称组不能为空");
            case "PRODUCT_TYPE_GROUP" -> requireText(scope.getProductType(), 50, "产品大类不能为空");
            case "ALL" -> { }
            default -> throw new BusinessException(400, "不支持的产品范围");
        }
    }

    private void normalizeDateRange(AssayAbnormalitiesQueryDTO query) {
        AssayRecordsQueryDTO.DateRange range = query.getDateRange();
        if (range == null) {
            return;
        }
        LocalDate from;
        LocalDate to;
        switch (range.getType()) {
            case "EXACT" -> {
                if (range.getDate() == null) {
                    throw new BusinessException(400, "EXACT 日期范围必须提供 date");
                }
                from = range.getDate();
                to = range.getDate();
            }
            case "LAST_DAYS" -> {
                if (range.getDays() == null || range.getDays() < 1 || range.getDays() > 366) {
                    throw new BusinessException(400, "LAST_DAYS 天数必须在 1 到 366 之间");
                }
                to = LocalDate.now();
                from = to.minusDays(range.getDays() - 1L);
            }
            case "RANGE" -> {
                if (range.getFrom() == null || range.getTo() == null) {
                    throw new BusinessException(400, "RANGE 日期范围必须提供 from 和 to");
                }
                from = range.getFrom();
                to = range.getTo();
            }
            default -> throw new BusinessException(400, "不支持的日期范围类型");
        }
        if (from.isAfter(to)) {
            throw new BusinessException(400, "化验开始日期不能晚于结束日期");
        }
        query.setResolvedFrom(from);
        query.setResolvedTo(to);
    }

    private Product resolveSingleProduct(AssayRecordsQueryDTO.ProductScope scope) {
        if (!"SINGLE_PRODUCT".equals(scope.getType())) {
            return null;
        }
        Product product = productMapper.selectById(scope.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }

    private AssayAbnormalityGroupVO toGroup(AssayAbnormalityGroupRow row, String groupBy) {
        String label = switch (groupBy) {
            case "product" -> productLabel(row.getProductName(), row.getPackagingMethod(), row.getWeightPerPiece(), row.getPiecesPerPallet());
            case "abnormal_type" -> judgeLabel(row.getGroupLabel());
            case "metric" -> row.getGroupLabel() == null || row.getGroupLabel().isBlank() ? "未命名指标" : row.getGroupLabel();
            default -> row.getGroupLabel();
        };
        return AssayAbnormalityGroupVO.builder()
                .groupLabel(label)
                .total(value(row.getTotal()))
                .failedCount(value(row.getFailedCount()))
                .noStandardCount(value(row.getNoStandardCount()))
                .multipleCandidatesCount(value(row.getMultipleCandidatesCount()))
                .latestSampleDate(row.getLatestSampleDate())
                .riskLabels(riskLabels(row))
                .build();
    }

    private String scopeLabel(AssayRecordsQueryDTO.ProductScope scope, Product product) {
        return switch (scope.getType()) {
            case "SINGLE_PRODUCT" -> productLabel(product.getProductName(), product.getPackagingMethod(),
                    product.getWeightPerPiece(), product.getPiecesPerPallet());
            case "EXACT_PRODUCT_NAME_GROUP" -> "产品名称为“" + scope.getProductName().trim() + "”的全部规格";
            case "PRODUCT_TYPE_GROUP" -> "全部" + scope.getProductType().trim() + "大类";
            default -> "全部产品";
        };
    }

    private String dateRangeLabel(AssayAbnormalitiesQueryDTO query) {
        if (query.getDateRange() == null) {
            return "全部日期";
        }
        AssayRecordsQueryDTO.DateRange range = query.getDateRange();
        return switch (range.getType()) {
            case "EXACT" -> String.valueOf(range.getDate());
            case "LAST_DAYS" -> "最近" + range.getDays() + "天";
            case "RANGE" -> range.getFrom() + "至" + range.getTo();
            default -> "指定日期范围";
        };
    }

    private String summaryText(String scopeLabel, String dateRangeLabel, AssayAbnormalitySummaryRow summary) {
        long total = value(summary.getTotal());
        if (total == 0) {
            return "未查询到" + scopeLabel + "在" + dateRangeLabel + "的化验质量异常。";
        }
        return dateRangeLabel + scopeLabel + "发现 " + total + " 条化验质量异常，其中 "
                + value(summary.getFailedCount()) + " 条不合格，"
                + value(summary.getNoStandardCount()) + " 条无标准，"
                + value(summary.getMultipleCandidatesCount()) + " 条标准多候选。";
    }

    private List<String> riskLabels(AssayAbnormalitySummaryRow summary) {
        List<String> labels = new ArrayList<>();
        if (value(summary.getFailedCount()) > 0) {
            labels.add("存在不合格化验");
        }
        if (value(summary.getNoStandardCount()) > 0) {
            labels.add("存在无标准化验");
        }
        if (value(summary.getMultipleCandidatesCount()) > 0) {
            labels.add("存在标准多候选化验");
        }
        return labels;
    }

    private List<String> riskLabels(AssayAbnormalityGroupRow row) {
        AssayAbnormalitySummaryRow summary = new AssayAbnormalitySummaryRow();
        summary.setFailedCount(row.getFailedCount());
        summary.setNoStandardCount(row.getNoStandardCount());
        summary.setMultipleCandidatesCount(row.getMultipleCandidatesCount());
        return riskLabels(summary);
    }

    private List<String> notes(AssayAbnormalitiesQueryDTO query) {
        List<String> notes = new ArrayList<>();
        notes.add("日期范围按化验采样日期计算。");
        notes.add("无标准表示无法自动判定，不等同于不合格。");
        notes.add("无化验不属于本工具统计范围，需要使用缺化验产品工具或库存风险工具。");
        if ("metric".equals(query.getGroupBy())) {
            notes.add("按指标分组仅统计明确不合格化验中的异常指标。");
        }
        return notes;
    }

    private String productLabel(String name, String packaging, BigDecimal weight, Integer piecesPerPallet) {
        StringBuilder label = new StringBuilder(name == null ? "未命名产品" : name);
        if (packaging != null && !packaging.isBlank()
                && !label.toString().contains("（" + packaging + "）")
                && !label.toString().contains("(" + packaging + ")")) {
            label.append("（").append(packaging).append("）");
        }
        if (weight != null) {
            label.append(' ').append(weight.stripTrailingZeros().toPlainString()).append("kg/件");
        }
        if (piecesPerPallet != null) {
            label.append(' ').append(piecesPerPallet).append("件/板");
        }
        return label.toString();
    }

    private String judgeLabel(String judgeResult) {
        return switch (judgeResult == null ? "" : judgeResult) {
            case "FAIL" -> "不合格";
            case "NO_STANDARD" -> "无标准";
            case "MULTIPLE_CANDIDATES" -> "标准多候选";
            default -> judgeResult;
        };
    }

    private void requireText(String value, int maxLength, String message) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new BusinessException(400, message);
        }
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }
}

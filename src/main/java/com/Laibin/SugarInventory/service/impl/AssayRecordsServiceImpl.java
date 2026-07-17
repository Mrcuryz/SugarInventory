package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayRecordsQueryDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.vo.AssayRecordRowVO;
import com.Laibin.SugarInventory.domain.vo.AssayRecordsVO;
import com.Laibin.SugarInventory.mapper.AssayRecordsMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.model.AssayRecordRow;
import com.Laibin.SugarInventory.mapper.model.AssayRecordsSummaryRow;
import com.Laibin.SugarInventory.service.AssayRecordsService;
import com.Laibin.SugarInventory.service.support.AssayReportRefCodec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AssayRecordsServiceImpl implements AssayRecordsService {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final AssayRecordsMapper assayRecordsMapper;
    private final ProductMapper productMapper;
    private final ObjectMapper objectMapper;
    private final AssayReportRefCodec reportRefCodec;

    @Override
    @Transactional(readOnly = true)
    public AssayRecordsVO queryRecords(AssayRecordsQueryDTO query) {
        normalizeAndValidate(query);
        Product singleProduct = resolveSingleProduct(query.getProductScope());

        AssayRecordsSummaryRow summary = assayRecordsMapper.selectSummary(query);
        if (summary == null) {
            summary = new AssayRecordsSummaryRow();
        }
        int offset = (query.getPage() - 1) * query.getSize();
        List<AssayRecordRow> rows = assayRecordsMapper.selectRecords(query, offset, query.getSize());
        List<AssayRecordRowVO> records = (rows == null ? List.<AssayRecordRow>of() : rows).stream()
                .map(this::toRecord)
                .toList();
        String scopeLabel = scopeLabel(query.getProductScope(), singleProduct);
        String dateRangeLabel = dateRangeLabel(query);
        long total = value(summary.getTotal());
        return AssayRecordsVO.builder()
                .scopeLabel(scopeLabel)
                .dateRangeLabel(dateRangeLabel)
                .total(total)
                .passCount(value(summary.getPassCount()))
                .failedCount(value(summary.getFailedCount()))
                .noStandardCount(value(summary.getNoStandardCount()))
                .multipleCandidatesCount(value(summary.getMultipleCandidatesCount()))
                .latestSampleDate(summary.getLatestSampleDate())
                .summaryText(summaryText(scopeLabel, dateRangeLabel, query.getJudgeStatus(), summary))
                .page(query.getPage())
                .size(query.getSize())
                .records(records)
                .notes(notes(query))
                .build();
    }

    private void normalizeAndValidate(AssayRecordsQueryDTO query) {
        if (query == null || query.getProductScope() == null) {
            throw new BusinessException(400, "化验记录查询产品范围不能为空");
        }
        if (query.getPage() == null) {
            query.setPage(DEFAULT_PAGE);
        }
        if (query.getSize() == null) {
            query.setSize(DEFAULT_SIZE);
        }
        if (query.getPage() < 1) {
            throw new BusinessException(400, "page 必须大于等于 1");
        }
        if (query.getSize() < 1 || query.getSize() > MAX_SIZE) {
            throw new BusinessException(400, "size 必须在 1 到 100 之间");
        }
        if (query.getJudgeStatus() == null) {
            query.setJudgeStatus("ANY");
        }
        if (query.getSortBy() == null) {
            query.setSortBy("sampleDate");
        }
        if (query.getSortDirection() == null) {
            query.setSortDirection("DESC");
        }
        if (!Set.of("sampleDate", "createdAt").contains(query.getSortBy())) {
            throw new BusinessException(400, "sortBy 仅支持 sampleDate 或 createdAt");
        }
        if (!Set.of("ASC", "DESC").contains(query.getSortDirection())) {
            throw new BusinessException(400, "sortDirection 仅支持 ASC 或 DESC");
        }
        validateProductScope(query.getProductScope());
        normalizeDateRange(query);
        query.setResolvedJudgeResult(resolveJudgeResult(query.getJudgeStatus()));
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

    private void normalizeDateRange(AssayRecordsQueryDTO query) {
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

    private AssayRecordRowVO toRecord(AssayRecordRow row) {
        String productLabel = productLabel(row.getProductName(), row.getPackagingMethod(),
                row.getWeightPerPiece(), row.getPiecesPerPallet());
        String judge = row.getJudgeResult();
        String failedMetricText = failedMetricText(row);
        String standardLabel = standardLabel(row.getAppliedStandardName(), row.getAppliedStandardVersion());
        return AssayRecordRowVO.builder()
                .recordRef(recordRef(row))
                .recordLabel(row.getSampleDate() + " " + productLabel + "化验")
                .productLabel(productLabel)
                .sampleDate(row.getSampleDate())
                .createdAt(row.getCreatedAt())
                .judgeStatus(judge)
                .judgeLabel(judgeLabel(judge, row.getIsQualified()))
                .failedMetricText(failedMetricText)
                .failedMetricCount(row.getFailedMetricCount() == null ? 0 : row.getFailedMetricCount())
                .standardLabel(standardLabel)
                .testerLabel(row.getTesterName())
                .actionHint("可查看详情")
                .build();
    }

    private String resolveJudgeResult(String judgeStatus) {
        return switch (judgeStatus == null ? "ANY" : judgeStatus) {
            case "PASS" -> "PASS";
            case "FAILED" -> "FAIL";
            case "NO_STANDARD" -> "NO_STANDARD";
            case "MULTIPLE_CANDIDATES" -> "MULTIPLE_CANDIDATES";
            case "ANY" -> null;
            default -> throw new BusinessException(400, "不支持的化验判定状态");
        };
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

    private String dateRangeLabel(AssayRecordsQueryDTO query) {
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

    private String summaryText(String scopeLabel, String dateRangeLabel, String judgeStatus,
                               AssayRecordsSummaryRow summary) {
        long total = value(summary.getTotal());
        if (total == 0) {
            return "未查询到" + scopeLabel + "在" + dateRangeLabel + "的化验记录。";
        }
        if ("FAILED".equals(judgeStatus)) {
            return dateRangeLabel + scopeLabel + "共有 " + total + " 条不合格化验记录。";
        }
        return dateRangeLabel + scopeLabel + "共有 " + total + " 条化验记录，其中 "
                + value(summary.getPassCount()) + " 条合格，"
                + value(summary.getFailedCount()) + " 条不合格，"
                + value(summary.getNoStandardCount()) + " 条无标准，"
                + value(summary.getMultipleCandidatesCount()) + " 条标准多候选。";
    }

    private List<String> notes(AssayRecordsQueryDTO query) {
        List<String> notes = new ArrayList<>();
        notes.add("最近一次默认按采样日期排序，采样日期相同时按创建时间排序。");
        if (query.getResolvedJudgeResult() != null) {
            notes.add("已按化验判定状态过滤。");
        }
        if (query.getDateRange() != null) {
            notes.add("日期范围按化验采样日期计算。");
        }
        notes.add("无标准表示无法自动判定，不等同于不合格。");
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

    private String judgeLabel(String judgeResult, String fallback) {
        return switch (judgeResult == null ? "" : judgeResult) {
            case "PASS" -> "合格";
            case "FAIL" -> "不合格";
            case "NO_STANDARD" -> "无标准";
            case "MULTIPLE_CANDIDATES" -> "标准多候选";
            default -> fallback == null || fallback.isBlank() ? "未知" : fallback;
        };
    }

    private String failedMetricText(AssayRecordRow row) {
        if (row.getFailedMetricCount() == null || row.getFailedMetricCount() <= 0) {
            return "";
        }
        try {
            List<Map<String, Object>> metrics = objectMapper.readValue(
                    row.getFailedMetricsJson(), new TypeReference<>() {});
            List<String> names = metrics.stream()
                    .map(metric -> metric.get("metricName"))
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(name -> !name.isBlank())
                    .limit(5)
                    .toList();
            if (!names.isEmpty()) {
                return String.join("、", names);
            }
        } catch (Exception ignored) {
            // Fall back to count below.
        }
        return row.getFailedMetricCount() + "项指标异常";
    }

    private String standardLabel(String name, Integer version) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return version == null ? name : name + " v" + version;
    }

    private String recordRef(AssayRecordRow row) {
        return reportRefCodec.encode(row.getId(), row.getProductId(), row.getSampleDate());
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

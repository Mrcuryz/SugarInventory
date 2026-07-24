package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.InventoryDistributionQueryDTO;
import com.Laibin.SugarInventory.domain.dto.InventoryQualityQueryDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.QualityStandard;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.domain.vo.InventoryQualityRecordVO;
import com.Laibin.SugarInventory.domain.vo.InventoryQualityVO;
import com.Laibin.SugarInventory.mapper.InventoryQualityMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.QualityStandardMapper;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.mapper.model.InventoryQualityAggregateRow;
import com.Laibin.SugarInventory.mapper.model.InventoryQualityRow;
import com.Laibin.SugarInventory.service.InventoryQualityService;
import com.Laibin.SugarInventory.service.support.AssayReportRefCodec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InventoryQualityServiceImpl implements InventoryQualityService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final Set<String> MODES = Set.of("JUDGE_STATUS", "STANDARD", "METRIC");
    private static final Set<String> JUDGE_STATUSES = Set.of("PASS", "FAIL", "NO_STANDARD", "MULTIPLE_CANDIDATES", "MISSING_ASSAY");
    private static final Map<String, MetricDefinition> METRICS = Map.of(
            "color_value", new MetricDefinition("色值", "IU"),
            "reducing_sugar", new MetricDefinition("还原糖分", "g/100g"),
            "dry_weight_loss", new MetricDefinition("干燥失重", "g/100g"),
            "conductivity_ash", new MetricDefinition("电导灰分", "g/100g"),
            "sucrose", new MetricDefinition("蔗糖分", "g/100g"),
            "insoluble_impurity", new MetricDefinition("不溶于水杂质", "mg/kg"),
            "ph", new MetricDefinition("pH", "")
    );

    private final InventoryQualityMapper inventoryQualityMapper;
    private final ProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;
    private final QualityStandardMapper qualityStandardMapper;
    private final AssayReportRefCodec reportRefCodec;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public InventoryQualityVO query(InventoryQualityQueryDTO query) {
        normalizeAndValidate(query);
        Product product = resolveProduct(query.getProductScope());
        validateWarehouse(query.getWarehouseScope());
        QualityStandard standard = resolveStandard(query);

        InventoryQualityAggregateRow aggregate = inventoryQualityMapper.selectAggregate(query);
        if (aggregate == null) {
            aggregate = new InventoryQualityAggregateRow();
        }
        List<InventoryQualityRow> rows = inventoryQualityMapper.selectRecords(query);
        List<InventoryQualityRecordVO> records = (rows == null ? List.<InventoryQualityRow>of() : rows).stream()
                .map(row -> toRecord(row, query))
                .toList();
        long totalGroups = value(aggregate.getTotalGroups());
        return InventoryQualityVO.builder()
                .queryType(query.getMode())
                .queryLabel(queryLabel(query, product, standard))
                .totalGroups(totalGroups)
                .totalEquivalentPieces(value(aggregate.getTotalEquivalentPieces()))
                .totalWeightText(weightText(aggregate.getTotalWeight()))
                .truncated(totalGroups > query.getLimit())
                .records(records)
                .notes(notes(query))
                .build();
    }

    private void normalizeAndValidate(InventoryQualityQueryDTO query) {
        if (query == null || query.getProductScope() == null || query.getWarehouseScope() == null) {
            throw new BusinessException(400, "库存质量查询范围不能为空");
        }
        if (query.getLimit() == null) query.setLimit(DEFAULT_LIMIT);
        if (query.getLimit() < 1 || query.getLimit() > MAX_LIMIT) {
            throw new BusinessException(400, "limit 必须在 1 到 100 之间");
        }
        if (!MODES.contains(query.getMode())) {
            throw new BusinessException(400, "不支持的库存质量查询类型");
        }
        validateProductScope(query.getProductScope());
        validateWarehouseScope(query.getWarehouseScope());
        switch (query.getMode()) {
            case "JUDGE_STATUS" -> {
                if (!JUDGE_STATUSES.contains(query.getJudgeStatus())) {
                    throw new BusinessException(400, "库存质量判定状态无效");
                }
            }
            case "STANDARD" -> requireText(query.getStandardCode(), 64, "标准编码不能为空");
            case "METRIC" -> validateMetric(query.getMetricCondition());
            default -> throw new BusinessException(400, "不支持的库存质量查询类型");
        }
    }

    private void validateProductScope(InventoryDistributionQueryDTO.ProductScope scope) {
        if (scope.getType() == null) throw new BusinessException(400, "产品范围类型不能为空");
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

    private void validateWarehouseScope(InventoryDistributionQueryDTO.WarehouseScope scope) {
        if ("ALL".equals(scope.getType())) return;
        if (!"SINGLE_WAREHOUSE".equals(scope.getType()) || scope.getWarehouseId() == null || scope.getWarehouseId() <= 0) {
            throw new BusinessException(400, "库位范围仅支持 ALL 或有效的 SINGLE_WAREHOUSE");
        }
    }

    private void validateMetric(InventoryQualityQueryDTO.MetricCondition condition) {
        if (condition == null || !METRICS.containsKey(condition.getMetricCode())) {
            throw new BusinessException(400, "化验指标不能为空或不受支持");
        }
        String operator = condition.getOperator();
        if (!Set.of("GT", "GTE", "LT", "LTE", "EQ", "BETWEEN").contains(operator)) {
            throw new BusinessException(400, "指标比较方式无效");
        }
        if ("BETWEEN".equals(operator)) {
            if (condition.getMinValue() == null || condition.getMaxValue() == null
                    || condition.getMinValue().compareTo(condition.getMaxValue()) > 0) {
                throw new BusinessException(400, "BETWEEN 必须提供有效的最小值和最大值");
            }
        } else if (condition.getValue() == null) {
            throw new BusinessException(400, "指标比较值不能为空");
        }
    }

    private Product resolveProduct(InventoryDistributionQueryDTO.ProductScope scope) {
        if (!"SINGLE_PRODUCT".equals(scope.getType())) return null;
        Product product = productMapper.selectById(scope.getProductId());
        if (product == null) throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        return product;
    }

    private void validateWarehouse(InventoryDistributionQueryDTO.WarehouseScope scope) {
        if (!"SINGLE_WAREHOUSE".equals(scope.getType())) return;
        Warehouse warehouse = warehouseMapper.selectById(scope.getWarehouseId());
        if (warehouse == null) throw new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND);
    }

    private QualityStandard resolveStandard(InventoryQualityQueryDTO query) {
        if (!"STANDARD".equals(query.getMode())) return null;
        QualityStandard standard = qualityStandardMapper.selectByCodeAndVersion(query.getStandardCode().trim(), query.getStandardVersion());
        if (standard == null) throw new BusinessException(404, "未找到指定化验标准");
        query.setResolvedStandardId(standard.getId());
        query.setResolvedStandardName(standard.getStandardName());
        query.setResolvedStandardVersion(standard.getVersion());
        query.setResolvedStandardProductType(standard.getProductType());
        return standard;
    }

    private InventoryQualityRecordVO toRecord(InventoryQualityRow row, InventoryQualityQueryDTO query) {
        Integer piecesPerPallet = row.getPiecesPerPallet();
        long pieces = value(row.getTotalEquivalentPieces());
        MetricDefinition metric = "METRIC".equals(query.getMode()) ? METRICS.get(query.getMetricCondition().getMetricCode()) : null;
        return InventoryQualityRecordVO.builder()
                .productLabel(productLabel(row))
                .productionDate(row.getProductionDate())
                .warehouseLabel(warehouseLabel(row.getWarehouseName()))
                .stockText(piecesPerPallet == null || piecesPerPallet <= 0
                        ? pieces + "件" : pieces / piecesPerPallet + "板" + pieces % piecesPerPallet + "件")
                .totalWeightText(weightText(row.getTotalWeight()))
                .palletCount(value(row.getPalletCount()))
                .judgeStatus(row.getJudgeResult())
                .judgeLabel(judgeLabel(row.getJudgeResult()))
                .standardLabel(standardLabel(row.getAppliedStandardName(), row.getAppliedStandardVersion()))
                .failedMetricText(failedMetricText(row))
                .metricLabel(metric == null ? null : metric.name())
                .metricValueText(metric == null || row.getMetricValue() == null ? null
                        : decimal(row.getMetricValue()) + metric.unit())
                .reportRef(row.getAssayId() == null ? null
                        : reportRefCodec.encode(row.getAssayId(), row.getProductId(), row.getProductionDate()))
                .build();
    }

    private String queryLabel(InventoryQualityQueryDTO query, Product product, QualityStandard standard) {
        String scope = product == null ? scopeLabel(query.getProductScope()) : productLabel(product);
        return switch (query.getMode()) {
            case "JUDGE_STATUS" -> scope + "当前" + judgeLabel(query.getJudgeStatus()) + "库存";
            case "STANDARD" -> scope + "当前符合“" + standard.getStandardName() + " v" + standard.getVersion() + "”的库存";
            case "METRIC" -> scope + "当前" + metricConditionLabel(query.getMetricCondition()) + "的库存";
            default -> scope + "当前库存质量查询";
        };
    }

    private List<String> notes(InventoryQualityQueryDTO query) {
        if ("JUDGE_STATUS".equals(query.getMode()) && "FAIL".equals(query.getJudgeStatus())) {
            return List.of("仅包含当前仍在库、且对应批次最新版本化验明确判定为不合格的库存。",
                    "无化验和无标准库存单独统计，不计入不合格。");
        }
        if ("STANDARD".equals(query.getMode())) {
            return List.of("按当前库存批次最新版本化验的原始指标逐项匹配指定标准。",
                    "缺少该标准任一已配置指标值时，不计为符合标准。");
        }
        return List.of("按当前库存批次最新版本化验的受控原始指标筛选。",
                "没有化验或该指标没有数值的库存不计入结果。");
    }

    private String failedMetricText(InventoryQualityRow row) {
        if (row.getFailedMetricCount() == null || row.getFailedMetricCount() <= 0 || row.getFailedMetricsJson() == null) return null;
        try {
            List<Map<String, Object>> metrics = objectMapper.readValue(row.getFailedMetricsJson(), new TypeReference<>() { });
            List<String> names = metrics.stream().map(item -> item.get("metricName"))
                    .filter(String.class::isInstance).map(String.class::cast).filter(name -> !name.isBlank()).limit(7).toList();
            return names.isEmpty() ? row.getFailedMetricCount() + "项指标不达标" : String.join("、", names);
        } catch (Exception ignored) {
            return row.getFailedMetricCount() + "项指标不达标";
        }
    }

    private String metricConditionLabel(InventoryQualityQueryDTO.MetricCondition condition) {
        MetricDefinition metric = METRICS.get(condition.getMetricCode());
        String symbol = switch (condition.getOperator()) {
            case "GT" -> ">"; case "GTE" -> "≥"; case "LT" -> "<"; case "LTE" -> "≤"; case "EQ" -> "=";
            case "BETWEEN" -> decimal(condition.getMinValue()) + "～" + decimal(condition.getMaxValue());
            default -> "";
        };
        String value = "BETWEEN".equals(condition.getOperator()) ? "" : decimal(condition.getValue());
        return metric.name() + symbol + value + metric.unit();
    }

    private String scopeLabel(InventoryDistributionQueryDTO.ProductScope scope) {
        return switch (scope.getType()) {
            case "EXACT_PRODUCT_NAME_GROUP" -> "产品名称为“" + scope.getProductName().trim() + "”的全部规格";
            case "PRODUCT_TYPE_GROUP" -> "全部" + scope.getProductType().trim() + "大类";
            default -> "全部产品";
        };
    }

    private String productLabel(Product product) {
        return productLabel(product.getProductName(), product.getPackagingMethod(), product.getWeightPerPiece(), product.getPiecesPerPallet());
    }

    private String productLabel(InventoryQualityRow row) {
        return productLabel(row.getProductName(), row.getPackagingMethod(), row.getWeightPerPiece(), row.getPiecesPerPallet());
    }

    private String productLabel(String name, String packaging, BigDecimal weight, Integer piecesPerPallet) {
        StringBuilder label = new StringBuilder(name == null ? "未命名产品" : name);
        if (packaging != null && !packaging.isBlank() && !label.toString().contains("（" + packaging + "）")) label.append("（").append(packaging).append("）");
        if (weight != null) label.append(' ').append(decimal(weight)).append("kg/件");
        if (piecesPerPallet != null) label.append(' ').append(piecesPerPallet).append("件/板");
        return label.toString();
    }

    private String warehouseLabel(String name) {
        if (name == null || name.isBlank()) return "未命名库位";
        return name.matches(".*(库位|仓库)$") ? name : name + "号库位";
    }

    private String judgeLabel(String status) {
        return switch (status == null ? "" : status) {
            case "PASS" -> "合格"; case "FAIL" -> "不合格"; case "NO_STANDARD" -> "无标准";
            case "MULTIPLE_CANDIDATES" -> "符合多个标准"; case "MISSING_ASSAY" -> "无化验"; default -> "未判定";
        };
    }

    private String standardLabel(String name, Integer version) {
        if (name == null || name.isBlank()) return null;
        return version == null ? name : name + " v" + version;
    }

    private String weightText(BigDecimal value) { return value == null ? "0kg" : decimal(value) + "kg"; }
    private String decimal(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private long value(Long value) { return value == null ? 0L : value; }

    private void requireText(String value, int maxLength, String message) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) throw new BusinessException(400, message);
    }

    private record MetricDefinition(String name, String unit) { }
}

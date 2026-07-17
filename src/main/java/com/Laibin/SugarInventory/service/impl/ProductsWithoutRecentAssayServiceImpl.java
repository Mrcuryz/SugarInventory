package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayRecordsQueryDTO;
import com.Laibin.SugarInventory.domain.dto.InventoryDistributionQueryDTO;
import com.Laibin.SugarInventory.domain.dto.ProductsWithoutRecentAssayQueryDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.domain.vo.ProductWithoutRecentAssayGroupVO;
import com.Laibin.SugarInventory.domain.vo.ProductsWithoutRecentAssayVO;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.ProductsWithoutRecentAssayMapper;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.mapper.model.ProductsWithoutRecentAssayAggregateRow;
import com.Laibin.SugarInventory.mapper.model.ProductsWithoutRecentAssayGroupRow;
import com.Laibin.SugarInventory.service.ProductsWithoutRecentAssayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductsWithoutRecentAssayServiceImpl implements ProductsWithoutRecentAssayService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final String CALCULATION_NOTE = "按各产品每板件数将原始整板数和散件数折算为等价件数。";

    private final ProductsWithoutRecentAssayMapper mapper;
    private final ProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;

    @Override
    @Transactional(readOnly = true)
    public ProductsWithoutRecentAssayVO queryProductsWithoutRecentAssay(ProductsWithoutRecentAssayQueryDTO query) {
        normalizeAndValidate(query);
        Product singleProduct = resolveSingleProduct(query.getProductScope());
        Warehouse singleWarehouse = resolveSingleWarehouse(query.getWarehouseScope());

        ProductsWithoutRecentAssayAggregateRow aggregate = mapper.selectAggregate(query);
        if (aggregate == null) {
            aggregate = new ProductsWithoutRecentAssayAggregateRow();
        }
        long totalEquivalentPieces = value(aggregate.getTotalEquivalentPieces());
        boolean mixedScale = mixedScale(aggregate.getProductCount(), aggregate.getMinPiecesPerPallet(),
                aggregate.getMaxPiecesPerPallet());
        Integer commonPiecesPerPallet = mixedScale ? null : positive(aggregate.getMinPiecesPerPallet());
        if (commonPiecesPerPallet == null && singleProduct != null) {
            commonPiecesPerPallet = positive(singleProduct.getPiecesPerPallet());
        }
        List<ProductsWithoutRecentAssayGroupRow> groupRows = mapper.selectGroups(query);
        List<ProductWithoutRecentAssayGroupVO> groups = (groupRows == null
                ? List.<ProductsWithoutRecentAssayGroupRow>of()
                : groupRows).stream()
                .map(row -> toGroup(row))
                .toList();
        String scopeLabel = scopeLabel(query.getProductScope(), singleProduct);
        String warehouseScopeLabel = warehouseScopeLabel(query.getWarehouseScope(), singleWarehouse);
        String dateRangeLabel = dateRangeLabel(query);
        return ProductsWithoutRecentAssayVO.builder()
                .scopeLabel(scopeLabel)
                .warehouseScopeLabel(warehouseScopeLabel)
                .dateRangeLabel(dateRangeLabel)
                .population(query.getPopulation())
                .groupBy(query.getGroupBy())
                .totalGroups(groups.size())
                .rawFullPallets(value(aggregate.getRawFullPallets()))
                .rawLoosePieces(value(aggregate.getRawLoosePieces()))
                .normalizedPallets(normalizedPallets(totalEquivalentPieces, commonPiecesPerPallet))
                .normalizedLoosePieces(normalizedLoosePieces(totalEquivalentPieces, commonPiecesPerPallet))
                .totalEquivalentPieces(totalEquivalentPieces)
                .totalStockText(stockText(totalEquivalentPieces, commonPiecesPerPallet, mixedScale))
                .totalWeightText(weightText(aggregate.getTotalWeight()))
                .inventoryRecordCount(value(aggregate.getInventoryRecordCount()))
                .palletCount(value(aggregate.getPalletCount()))
                .warehouseCount(value(aggregate.getWarehouseCount()))
                .productCount(value(aggregate.getProductCount()))
                .summaryText(summaryText(scopeLabel, warehouseScopeLabel, dateRangeLabel, groups.size()))
                .groups(groups)
                .riskLabels(groups.isEmpty() ? List.of() : List.of("存在无有效化验库存"))
                .notes(notes(query, mixedScale))
                .build();
    }

    private void normalizeAndValidate(ProductsWithoutRecentAssayQueryDTO query) {
        if (query == null || query.getProductScope() == null || query.getWarehouseScope() == null) {
            throw new BusinessException(400, "缺化验查询范围不能为空");
        }
        validateProductScope(query.getProductScope());
        validateWarehouseScope(query.getWarehouseScope());
        if (query.getPopulation() == null) {
            query.setPopulation("CURRENT_INVENTORY");
        }
        if (!"CURRENT_INVENTORY".equals(query.getPopulation())) {
            throw new BusinessException(400, "population 第一版仅支持 CURRENT_INVENTORY");
        }
        if (query.getGroupBy() == null) {
            query.setGroupBy("product");
        }
        if (!Set.of("product", "warehouse", "product_warehouse").contains(query.getGroupBy())) {
            throw new BusinessException(400, "groupBy 仅支持 product、warehouse 或 product_warehouse");
        }
        if (query.getLimit() == null) {
            query.setLimit(DEFAULT_LIMIT);
        }
        if (query.getLimit() < 1 || query.getLimit() > MAX_LIMIT) {
            throw new BusinessException(400, "limit 必须在 1 到 100 之间");
        }
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

    private void validateWarehouseScope(InventoryDistributionQueryDTO.WarehouseScope scope) {
        if (scope.getType() == null) {
            throw new BusinessException(400, "库位范围类型不能为空");
        }
        if ("ALL".equals(scope.getType())) {
            return;
        }
        if (!"SINGLE_WAREHOUSE".equals(scope.getType()) || scope.getWarehouseId() == null
                || scope.getWarehouseId() <= 0) {
            throw new BusinessException(400, "库位范围仅支持 ALL 或有效的 SINGLE_WAREHOUSE");
        }
    }

    private void normalizeDateRange(ProductsWithoutRecentAssayQueryDTO query) {
        AssayRecordsQueryDTO.DateRange range = query.getDateRange();
        if (range == null) {
            LocalDate today = LocalDate.now();
            query.setResolvedFrom(today);
            query.setResolvedTo(today);
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

    private Warehouse resolveSingleWarehouse(InventoryDistributionQueryDTO.WarehouseScope scope) {
        if (!"SINGLE_WAREHOUSE".equals(scope.getType())) {
            return null;
        }
        Warehouse warehouse = warehouseMapper.selectById(scope.getWarehouseId());
        if (warehouse == null) {
            throw new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }
        return warehouse;
    }

    private ProductWithoutRecentAssayGroupVO toGroup(ProductsWithoutRecentAssayGroupRow row) {
        long equivalentPieces = value(row.getTotalEquivalentPieces());
        boolean mixedScale = mixedScale(row.getProductCount(), row.getMinPiecesPerPallet(), row.getMaxPiecesPerPallet());
        Integer piecesPerPallet = mixedScale ? null : positive(
                row.getPiecesPerPallet() == null ? row.getMinPiecesPerPallet() : row.getPiecesPerPallet());
        String warehouseLabel = row.getWarehouseName() == null ? null : warehouseLabel(row.getWarehouseName());
        String productLabel = row.getProductName() == null ? null : productLabel(row.getProductName(),
                row.getPackagingMethod(), row.getWeightPerPiece(), row.getPiecesPerPallet());
        return ProductWithoutRecentAssayGroupVO.builder()
                .groupLabel(groupLabel(warehouseLabel, productLabel))
                .productLabel(productLabel)
                .warehouseLabel(warehouseLabel)
                .rawFullPallets(value(row.getRawFullPallets()))
                .rawLoosePieces(value(row.getRawLoosePieces()))
                .normalizedPallets(normalizedPallets(equivalentPieces, piecesPerPallet))
                .normalizedLoosePieces(normalizedLoosePieces(equivalentPieces, piecesPerPallet))
                .totalEquivalentPieces(equivalentPieces)
                .stockText(stockText(equivalentPieces, piecesPerPallet, mixedScale))
                .totalWeightText(weightText(row.getTotalWeight()))
                .inventoryRecordCount(value(row.getInventoryRecordCount()))
                .palletCount(value(row.getPalletCount()))
                .warehouseCount(value(row.getWarehouseCount()))
                .productCount(value(row.getProductCount()))
                .latestInboundTime(row.getLatestInboundTime())
                .warehouseLabels(warehouseLabels(row.getWarehouseNames()))
                .riskLabels(List.of("无有效化验"))
                .nextActionLabel("去补充")
                .calculationNote(CALCULATION_NOTE)
                .build();
    }

    private String scopeLabel(AssayRecordsQueryDTO.ProductScope scope, Product product) {
        return switch (scope.getType()) {
            case "SINGLE_PRODUCT" -> productLabel(product.getProductName(), product.getPackagingMethod(),
                    product.getWeightPerPiece(), product.getPiecesPerPallet());
            case "EXACT_PRODUCT_NAME_GROUP" -> "产品名称为“" + scope.getProductName().trim() + "”的全部规格";
            case "PRODUCT_TYPE_GROUP" -> "全部" + scope.getProductType().trim() + "大类";
            default -> "当前在库全部产品";
        };
    }

    private String warehouseScopeLabel(InventoryDistributionQueryDTO.WarehouseScope scope, Warehouse warehouse) {
        if ("SINGLE_WAREHOUSE".equals(scope.getType())) {
            return warehouseLabel(warehouse.getWarehouseName());
        }
        return "全部库位";
    }

    private String dateRangeLabel(ProductsWithoutRecentAssayQueryDTO query) {
        AssayRecordsQueryDTO.DateRange range = query.getDateRange();
        if (range == null) {
            return "今天";
        }
        return switch (range.getType()) {
            case "EXACT" -> String.valueOf(range.getDate());
            case "LAST_DAYS" -> "最近" + range.getDays() + "天";
            case "RANGE" -> range.getFrom() + "至" + range.getTo();
            default -> "指定日期范围";
        };
    }

    private String summaryText(String scopeLabel, String warehouseScopeLabel, String dateRangeLabel, int groupCount) {
        if (groupCount == 0) {
            return "未查询到" + warehouseScopeLabel + "中" + scopeLabel + "在" + dateRangeLabel + "缺少有效化验的当前在库分组。";
        }
        return dateRangeLabel + warehouseScopeLabel + "中" + scopeLabel + "共有 " + groupCount + " 个当前在库分组缺少有效化验。";
    }

    private List<String> notes(ProductsWithoutRecentAssayQueryDTO query, boolean mixedScale) {
        List<String> notes = new ArrayList<>();
        notes.add("默认全集为当前在库库存。");
        notes.add("有效化验指存在关联产品且采样日期落入查询日期范围的化验记录，不要求化验结论合格。");
        notes.add("无标准表示已有化验但无法自动判定，不等同于无化验。");
        if (query.getDateRange() == null) {
            notes.add("未传日期范围时按今天计算。");
        }
        if (mixedScale) {
            notes.add("当前范围包含不同每板件数的规格，汇总库存以总等价件数和总重量为准。");
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
            label.append(' ').append(decimal(weight)).append("kg/件");
        }
        if (piecesPerPallet != null) {
            label.append(' ').append(piecesPerPallet).append("件/板");
        }
        return label.toString();
    }

    private String warehouseLabel(String name) {
        if (name == null || name.isBlank()) {
            return "未命名库位";
        }
        return name.matches(".*(库位|仓库)$") ? name : name + "号库位";
    }

    private String groupLabel(String warehouseLabel, String productLabel) {
        if (warehouseLabel != null && productLabel != null) {
            return productLabel + " / " + warehouseLabel;
        }
        return productLabel != null ? productLabel : warehouseLabel;
    }

    private List<String> warehouseLabels(String warehouseNames) {
        if (warehouseNames == null || warehouseNames.isBlank()) {
            return List.of();
        }
        return Arrays.stream(warehouseNames.split("、"))
                .filter(name -> !name.isBlank())
                .map(this::warehouseLabel)
                .distinct()
                .toList();
    }

    private String stockText(long pieces, Integer piecesPerPallet, boolean mixedScale) {
        if (mixedScale || piecesPerPallet == null) {
            return pieces + "件（跨规格）";
        }
        return pieces / piecesPerPallet + "板" + pieces % piecesPerPallet + "件";
    }

    private Long normalizedPallets(long pieces, Integer piecesPerPallet) {
        return piecesPerPallet == null ? null : pieces / piecesPerPallet;
    }

    private Long normalizedLoosePieces(long pieces, Integer piecesPerPallet) {
        return piecesPerPallet == null ? null : pieces % piecesPerPallet;
    }

    private String weightText(BigDecimal weight) {
        return weight == null ? null : decimal(weight) + "kg";
    }

    private boolean mixedScale(Long productCount, Integer min, Integer max) {
        return value(productCount) > 1 && (min == null || max == null || !min.equals(max));
    }

    private Integer positive(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private String decimal(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private void requireText(String value, int maxLength, String message) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new BusinessException(400, message);
        }
    }
}

package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.InventoryDistributionQueryDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.domain.vo.InventoryDistributionGroupVO;
import com.Laibin.SugarInventory.domain.vo.InventoryDistributionVO;
import com.Laibin.SugarInventory.mapper.InventoryDistributionMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.mapper.model.InventoryDistributionAggregateRow;
import com.Laibin.SugarInventory.mapper.model.InventoryDistributionGroupRow;
import com.Laibin.SugarInventory.service.InventoryDistributionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InventoryDistributionServiceImpl implements InventoryDistributionService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final String CALCULATION_NOTE =
            "一条库存记录对应一个二维码板位；不足一板时以该板实际件数计算，不再叠加整板。换算参数取产品管理当前配置。";
    private static final Set<String> PRODUCT_STATUSES = Set.of("半成品", "成品");
    private static final Set<String> WAREHOUSE_STATUSES = Set.of("正常", "空置", "满仓", "维护", "临期预警");
    private static final Set<String> PALLET_STATUSES = Set.of("FREE", "PENDING", "INSTOCK", "INVALID", "ORDER_RESERVED");

    private final InventoryDistributionMapper distributionMapper;
    private final ProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;

    @Override
    @Transactional(readOnly = true)
    public InventoryDistributionVO getDistribution(InventoryDistributionQueryDTO query) {
        normalizeAndValidate(query);
        Product singleProduct = resolveSingleProduct(query.getProductScope());
        validateWarehouse(query.getWarehouseScope());

        InventoryDistributionAggregateRow aggregate = distributionMapper.selectAggregate(query);
        if (aggregate == null) {
            aggregate = new InventoryDistributionAggregateRow();
        }
        long totalEquivalentPieces = value(aggregate.getTotalEquivalentPieces());
        boolean mixedScale = mixedScale(aggregate.getProductCount(), aggregate.getMinPiecesPerPallet(),
                aggregate.getMaxPiecesPerPallet());
        Integer commonPiecesPerPallet = mixedScale ? null : positive(aggregate.getMinPiecesPerPallet());
        if (commonPiecesPerPallet == null && singleProduct != null) {
            commonPiecesPerPallet = positive(singleProduct.getPiecesPerPallet());
        }
        List<InventoryDistributionGroupRow> groupRows = distributionMapper.selectGroups(query);
        List<InventoryDistributionGroupVO> groups = (groupRows == null ? List.<InventoryDistributionGroupRow>of() : groupRows).stream()
                .map(row -> toGroup(row, totalEquivalentPieces))
                .toList();
        String scopeLabel = scopeLabel(query.getProductScope(), singleProduct);

        return InventoryDistributionVO.builder()
                .scopeLabel(scopeLabel)
                .productLabel(scopeLabel)
                .groupBy(query.getGroupBy())
                .rawFullPallets(value(aggregate.getRawFullPallets()))
                .rawLoosePieces(value(aggregate.getRawLoosePieces()))
                .normalizedPallets(normalizedPallets(totalEquivalentPieces, commonPiecesPerPallet))
                .normalizedLoosePieces(normalizedLoosePieces(totalEquivalentPieces, commonPiecesPerPallet))
                .totalEquivalentPieces(totalEquivalentPieces)
                .totalStockText(stockText(totalEquivalentPieces, commonPiecesPerPallet, mixedScale))
                .totalWeightText(weightText(aggregate.getTotalWeight()))
                .warehouseCount(value(aggregate.getWarehouseCount()))
                .productCount(value(aggregate.getProductCount()))
                .palletCount(value(aggregate.getPalletCount()))
                .calculationNote(CALCULATION_NOTE)
                .groups(groups)
                .notes(notes(query, mixedScale))
                .build();
    }

    private InventoryDistributionGroupVO toGroup(InventoryDistributionGroupRow row, long grandTotalPieces) {
        long equivalentPieces = value(row.getTotalEquivalentPieces());
        boolean mixedScale = mixedScale(row.getProductCount(), row.getMinPiecesPerPallet(), row.getMaxPiecesPerPallet());
        Integer piecesPerPallet = mixedScale ? null : positive(
                row.getPiecesPerPallet() == null ? row.getMinPiecesPerPallet() : row.getPiecesPerPallet());
        String warehouseLabel = row.getWarehouseName() == null ? null : warehouseLabel(row.getWarehouseName());
        String productLabel = row.getProductName() == null ? null : productLabel(row);
        return InventoryDistributionGroupVO.builder()
                .groupLabel(groupLabel(warehouseLabel, productLabel))
                .warehouseLabel(warehouseLabel)
                .canonicalProductName(row.getProductName())
                .productLabel(productLabel)
                .rawFullPallets(value(row.getRawFullPallets()))
                .rawLoosePieces(value(row.getRawLoosePieces()))
                .normalizedPallets(normalizedPallets(equivalentPieces, piecesPerPallet))
                .normalizedLoosePieces(normalizedLoosePieces(equivalentPieces, piecesPerPallet))
                .totalEquivalentPieces(equivalentPieces)
                .stockText(stockText(equivalentPieces, piecesPerPallet, mixedScale))
                .totalWeightText(weightText(row.getTotalWeight()))
                .palletCount(value(row.getPalletCount()))
                .warehouseCount(value(row.getWarehouseCount()))
                .productCount(value(row.getProductCount()))
                .percentageText(percentageText(equivalentPieces, grandTotalPieces))
                .latestInboundTime(row.getLatestInboundTime())
                .riskLabels(riskLabels(row))
                .calculationNote(CALCULATION_NOTE)
                .build();
    }

    private void normalizeAndValidate(InventoryDistributionQueryDTO query) {
        if (query == null || query.getProductScope() == null || query.getWarehouseScope() == null) {
            throw new BusinessException(400, "库存分布查询范围不能为空");
        }
        if (query.getLimit() == null) {
            query.setLimit(DEFAULT_LIMIT);
        }
        if (query.getLimit() < 1 || query.getLimit() > MAX_LIMIT) {
            throw new BusinessException(400, "limit 必须在 1 到 100 之间");
        }
        if (!Set.of("warehouse", "product", "warehouse_product").contains(query.getGroupBy())) {
            throw new BusinessException(400, "groupBy 仅支持 warehouse、product 或 warehouse_product");
        }
        validateProductScope(query.getProductScope());
        validateWarehouseScope(query.getWarehouseScope());
        validateFilters(query.getStatusFilter());
    }

    private void validateProductScope(InventoryDistributionQueryDTO.ProductScope scope) {
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

    private void validateFilters(InventoryDistributionQueryDTO.StatusFilter filter) {
        if (filter == null) {
            return;
        }
        validateValues(filter.getProductStatuses(), PRODUCT_STATUSES, "产品状态");
        validateValues(filter.getWarehouseStatuses(), WAREHOUSE_STATUSES, "库位状态");
        validateValues(filter.getPalletStatuses(), PALLET_STATUSES, "托盘状态");
        if (filter.getEntryDateFrom() != null && filter.getEntryDateTo() != null
                && filter.getEntryDateFrom().isAfter(filter.getEntryDateTo())) {
            throw new BusinessException(400, "入库开始日期不能晚于结束日期");
        }
    }

    private Product resolveSingleProduct(InventoryDistributionQueryDTO.ProductScope scope) {
        if (!"SINGLE_PRODUCT".equals(scope.getType())) {
            return null;
        }
        Product product = productMapper.selectById(scope.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if (positive(product.getPiecesPerPallet()) == null) {
            throw new BusinessException(400, "产品未配置有效的每板件数，无法计算库存分布");
        }
        return product;
    }

    private void validateWarehouse(InventoryDistributionQueryDTO.WarehouseScope scope) {
        if ("SINGLE_WAREHOUSE".equals(scope.getType())) {
            Warehouse warehouse = warehouseMapper.selectById(scope.getWarehouseId());
            if (warehouse == null) {
                throw new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND);
            }
        }
    }

    private String scopeLabel(InventoryDistributionQueryDTO.ProductScope scope, Product product) {
        return switch (scope.getType()) {
            case "SINGLE_PRODUCT" -> productLabel(product);
            case "EXACT_PRODUCT_NAME_GROUP" -> "产品名称为“" + scope.getProductName().trim() + "”的全部规格";
            case "PRODUCT_TYPE_GROUP" -> "全部" + scope.getProductType().trim() + "大类";
            default -> "全部产品";
        };
    }

    private String productLabel(Product product) {
        return productLabel(product.getProductName(), product.getPackagingMethod(), product.getWeightPerPiece(),
                product.getPiecesPerPallet());
    }

    private String productLabel(InventoryDistributionGroupRow row) {
        return productLabel(row.getProductName(), row.getPackagingMethod(), row.getWeightPerPiece(),
                row.getPiecesPerPallet());
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
            return warehouseLabel + " / " + productLabel;
        }
        return warehouseLabel != null ? warehouseLabel : productLabel;
    }

    private List<String> riskLabels(InventoryDistributionGroupRow row) {
        List<String> labels = new ArrayList<>();
        if (value(row.getMissingAssayCount()) > 0) labels.add("存在无化验库存");
        if (value(row.getFailedAssayCount()) > 0) labels.add("存在化验不合格库存");
        if (value(row.getNoStandardAssayCount()) > 0) labels.add("存在无标准化验");
        if (value(row.getAbnormalPalletCount()) > 0) labels.add("存在托盘状态异常");
        return labels;
    }

    private List<String> notes(InventoryDistributionQueryDTO query, boolean mixedScale) {
        List<String> notes = new ArrayList<>();
        notes.add("仅统计当前在库库存。");
        notes.add(CALCULATION_NOTE);
        if (mixedScale) {
            notes.add("当前范围包含不同每板件数的规格，汇总库存以总等价件数和总重量为准。");
        }
        if (query.getStatusFilter() != null && hasFilters(query.getStatusFilter())) {
            notes.add("结果已应用库存状态、库位、托盘、化验或日期过滤条件。");
        }
        return notes;
    }

    private boolean hasFilters(InventoryDistributionQueryDTO.StatusFilter filter) {
        return hasValues(filter.getProductStatuses()) || hasValues(filter.getWarehouseStatuses())
                || hasValues(filter.getPalletStatuses()) || filter.getAssayStatus() != null
                || filter.getEntryDateFrom() != null || filter.getEntryDateTo() != null;
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

    private String percentageText(long value, long total) {
        if (total <= 0) return "0.0%";
        return BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP).toPlainString() + "%";
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
        return value.stripTrailingZeros().toPlainString();
    }

    private void requireText(String value, int maxLength, String message) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new BusinessException(400, message);
        }
    }

    private void validateValues(List<String> values, Set<String> allowed, String label) {
        if (values != null && (values.size() > 10
                || values.stream().anyMatch(value -> value == null || !allowed.contains(value)))) {
            throw new BusinessException(400, label + "过滤条件无效");
        }
    }

    private boolean hasValues(List<String> values) {
        return values != null && !values.isEmpty();
    }
}

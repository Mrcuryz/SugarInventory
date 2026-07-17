package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayRecordsQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssayStandardCoverageQueryDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.vo.AssayStandardCoverageGroupVO;
import com.Laibin.SugarInventory.domain.vo.AssayStandardCoverageVO;
import com.Laibin.SugarInventory.mapper.AssayStandardCoverageMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.model.AssayStandardCoverageGroupRow;
import com.Laibin.SugarInventory.service.AssayStandardCoverageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AssayStandardCoverageServiceImpl implements AssayStandardCoverageService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final AssayStandardCoverageMapper mapper;
    private final ProductMapper productMapper;

    @Override
    @Transactional(readOnly = true)
    public AssayStandardCoverageVO queryCoverage(AssayStandardCoverageQueryDTO query) {
        normalizeAndValidate(query);
        Product singleProduct = resolveSingleProduct(query.getProductScope());
        List<AssayStandardCoverageGroupRow> rows = mapper.selectProductWithoutStandardGroups(query);
        List<AssayStandardCoverageGroupVO> groups = (rows == null
                ? List.<AssayStandardCoverageGroupRow>of()
                : rows).stream()
                .map(this::toGroup)
                .toList();
        long totalEquivalentPieces = groups.stream().mapToLong(AssayStandardCoverageGroupVO::getTotalEquivalentPieces).sum();
        long rawFullPallets = groups.stream().mapToLong(AssayStandardCoverageGroupVO::getRawFullPallets).sum();
        long rawLoosePieces = groups.stream().mapToLong(AssayStandardCoverageGroupVO::getRawLoosePieces).sum();
        long inventoryRecordCount = groups.stream().mapToLong(AssayStandardCoverageGroupVO::getInventoryRecordCount).sum();
        long palletCount = groups.stream().mapToLong(AssayStandardCoverageGroupVO::getPalletCount).sum();
        long warehouseCount = groups.stream().flatMap(group -> group.getWarehouseLabels().stream()).distinct().count();
        Integer commonPiecesPerPallet = commonPiecesPerPallet(rows);
        String scopeLabel = scopeLabel(query.getProductScope(), singleProduct);
        return AssayStandardCoverageVO.builder()
                .scopeLabel(scopeLabel)
                .dateRangeLabel(dateRangeLabel(query))
                .coverageType(query.getCoverageType())
                .totalGroups(groups.size())
                .rawFullPallets(rawFullPallets)
                .rawLoosePieces(rawLoosePieces)
                .normalizedPallets(normalizedPallets(totalEquivalentPieces, commonPiecesPerPallet))
                .normalizedLoosePieces(normalizedLoosePieces(totalEquivalentPieces, commonPiecesPerPallet))
                .totalEquivalentPieces(totalEquivalentPieces)
                .totalStockText(stockText(totalEquivalentPieces, commonPiecesPerPallet, commonPiecesPerPallet == null))
                .totalWeightText(totalWeightText(rows))
                .inventoryRecordCount(inventoryRecordCount)
                .palletCount(palletCount)
                .warehouseCount(warehouseCount)
                .productCount(groups.size())
                .summaryText(summaryText(scopeLabel, groups.size()))
                .groups(groups)
                .riskLabels(groups.isEmpty() ? List.of() : List.of("存在当前在库产品无有效质量标准"))
                .notes(notes(query))
                .build();
    }

    private void normalizeAndValidate(AssayStandardCoverageQueryDTO query) {
        if (query == null || query.getProductScope() == null) {
            throw new BusinessException(400, "质量标准覆盖查询产品范围不能为空");
        }
        validateProductScope(query.getProductScope());
        if (query.getCoverageType() == null) {
            query.setCoverageType("PRODUCT_WITHOUT_STANDARD");
        }
        if (!Set.of("PRODUCT_WITHOUT_STANDARD", "ASSAY_WITHOUT_STANDARD", "UNUSED_STANDARD")
                .contains(query.getCoverageType())) {
            throw new BusinessException(400, "不支持的质量标准覆盖类型");
        }
        if (!"PRODUCT_WITHOUT_STANDARD".equals(query.getCoverageType())) {
            throw new BusinessException(400, "当前仅支持 PRODUCT_WITHOUT_STANDARD，其他质量标准覆盖口径待确认");
        }
        if (query.getLimit() == null) {
            query.setLimit(DEFAULT_LIMIT);
        }
        if (query.getLimit() < 1 || query.getLimit() > MAX_LIMIT) {
            throw new BusinessException(400, "limit 必须在 1 到 100 之间");
        }
        query.setResolvedAt(LocalDateTime.now());
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

    private AssayStandardCoverageGroupVO toGroup(AssayStandardCoverageGroupRow row) {
        long equivalentPieces = value(row.getTotalEquivalentPieces());
        boolean mixedScale = mixedScale(row);
        Integer piecesPerPallet = mixedScale ? null : positive(
                row.getPiecesPerPallet() == null ? row.getMinPiecesPerPallet() : row.getPiecesPerPallet());
        String productLabel = productLabel(row.getProductName(), row.getPackagingMethod(),
                row.getWeightPerPiece(), row.getPiecesPerPallet());
        return AssayStandardCoverageGroupVO.builder()
                .groupLabel(productLabel)
                .productLabel(productLabel)
                .coverageLabel(coverageLabel(row))
                .affectedStockText(stockText(equivalentPieces, piecesPerPallet, mixedScale))
                .rawFullPallets(value(row.getRawFullPallets()))
                .rawLoosePieces(value(row.getRawLoosePieces()))
                .normalizedPallets(normalizedPallets(equivalentPieces, piecesPerPallet))
                .normalizedLoosePieces(normalizedLoosePieces(equivalentPieces, piecesPerPallet))
                .totalEquivalentPieces(equivalentPieces)
                .totalWeightText(weightText(row.getTotalWeight()))
                .inventoryRecordCount(value(row.getInventoryRecordCount()))
                .palletCount(value(row.getPalletCount()))
                .warehouseCount(value(row.getWarehouseCount()))
                .latestInboundTime(row.getLatestInboundTime())
                .warehouseLabels(warehouseLabels(row.getWarehouseNames()))
                .riskLabels(List.of("无法自动判定化验合格性"))
                .build();
    }

    private String coverageLabel(AssayStandardCoverageGroupRow row) {
        if (value(row.getRelationCount()) == 0) {
            return "未绑定质量标准";
        }
        if (value(row.getEnabledRelationCount()) == 0) {
            return "未启用有效标准关系";
        }
        return "未绑定有效质量标准";
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

    private String dateRangeLabel(AssayStandardCoverageQueryDTO query) {
        return "当前有效标准关系";
    }

    private String summaryText(String scopeLabel, int groupCount) {
        if (groupCount == 0) {
            return "未查询到" + scopeLabel + "中缺少有效质量标准的当前在库产品。";
        }
        return scopeLabel + "共有 " + groupCount + " 个当前在库产品未绑定有效质量标准。";
    }

    private List<String> notes(AssayStandardCoverageQueryDTO query) {
        List<String> notes = new ArrayList<>();
        notes.add("第一版全集为当前在库产品。");
        notes.add("有效质量标准指产品存在启用的标准关系，且关系当前生效，标准状态为空或 ENABLED。");
        notes.add("无标准表示无法自动判定化验合格性，不等同于化验不合格。");
        notes.add("ASSAY_WITHOUT_STANDARD 和 UNUSED_STANDARD 的时间窗口及历史口径待确认，当前不会自动推断。");
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

    private String warehouseLabel(String name) {
        if (name == null || name.isBlank()) {
            return "未命名库位";
        }
        return name.matches(".*(库位|仓库)$") ? name : name + "号库位";
    }

    private String stockText(long pieces, Integer piecesPerPallet, boolean mixedScale) {
        if (mixedScale || piecesPerPallet == null) {
            return pieces + "件（跨规格）";
        }
        return pieces / piecesPerPallet + "板" + pieces % piecesPerPallet + "件";
    }

    private String totalWeightText(List<AssayStandardCoverageGroupRow> rows) {
        BigDecimal total = rows == null ? BigDecimal.ZERO : rows.stream()
                .map(AssayStandardCoverageGroupRow::getTotalWeight)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return weightText(total);
    }

    private String weightText(BigDecimal weight) {
        return weight == null ? null : decimal(weight) + "kg";
    }

    private Long normalizedPallets(long pieces, Integer piecesPerPallet) {
        return piecesPerPallet == null ? null : pieces / piecesPerPallet;
    }

    private Long normalizedLoosePieces(long pieces, Integer piecesPerPallet) {
        return piecesPerPallet == null ? null : pieces % piecesPerPallet;
    }

    private Integer commonPiecesPerPallet(List<AssayStandardCoverageGroupRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        Integer value = null;
        for (AssayStandardCoverageGroupRow row : rows) {
            Integer current = positive(row.getPiecesPerPallet());
            if (current == null) {
                return null;
            }
            if (value == null) {
                value = current;
            } else if (!value.equals(current)) {
                return null;
            }
        }
        return value;
    }

    private boolean mixedScale(AssayStandardCoverageGroupRow row) {
        return row.getMinPiecesPerPallet() == null
                || row.getMaxPiecesPerPallet() == null
                || !row.getMinPiecesPerPallet().equals(row.getMaxPiecesPerPallet());
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

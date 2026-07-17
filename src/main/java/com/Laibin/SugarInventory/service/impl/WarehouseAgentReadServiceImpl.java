package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.WarehouseCapacityDistributionQueryDTO;
import com.Laibin.SugarInventory.domain.vo.VWarehouseCapacity;
import com.Laibin.SugarInventory.domain.vo.WarehouseCapacityDistributionVO;
import com.Laibin.SugarInventory.service.InventoryService;
import com.Laibin.SugarInventory.service.WarehouseAgentReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import com.Laibin.SugarInventory.mapper.PalletFlowRecordMapper;
import com.Laibin.SugarInventory.domain.dto.WarehouseRecentOperationsAgentQueryDTO;
import com.Laibin.SugarInventory.domain.vo.WarehouseRecentOperationsAgentVO;
import com.Laibin.SugarInventory.domain.vo.WarehouseRecentOperationVO;
import java.util.ArrayList;
import com.Laibin.SugarInventory.mapper.InventorySummaryMapper;
import com.Laibin.SugarInventory.mapper.model.WarehouseMixedStorageFactRow;
import com.Laibin.SugarInventory.domain.dto.WarehouseMixedStorageFactsQueryDTO;
import com.Laibin.SugarInventory.domain.vo.WarehouseMixedStorageFactsVO;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WarehouseAgentReadServiceImpl implements WarehouseAgentReadService {
    private static final Set<String> BANDS = Set.of("ANY", "EMPTY", "LOW", "MEDIUM", "HIGH", "FULL");
    private final InventoryService inventoryService;
    private final PalletFlowRecordMapper palletFlowRecordMapper;
    private final InventorySummaryMapper inventorySummaryMapper;

    @Override
    public WarehouseCapacityDistributionVO queryCapacityDistribution(WarehouseCapacityDistributionQueryDTO query) {
        WarehouseCapacityDistributionQueryDTO source = query == null ? new WarehouseCapacityDistributionQueryDTO() : query;
        int page = source.getPage() == null ? 1 : source.getPage();
        int size = source.getSize() == null ? 20 : source.getSize();
        if (page < 1 || size < 1 || size > 50) throw new BusinessException(400, "分页参数超出允许范围");
        String band = source.getOccupancyBand() == null ? "ANY" : source.getOccupancyBand().trim().toUpperCase();
        if (!BANDS.contains(band)) throw new BusinessException(400, "occupancyBand 不受支持");
        var scope = source.getWarehouseScope() == null ? new WarehouseCapacityDistributionQueryDTO.WarehouseScope() : source.getWarehouseScope();
        String scopeType = scope.getType() == null ? "ALL" : scope.getType().trim().toUpperCase();
        if (!Set.of("ALL", "SINGLE_WAREHOUSE").contains(scopeType)) throw new BusinessException(400, "warehouseScope.type 不受支持");
        if ("SINGLE_WAREHOUSE".equals(scopeType) && (scope.getWarehouseId() == null || scope.getWarehouseId() <= 0)) {
            throw new BusinessException(400, "SINGLE_WAREHOUSE 必须提供 warehouseId");
        }
        List<VWarehouseCapacity> sourceRows = inventoryService.getWarehouses();
        List<VWarehouseCapacity> all = sourceRows == null ? List.of() : sourceRows;
        List<WarehouseCapacityDistributionVO.Row> matched = all.stream()
                .filter(item -> "ALL".equals(scopeType) || item.getWarehouseId() == scope.getWarehouseId())
                .map(this::row)
                .filter(item -> "ANY".equals(band) || band.equals(item.getOccupancyBand()))
                .filter(item -> !Boolean.TRUE.equals(source.getOnlyAvailable()) || item.getRemainingCapacity().compareTo(BigDecimal.ZERO) > 0)
                .toList();
        int from = Math.min((page - 1) * size, matched.size());
        int to = Math.min(from + size, matched.size());
        List<WarehouseCapacityDistributionVO.Row> records = matched.subList(from, to);
        BigDecimal current = matched.stream().map(WarehouseCapacityDistributionVO.Row::getCurrentCapacity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal maximum = matched.stream().map(WarehouseCapacityDistributionVO.Row::getMaximumCapacity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = matched.stream().map(WarehouseCapacityDistributionVO.Row::getRemainingCapacity).reduce(BigDecimal.ZERO, BigDecimal::add);
        return WarehouseCapacityDistributionVO.builder().dataScope("CURRENT_WAREHOUSE_CAPACITY_FACTS")
                .total(matched.size()).page(page).size(size).records(records)
                .summary(WarehouseCapacityDistributionVO.Summary.builder().warehouseCount(matched.size())
                        .currentCapacity(current).maximumCapacity(maximum).remainingCapacity(remaining)
                        .occupancyRate(rate(current, maximum))
                        .emptyCount((int) matched.stream().filter(item -> "EMPTY".equals(item.getOccupancyBand())).count())
                        .fullCount((int) matched.stream().filter(item -> "FULL".equals(item.getOccupancyBand())).count()).build())
                .occupancyBandDefinition(bandDefinition())
                .limitations(List.of("LOW/MEDIUM/HIGH 是固定展示分段，不是业务风险阈值或库位分配规则。",
                        "容量单位沿用现有 warehouse.cur_capacity/max_capacity 配置；当前业务尚未确认所有库位是否统一为托盘位。",
                        "结果仅表示查询时点容量事实，不执行入库、调拨或容量修改。"))
                .build();
    }

    private WarehouseCapacityDistributionVO.Row row(VWarehouseCapacity item) {
        BigDecimal current = value(item.getCurCapacity());
        BigDecimal maximum = value(item.getMaxCapacity());
        BigDecimal remaining = maximum.subtract(current).max(BigDecimal.ZERO);
        BigDecimal rate = rate(current, maximum);
        return WarehouseCapacityDistributionVO.Row.builder().warehouseName(item.getWarehouseName()).status(item.getStatus())
                .currentCapacity(current).maximumCapacity(maximum).remainingCapacity(remaining).occupancyRate(rate)
                .occupancyBand(band(rate, current, maximum)).currentPalletCount(item.getCurrentPalletCount())
                .currentProductCount(item.getCurrentProductCount()).build();
    }

    private BigDecimal value(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private BigDecimal rate(BigDecimal current, BigDecimal maximum) {
        return maximum.signum() <= 0 ? BigDecimal.ZERO : current.multiply(BigDecimal.valueOf(100)).divide(maximum, 2, RoundingMode.HALF_UP);
    }
    private String band(BigDecimal rate, BigDecimal current, BigDecimal maximum) {
        if (current.signum() == 0) return "EMPTY";
        if (maximum.signum() > 0 && current.compareTo(maximum) >= 0) return "FULL";
        if (rate.compareTo(BigDecimal.valueOf(50)) < 0) return "LOW";
        if (rate.compareTo(BigDecimal.valueOf(80)) < 0) return "MEDIUM";
        return "HIGH";
    }
    private LinkedHashMap<String, String> bandDefinition() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        result.put("EMPTY", "currentCapacity = 0"); result.put("LOW", "0% < occupancyRate < 50%");
        result.put("MEDIUM", "50% <= occupancyRate < 80%"); result.put("HIGH", "80% <= occupancyRate < 100%");
        result.put("FULL", "currentCapacity >= maximumCapacity 且 maximumCapacity > 0"); return result;
    }

    @Override
    public WarehouseRecentOperationsAgentVO queryRecentOperations(WarehouseRecentOperationsAgentQueryDTO query) {
        WarehouseRecentOperationsAgentQueryDTO source = query == null ? new WarehouseRecentOperationsAgentQueryDTO() : query;
        if (source.getWarehouseId() != null && source.getWarehouseId() <= 0) throw new BusinessException(400, "warehouseId 必须为正整数");
        if (source.getFrom() != null && source.getTo() != null && source.getFrom().isAfter(source.getTo())) {
            throw new BusinessException(400, "开始时间不能晚于结束时间");
        }
        int limit = source.getLimit() == null ? 20 : source.getLimit();
        if (limit < 1 || limit > 50) throw new BusinessException(400, "limit 必须在 1 到 50 之间");
        List<String> categories = source.getEventTypes() == null ? List.of() : source.getEventTypes();
        if (categories.size() > 3 || categories.stream().anyMatch(value -> !Set.of("INBOUND", "OUTBOUND", "TRANSFER").contains(value))) {
            throw new BusinessException(400, "eventTypes 不受支持");
        }
        List<String> rawTypes = new ArrayList<>();
        if (categories.contains("INBOUND")) { rawTypes.add("SEMI_INSTOCK"); rawTypes.add("FINISH_INSTOCK"); }
        if (categories.contains("OUTBOUND")) { rawTypes.add("OUT"); rawTypes.add("PREPARE_CONSUMED"); rawTypes.add("ORDER_MATERIAL_PICK"); }
        if (categories.contains("TRANSFER")) rawTypes.add("TRANSFER");
        List<WarehouseRecentOperationVO> rows = palletFlowRecordMapper.queryRecentWarehouseOperations(
                source.getWarehouseId(), source.getFrom(), source.getTo(), rawTypes, limit);
        List<WarehouseRecentOperationsAgentVO.Row> safeRows = (rows == null ? List.<WarehouseRecentOperationVO>of() : rows).stream()
                .map(item -> WarehouseRecentOperationsAgentVO.Row.builder().operationTime(item.getOperationTime())
                        .eventType(category(item.getOperationType())).operationName(item.getOperationName())
                        .operatorName(item.getOperatorName()).palletCode(item.getPalletCode()).productName(item.getProductName())
                        .fromWarehouseName(item.getFromWarehouseName()).toWarehouseName(item.getToWarehouseName())
                        .remark(item.getRemark()).build()).toList();
        return WarehouseRecentOperationsAgentVO.builder().dataScope("RECORDED_WAREHOUSE_PALLET_FLOW_EVENTS")
                .count(safeRows.size()).records(safeRows)
                .limitations(List.of("仅覆盖 pallet_flow_record 中已登记的托盘流转事件，不代表完整操作日志或历史事件账。",
                        "“当前库位有什么”应查询库存分布，本工具只回答已登记的近期流转。",
                        "结果不执行入库、出库、调拨或库存变更。"))
                .build();
    }

    private String category(String raw) {
        if (raw == null) return "OTHER";
        if (Set.of("SEMI_INSTOCK", "FINISH_INSTOCK").contains(raw)) return "INBOUND";
        if (Set.of("OUT", "PREPARE_CONSUMED", "ORDER_MATERIAL_PICK").contains(raw)) return "OUTBOUND";
        if ("TRANSFER".equals(raw)) return "TRANSFER";
        return "OTHER";
    }

    @Override
    public WarehouseMixedStorageFactsVO queryMixedStorageFacts(WarehouseMixedStorageFactsQueryDTO query) {
        WarehouseMixedStorageFactsQueryDTO source = query == null ? new WarehouseMixedStorageFactsQueryDTO() : query;
        if (source.getWarehouseId() != null && source.getWarehouseId() <= 0) throw new BusinessException(400, "warehouseId 必须为正整数");
        String factType = source.getFactType() == null ? "ANY" : source.getFactType().trim().toUpperCase();
        if (!Set.of("ANY", "MULTIPLE_PRODUCTS", "MULTIPLE_SPECIFICATIONS").contains(factType)) {
            throw new BusinessException(400, "factType 不受支持");
        }
        int limit = source.getLimit() == null ? 20 : source.getLimit();
        if (limit < 1 || limit > 50) throw new BusinessException(400, "limit 必须在 1 到 50 之间");
        List<WarehouseMixedStorageFactRow> raw = inventorySummaryMapper.selectWarehouseMixedStorageFacts(source.getWarehouseId());
        List<WarehouseMixedStorageFactsVO.Row> records = (raw == null ? List.<WarehouseMixedStorageFactRow>of() : raw).stream()
                .filter(item -> matchesFact(item, factType)).limit(limit).map(this::mixedRow).toList();
        return WarehouseMixedStorageFactsVO.builder().dataScope("CURRENT_WAREHOUSE_MULTI_PRODUCT_SPEC_FACTS")
                .count(records.size()).records(records)
                .limitations(List.of("仅返回同一库位当前存在多个产品或多个产品/筛网/状态组合的客观事实。",
                        "当前没有经确认的混放风险规则；多产品或多规格事实不得自动解释为违规、风险或需要调拨。",
                        "结果不执行移库、调拨、入库或库存修改。"))
                .build();
    }

    private boolean matchesFact(WarehouseMixedStorageFactRow row, String factType) {
        int products = number(row.getProductCount()); int specs = number(row.getSpecificationCount());
        if ("MULTIPLE_PRODUCTS".equals(factType)) return products > 1;
        if ("MULTIPLE_SPECIFICATIONS".equals(factType)) return specs > 1;
        return products > 1 || specs > 1;
    }

    private WarehouseMixedStorageFactsVO.Row mixedRow(WarehouseMixedStorageFactRow row) {
        int products = number(row.getProductCount()); int specs = number(row.getSpecificationCount());
        List<String> facts = new ArrayList<>();
        if (products > 1) facts.add("同库位存在 " + products + " 个不同产品");
        if (specs > 1) facts.add("同库位存在 " + specs + " 个产品/筛网/状态组合");
        return WarehouseMixedStorageFactsVO.Row.builder().warehouseName(row.getWarehouseName())
                .productCount(products).productTypeCount(number(row.getProductTypeCount())).specificationCount(specs)
                .inventoryRecordCount(number(row.getInventoryRecordCount())).productLabels(labels(row.getProductLabels()))
                .productTypeLabels(labels(row.getProductTypeLabels())).productStatusLabels(labels(row.getProductStatusLabels()))
                .screenMeshLabels(labels(row.getScreenMeshLabels())).observedFacts(facts).build();
    }

    private int number(Integer value) { return value == null ? 0 : value; }
    private List<String> labels(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("、")).map(String::trim).filter(item -> !item.isEmpty()).limit(20).toList();
    }
}

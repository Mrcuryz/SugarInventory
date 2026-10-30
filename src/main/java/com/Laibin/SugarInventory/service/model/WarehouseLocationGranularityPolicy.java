package com.Laibin.SugarInventory.service.model;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.Warehouse;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 库位级库存兼容策略。
 *
 * <p>数据库继续保留 side/row_number/layer 字段及唯一索引，但它们不再表达真实物理位置。
 * 新库存统一写入左侧一层，排号仅作为不可见的技术序号以满足既有唯一约束。</p>
 */
public final class WarehouseLocationGranularityPolicy {
    public static final String INTERNAL_SIDE = "左";
    public static final int INTERNAL_LAYER = 1;

    private WarehouseLocationGranularityPolicy() {
    }

    public static int requireCapacity(Warehouse warehouse) {
        if (warehouse == null || warehouse.getId() == null) {
            throw new BusinessException("库位不存在");
        }
        Integer capacity = warehouse.getMaxCapacity();
        if (capacity == null || capacity <= 0) {
            throw new BusinessException("目标库位未配置有效最大容量");
        }
        return capacity;
    }

    public static int currentCapacity(Warehouse warehouse) {
        return warehouse.getCurCapacity() == null ? 0 : Math.max(warehouse.getCurCapacity(), 0);
    }

    public static int remainingCapacity(Warehouse warehouse) {
        return Math.max(requireCapacity(warehouse) - currentCapacity(warehouse), 0);
    }

    public static int nextTechnicalRow(List<Integer> usedRows, int maxCapacity) {
        Set<Integer> used = new HashSet<>();
        if (usedRows != null) {
            usedRows.stream().filter(row -> row != null && row > 0).forEach(used::add);
        }
        for (int row = 1; row <= maxCapacity; row++) {
            if (!used.contains(row)) {
                return row;
            }
        }
        throw new BusinessException("目标库位当前没有可用容量");
    }
}

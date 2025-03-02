package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.InventoryQueryDTO;
import com.Laibin.SugarInventory.domain.vo.VInventorySummary;
import com.Laibin.SugarInventory.domain.vo.VWarehouseCapacity;

import java.util.List;

public interface InventoryService {

    PageResult<VInventorySummary> getInventorySummary(InventoryQueryDTO query);

    List<VWarehouseCapacity> getWarehouses();
}

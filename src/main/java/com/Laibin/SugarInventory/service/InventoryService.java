package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.InventoryQueryDTO;
import com.Laibin.SugarInventory.domain.dto.OutProductQueryDTO;
import com.Laibin.SugarInventory.domain.dto.OutStockBatchQueryDTO;
import com.Laibin.SugarInventory.domain.vo.OutProductVO;
import com.Laibin.SugarInventory.domain.vo.OutWarehouseVO;
import com.Laibin.SugarInventory.domain.vo.VInventorySummary;
import com.Laibin.SugarInventory.domain.vo.VWarehouseCapacity;

import java.util.List;

public interface InventoryService {

    PageResult<VInventorySummary> getInventorySummary(InventoryQueryDTO query);

    List<OutWarehouseVO> getQualifiedWarehouses(OutProductQueryDTO queryDTO);

    List<OutProductVO> getInventoryDetails(Integer warehouseId, OutProductQueryDTO queryDTO);

    List<VWarehouseCapacity> getWarehouses();

    PageResult<VWarehouseCapacity> queryWarehouses(String warehouseName, List<Integer> warehouseIds, Integer page, Integer size, String status);

    PageResult<VWarehouseCapacity> batchQueryWarehouses(OutStockBatchQueryDTO queryDTO);

    /**
     * 获取所有产品的库存信息
     *
     * @param productStatus 产品状态：成品/半成品
     */
    List<VInventorySummary> getProductStock(String productStatus, String productName);
}

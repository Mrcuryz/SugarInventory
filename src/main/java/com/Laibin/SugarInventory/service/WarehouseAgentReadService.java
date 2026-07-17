package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.WarehouseCapacityDistributionQueryDTO;
import com.Laibin.SugarInventory.domain.vo.WarehouseCapacityDistributionVO;
import com.Laibin.SugarInventory.domain.dto.WarehouseRecentOperationsAgentQueryDTO;
import com.Laibin.SugarInventory.domain.vo.WarehouseRecentOperationsAgentVO;
import com.Laibin.SugarInventory.domain.dto.WarehouseMixedStorageFactsQueryDTO;
import com.Laibin.SugarInventory.domain.vo.WarehouseMixedStorageFactsVO;

public interface WarehouseAgentReadService {
    WarehouseCapacityDistributionVO queryCapacityDistribution(WarehouseCapacityDistributionQueryDTO query);
    WarehouseRecentOperationsAgentVO queryRecentOperations(WarehouseRecentOperationsAgentQueryDTO query);
    WarehouseMixedStorageFactsVO queryMixedStorageFacts(WarehouseMixedStorageFactsQueryDTO query);
}

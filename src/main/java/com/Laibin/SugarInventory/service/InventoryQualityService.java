package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.InventoryQualityQueryDTO;
import com.Laibin.SugarInventory.domain.vo.InventoryQualityVO;

public interface InventoryQualityService {
    InventoryQualityVO query(InventoryQualityQueryDTO query);
}

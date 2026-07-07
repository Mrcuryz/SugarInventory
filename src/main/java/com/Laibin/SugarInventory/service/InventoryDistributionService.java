package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.InventoryDistributionQueryDTO;
import com.Laibin.SugarInventory.domain.vo.InventoryDistributionVO;

public interface InventoryDistributionService {
    InventoryDistributionVO getDistribution(InventoryDistributionQueryDTO query);
}

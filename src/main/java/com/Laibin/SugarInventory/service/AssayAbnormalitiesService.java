package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.AssayAbnormalitiesQueryDTO;
import com.Laibin.SugarInventory.domain.vo.AssayAbnormalitiesVO;

public interface AssayAbnormalitiesService {
    AssayAbnormalitiesVO queryAbnormalities(AssayAbnormalitiesQueryDTO query);
}

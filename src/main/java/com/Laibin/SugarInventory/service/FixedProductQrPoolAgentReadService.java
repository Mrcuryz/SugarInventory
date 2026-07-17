package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.FixedProductQrPoolAgentQueryDTO;
import com.Laibin.SugarInventory.domain.vo.FixedProductQrPoolAgentVO;

public interface FixedProductQrPoolAgentReadService {
    FixedProductQrPoolAgentVO queryFixedProductQrPool(FixedProductQrPoolAgentQueryDTO query);
}

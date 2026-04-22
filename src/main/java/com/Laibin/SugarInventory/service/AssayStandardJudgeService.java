package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.service.model.AssayJudgeOutcome;

public interface AssayStandardJudgeService {
    AssayJudgeOutcome judge(Product product, AssaySubmitDTO dto);
}

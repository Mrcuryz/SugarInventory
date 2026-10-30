package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.QualityStandard;
import java.util.List;
import com.Laibin.SugarInventory.service.model.AssayJudgeOutcome;

public interface AssayStandardJudgeService {
    AssayJudgeOutcome judge(Product product, AssaySubmitDTO dto);

    /** Evaluate the already locked, ordered standards without rereading a stale transaction snapshot. */
    AssayJudgeOutcome judgeStandards(List<QualityStandard> standards, AssaySubmitDTO dto);
}

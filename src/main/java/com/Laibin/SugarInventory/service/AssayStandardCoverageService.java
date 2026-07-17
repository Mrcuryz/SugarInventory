package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.AssayStandardCoverageQueryDTO;
import com.Laibin.SugarInventory.domain.vo.AssayStandardCoverageVO;

public interface AssayStandardCoverageService {
    AssayStandardCoverageVO queryCoverage(AssayStandardCoverageQueryDTO query);
}

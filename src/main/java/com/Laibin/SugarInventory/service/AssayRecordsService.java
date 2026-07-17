package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.AssayRecordsQueryDTO;
import com.Laibin.SugarInventory.domain.vo.AssayRecordsVO;

public interface AssayRecordsService {
    AssayRecordsVO queryRecords(AssayRecordsQueryDTO query);
}

package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.AssayReportDetailQueryDTO;
import com.Laibin.SugarInventory.domain.vo.AssayReportDetailVO;

public interface AssayReportDetailService {
    AssayReportDetailVO getReportDetail(AssayReportDetailQueryDTO query);
}

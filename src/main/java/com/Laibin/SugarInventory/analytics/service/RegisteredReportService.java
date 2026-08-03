package com.Laibin.SugarInventory.analytics.service;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;

public interface RegisteredReportService {
    RegisteredReportRunVO run(RegisteredReportRunQueryDTO query);
}

package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.OperationLogQueryDTO;
import com.Laibin.SugarInventory.domain.po.OperationLog;

public interface OperationLogService {
    void saveOperationLog(OperationLog log);

    PageResult<OperationLog> queryOperationLogs(OperationLogQueryDTO queryDTO);
}

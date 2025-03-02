package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.po.OperationLog;

public interface OperationLogService {
    void saveOperationLog(OperationLog log);
}

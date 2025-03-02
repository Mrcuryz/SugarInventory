package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.domain.po.OperationLog;
import com.Laibin.SugarInventory.mapper.OperationLogMapper;
import com.Laibin.SugarInventory.service.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OperationLogServiceImpl implements OperationLogService {

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Override
    public void saveOperationLog(OperationLog log) {
        operationLogMapper.insert(log);
    }
}
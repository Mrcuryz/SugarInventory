package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.OperationLogQueryDTO;
import com.Laibin.SugarInventory.domain.po.OperationLog;
import com.Laibin.SugarInventory.mapper.OperationLogMapper;
import com.Laibin.SugarInventory.service.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OperationLogServiceImpl implements OperationLogService {

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Override
    public void saveOperationLog(OperationLog log) {
        operationLogMapper.insert(log);
    }

    @Override
    public PageResult<OperationLog> queryOperationLogs(OperationLogQueryDTO queryDTO) {
        int offset = (queryDTO.getPage() - 1) * queryDTO.getSize();

        List<OperationLog> records =  operationLogMapper
                .selectOperationLogs(queryDTO, offset, queryDTO.getSize());

        Long total = operationLogMapper.countOperationLogs(queryDTO);

        return new PageResult<>(total, records);
    }
}
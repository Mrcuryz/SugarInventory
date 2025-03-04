package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.OperationLogQueryDTO;
import com.Laibin.SugarInventory.domain.po.OperationLog;
import com.Laibin.SugarInventory.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "操作日志管理", description = "操作日志管理相关接口")
@RequestMapping("/api/logs")
public class OperationLogController {

    @Autowired
    private OperationLogService operationLogService;

    @PostMapping("/query")
    @PreAuthorize("hasAuthority('log:view')")
    @Operation(summary = "查询操作日志", description = "分页查询操作日志，可根据表名、操作类型、操作人、时间段筛选")
    public Result<PageResult<OperationLog>> queryOperationLogs(
            @RequestBody OperationLogQueryDTO queryDTO) {
        try {
            PageResult<OperationLog> logs = operationLogService.queryOperationLogs(queryDTO);
            return Result.success(logs);
        } catch (Exception e) {
            return Result.error(500,"查询操作日志失败: " + e.getMessage());
        }
    }
}
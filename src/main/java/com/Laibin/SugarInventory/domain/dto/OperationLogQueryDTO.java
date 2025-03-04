package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OperationLogQueryDTO {
    private String tableName;         // 操作表名（可选）
    private String operationType;     // 操作类型（INSERT/UPDATE/DELETE，可选）
    private String operator;          // 操作人（可选）
    private LocalDateTime startTime;  // 操作时间起始（可选）
    private LocalDateTime endTime;    // 操作时间结束（可选）
    private Integer page = 1;      // 页码
    private Integer size = 10;    // 每页条数
}
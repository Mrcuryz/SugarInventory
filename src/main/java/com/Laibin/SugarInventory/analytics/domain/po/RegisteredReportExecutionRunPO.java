package com.Laibin.SugarInventory.analytics.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_report_execution_run")
public class RegisteredReportExecutionRunPO {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String executionRef;
    private String reportDefinitionId;
    private Integer ownerUserId;
    private String status;
    private Long durationMs;
    private Boolean partialData;
    private String failureCode;
    private String failureSummary;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}

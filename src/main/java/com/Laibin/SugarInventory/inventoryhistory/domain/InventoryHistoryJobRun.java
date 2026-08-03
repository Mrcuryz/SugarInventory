package com.Laibin.SugarInventory.inventoryhistory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("inventory_history_job_run")
public class InventoryHistoryJobRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String jobRunId;
    private LocalDate businessDate;
    private String jobMode;
    private String triggerSource;
    private String status;
    private String snapshotRunId;
    private Boolean snapshotReused;
    private String reconciliationRunId;
    private String reconciliationStatus;
    private String errorCode;
    private String errorMessage;
    private String executorInstance;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

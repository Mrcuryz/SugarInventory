package com.Laibin.SugarInventory.inventoryhistory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("inventory_reconciliation_run")
public class InventoryReconciliationRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String reconciliationRunId;
    private LocalDate businessDate;
    private Integer revision;
    private String openingSnapshotRunId;
    private String closingSnapshotRunId;
    private String ruleVersion;
    private String status;
    private Integer globalResultCount;
    private Integer warehouseResultCount;
    private Integer failedResultCount;
    private Integer issueCount;
    private Integer maxAbsPieceDifference;
    private String reason;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}

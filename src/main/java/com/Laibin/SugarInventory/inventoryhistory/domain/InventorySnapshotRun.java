package com.Laibin.SugarInventory.inventoryhistory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("inventory_snapshot_run")
public class InventorySnapshotRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String snapshotRunId;
    private LocalDate snapshotDate;
    private String snapshotType;
    private Integer revision;
    private LocalDateTime dataAsOf;
    private String ruleVersion;
    private Integer rowCount;
    private String contentSha256;
    private String status;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}

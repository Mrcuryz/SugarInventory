package com.Laibin.SugarInventory.inventoryhistory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("inventory_trend_release_gate")
public class InventoryTrendReleaseGate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String gateKey;
    private String status;
    private Integer requiredPassedDays;
    private Integer consecutivePassedDays;
    private LocalDate windowStartDate;
    private LocalDate windowEndDate;
    private String lastReconciliationRunId;
    private String ruleVersion;
    private String reason;
    private LocalDateTime evaluatedAt;
}

package com.Laibin.SugarInventory.inventoryhistory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("inventory_data_quality_issue")
public class InventoryDataQualityIssue {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String issueId;
    private String issueFingerprint;
    private String reconciliationRunId;
    private LocalDate businessDate;
    private String issueCode;
    private String severity;
    private String sourceType;
    private Long sourceRecordId;
    private String businessActionId;
    private Integer productId;
    private Integer warehouseId;
    private String message;
    private String detailsJson;
    private String status;
    private LocalDateTime createdAt;
}

package com.Laibin.SugarInventory.production.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("production_daily_report")
public class ProductionDailyReport {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private LocalDate reportDate;
    private LocalDate preparedDate;
    private String preparedByName;
    private String status;
    private Integer version;
    private Integer createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private Integer updatedBy;
    private String updatedByName;
    private LocalDateTime updatedAt;
    private Integer submittedBy;
    private String submittedByName;
    private LocalDateTime submittedAt;
}

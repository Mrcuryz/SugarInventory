package com.Laibin.SugarInventory.production.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("production_daily_report_section")
public class ProductionDailyReportSection {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private String departmentCode;
    private String departmentNameSnapshot;
    private String status;
    private Integer version;
    private Integer updatedBy;
    private String updatedByName;
    private LocalDateTime updatedAt;
    private Integer submittedBy;
    private String submittedByName;
    private LocalDateTime submittedAt;
}

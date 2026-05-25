package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("production_report_record")
public class ProductionReportRecord {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("source_text")
    private String sourceText;

    @TableField("report_type")
    private String reportType;

    @TableField("report_date")
    private LocalDate reportDate;

    @TableField("inbound_json")
    private String inboundJson;

    @TableField("consumption_text")
    private String consumptionText;

    @TableField("consumption_json")
    private String consumptionJson;

    @TableField("unmatched_names")
    private String unmatchedNames;

    @TableField("inbound_task_ids")
    private String inboundTaskIds;

    @TableField("pallet_codes")
    private String palletCodes;

    @TableField("status")
    private String status;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_by")
    private Integer createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("remark")
    private String remark;
}

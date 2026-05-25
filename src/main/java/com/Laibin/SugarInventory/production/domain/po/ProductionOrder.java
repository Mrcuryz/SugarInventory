package com.Laibin.SugarInventory.production.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("production_order")
public class ProductionOrder {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("order_no")
    private String orderNo;

    @TableField("order_type")
    private String orderType;

    @TableField("status")
    private String status;

    @TableField("production_date")
    private LocalDate productionDate;

    @TableField("planned_material_json")
    private String plannedMaterialJson;

    @TableField("planned_output_json")
    private String plannedOutputJson;

    @TableField("team_name")
    private String teamName;

    @TableField("remark")
    private String remark;

    @TableField("created_by")
    private Integer createdBy;

    @TableField("created_by_name")
    private String createdByName;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;
}

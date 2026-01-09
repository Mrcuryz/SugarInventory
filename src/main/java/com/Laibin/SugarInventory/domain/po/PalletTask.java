package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@Data
/**
 * 托盘任务实体，对应 pallet_task，用于记录入库/出库等待处理的任务。
 */
@TableName("pallet_task")
public class PalletTask extends BaseEntity implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("pallet_code_id")
    private Integer palletCodeId;

    @TableField("task_type")
    private String taskType;

    @TableField("status")
    private String status;

    @TableField("product_id")
    private Integer productId;

    @TableField("product_status")
    private String productStatus;

    @TableField("production_date")
    private LocalDate productionDate;

    @TableField("screen_mesh_id")
    private Integer screenMeshId;

    @TableField("assay_id")
    private Integer assayId;

    @TableField("created_by")
    private Integer createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("confirmed_by")
    private Integer confirmedBy;

    @TableField("confirmed_at")
    private LocalDateTime confirmedAt;

    @TableField("remark")
    private String remark;

    @Override
    public Integer getId() {
        return id;
    }
}

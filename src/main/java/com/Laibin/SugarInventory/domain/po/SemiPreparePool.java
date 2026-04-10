package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("semi_prepare_pool")
public class SemiPreparePool extends BaseEntity implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("product_id")
    private Integer productId;

    @TableField("production_date")
    private LocalDate productionDate;

    @TableField("pallet_code_id")
    private Integer palletCodeId;

    @TableField("cycle_no")
    private Integer cycleNo;

    @TableField("status")
    private String status;

    @TableField("remark")
    private String remark;

    @TableField("created_by")
    private Integer createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_by")
    private Integer updatedBy;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @Override
    public Integer getId() {
        return id;
    }
}

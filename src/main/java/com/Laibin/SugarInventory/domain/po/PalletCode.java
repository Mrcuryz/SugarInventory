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

/**
 * <p>
 * 托盘码实体
 * </p>
 *
 * @author Mrcury
 * @since 2025-12-08
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("pallet_code")
public class PalletCode extends BaseEntity implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("code")
    private String code;

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

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private Integer createdBy;

    @TableField("updated_by")
    private Integer updatedBy;

    @Override
    public Integer getId() {
        return id;
    }
}


package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@Data
@TableName("in_stock")
public class InStock extends BaseEntity implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("product_id")
    private Integer productId;

    @Setter
    @TableField("warehouse_id")
    private Integer warehouseId;

    @TableField("quantity")
    private Integer quantity;

    @TableField("weight_per_piece")
    private BigDecimal weightPerPiece;

    @TableField("entry_date")
    private LocalDate entryDate;

    @TableField("assay_id")
    private Integer assayId;

    @TableField("total_weight")
    private BigDecimal totalWeight;

    @TableField("semi_product_record_id")
    private Integer semiProductRecordId;

    @TableField("screen_mesh_id")
    private Integer screenMeshId;

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

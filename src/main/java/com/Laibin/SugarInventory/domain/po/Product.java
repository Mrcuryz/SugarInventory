package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
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
@TableName("product")
public class Product extends BaseEntity implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("product_name")
    private String productName;

    @TableField("product_type")
    private String productType;

    @TableField("status")
    private String status;

    @TableField("packaging_method")
    private String packagingMethod;

    @TableField("weight_per_piece")
    private BigDecimal weightPerPiece;

    @TableField("pieces_per_pallet")
    private Integer piecesPerPallet;

    @TableField("can_stack")
    private Boolean canStack;

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
}

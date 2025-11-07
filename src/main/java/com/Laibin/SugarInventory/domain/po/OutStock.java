package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("out_stock")
public class OutStock extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField(value = "warehouse_id")
    private Integer warehouseId;

    @TableField(value = "product_id")
    private Integer productId;

    @TableField(value = "quantity")
    private Integer quantity;

    @TableField(value = "pieces")
    private Integer pieces;

    @TableField(value = "out_type")
    private Integer outType;

    @TableField(value = "unit")
    private String unit;

    @TableField(value = "in_date")
    private LocalDate inDate;

    @TableField(value = "total_weight")
    private BigDecimal totalWeight;

    @TableField(value = "out_date")
    private LocalDate outDate;

    @TableField(value = "operator_id")
    private Integer operatorId;

    @TableField(value = "assay_id")
    private Integer assayId;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

}

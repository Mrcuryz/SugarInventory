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

    @TableField(value = "weight_per_piece")
    private BigDecimal weightPerPiece;

    @TableField(value = "in_date")
    private LocalDate inDate;

    @TableField(value = "out_date")
    private LocalDateTime outDate;

    @TableField(value = "operator_id")
    private Integer operatorId;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;
}
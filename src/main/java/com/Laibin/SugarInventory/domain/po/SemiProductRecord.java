package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
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
@Data
@TableName("semi_product_record")
public class SemiProductRecord extends BaseEntity implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private Integer productId;
    private Integer quantity;
    private LocalDate operationDate;
    private String operator;
    private LocalDateTime createdAt;
    private BigDecimal totalWeight;
    private Integer warehouseId;
    private Integer assayId;
    private Integer screenMeshId;
    private String unit;
}

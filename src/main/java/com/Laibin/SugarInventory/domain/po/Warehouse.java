package com.Laibin.SugarInventory.domain.po;

import com.Laibin.SugarInventory.util.JsonTypeHandler;
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
@TableName("warehouse")
public class Warehouse extends BaseEntity implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String warehouseId;
    @TableField(typeHandler = JsonTypeHandler.class)
    private Coordinates coordinates;
    private String status;
    private LocalDateTime createdAt;
    @TableField(value = "max_capacity")
    private BigDecimal maxCapacity;
    @TableField(value = "cur_capacity")
    private BigDecimal curCapacity;
}

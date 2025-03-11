package com.Laibin.SugarInventory.domain.po;

import com.Laibin.SugarInventory.util.JsonTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "库位实体")
@TableName("warehouse")
public class Warehouse extends BaseEntity implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String warehouseName;

    private String status;

    private LocalDateTime createdAt;

    @Schema(description = "最大库存量（板）")
    @TableField(value = "max_capacity")
    private Integer maxCapacity;

    @Schema(description = "当前库存量（板）")
    @TableField(value = "cur_capacity")
    private Integer curCapacity;

    @Schema(description = "最大排数")
    @TableField(value = "max_rows")
    private Integer maxRows;

    public String getWarehouseId(){
        return String.valueOf(id);
    }
}

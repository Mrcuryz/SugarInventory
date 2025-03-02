package com.Laibin.SugarInventory.domain.po;

import com.Laibin.SugarInventory.util.JsonTypeHandler;
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
@TableName("inventory")
public class Inventory extends BaseEntity implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("warehouse_id")
    private Integer warehouseId;

    @TableField("product_id")
    private Integer productId;

    @TableField("entry_date")
    private LocalDate entryDate;

    @TableField("total_quantity")
    private Integer totalQuantity;

    @TableField("assay_id")
    private Integer assayId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("in_stock_id")
    private Integer inStockId;

    @TableField("screen_mesh_id")
    private Integer screenMeshId;
}


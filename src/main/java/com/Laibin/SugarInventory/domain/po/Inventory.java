package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.math3.geometry.partitioning.Side;

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

    @TableField(value = "side")
    private String side;

    @TableField("row_number")
    private Integer rowNumber;

    @TableField("layer")
    private Integer layer;

    @TableField("entry_date")
    private LocalDate entryDate;

    @TableField("quantity")
    private Integer quantity;

    @TableField("assay_id")
    private Integer assayId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("in_stock_id")
    private Integer inStockId;

    @TableField("screen_mesh_id")
    private Integer screenMeshId;

    @TableField("semi_record_id")
    private Integer semiRecordId;

    @TableField("product_status")
    private String productStatus;
}


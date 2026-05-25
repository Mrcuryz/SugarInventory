package com.Laibin.SugarInventory.production.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("production_order_output_code")
public class ProductionOrderOutputCode {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("production_order_id")
    private Long productionOrderId;

    @TableField("order_no")
    private String orderNo;

    @TableField("output_id")
    private Long outputId;

    @TableField("pallet_code_id")
    private Integer palletCodeId;

    @TableField("label_code_id")
    private Long labelCodeId;

    @TableField("pallet_code")
    private String palletCode;

    @TableField("pallet_cycle_no")
    private Integer palletCycleNo;

    @TableField("product_id")
    private Integer productId;

    @TableField("product_name_snapshot")
    private String productNameSnapshot;

    @TableField("quantity")
    private Integer quantity;

    @TableField("unit")
    private String unit;

    @TableField("pieces")
    private Integer pieces;

    @TableField("pallet_task_id")
    private Integer palletTaskId;

    @TableField("inventory_id")
    private Integer inventoryId;

    @TableField("status")
    private String status;

    @TableField("printed_at")
    private LocalDateTime printedAt;

    @TableField("inbound_at")
    private LocalDateTime inboundAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("remark")
    private String remark;
}

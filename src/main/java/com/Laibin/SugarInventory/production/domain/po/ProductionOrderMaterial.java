package com.Laibin.SugarInventory.production.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("production_order_material")
public class ProductionOrderMaterial {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("production_order_id")
    private Long productionOrderId;

    @TableField("order_no")
    private String orderNo;

    @TableField("pallet_code_id")
    private Integer palletCodeId;

    @TableField("pallet_code")
    private String palletCode;

    @TableField("pallet_cycle_no")
    private Integer palletCycleNo;

    @TableField("inventory_id")
    private Integer inventoryId;

    @TableField("product_id")
    private Integer productId;

    @TableField("product_name_snapshot")
    private String productNameSnapshot;

    @TableField("product_status")
    private String productStatus;

    @TableField("production_date")
    private LocalDate productionDate;

    @TableField("warehouse_id")
    private Integer warehouseId;

    @TableField("warehouse_name_snapshot")
    private String warehouseNameSnapshot;

    @TableField("side")
    private String side;

    @TableField("`row_number`")
    private Integer rowNumber;

    @TableField("layer")
    private Integer layer;

    @TableField("quantity")
    private Integer quantity;

    @TableField("unit")
    private String unit;

    @TableField("pieces")
    private Integer pieces;

    @TableField("pieces_per_pallet")
    private Integer piecesPerPallet;

    @TableField("total_pieces")
    private Integer totalPieces;

    @TableField("weight_per_piece")
    private BigDecimal weightPerPiece;

    @TableField("total_weight")
    private BigDecimal totalWeight;

    @TableField("status")
    private String status;

    @TableField("picked_by")
    private Integer pickedBy;

    @TableField("picked_by_name")
    private String pickedByName;

    @TableField("picked_at")
    private LocalDateTime pickedAt;

    @TableField("canceled_by")
    private Integer canceledBy;

    @TableField("canceled_at")
    private LocalDateTime canceledAt;

    @TableField("cancel_reason")
    private String cancelReason;

    @TableField("remark")
    private String remark;
}

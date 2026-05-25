package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("production_prepare_ledger")
public class ProductionPrepareLedger {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("product_id")
    private Integer productId;

    @TableField("product_name_snapshot")
    private String productNameSnapshot;

    @TableField("product_status")
    private String productStatus;

    @TableField("production_date")
    private LocalDate productionDate;

    @TableField("screen_mesh_id")
    private Integer screenMeshId;

    @TableField("assay_id")
    private Integer assayId;

    @TableField("source_pallet_code_id")
    private Integer sourcePalletCodeId;

    @TableField("source_pallet_code")
    private String sourcePalletCode;

    @TableField("source_inventory_id")
    private Integer sourceInventoryId;

    @TableField("source_warehouse_id")
    private Integer sourceWarehouseId;

    @TableField("source_warehouse_name")
    private String sourceWarehouseName;

    @TableField("source_side")
    private String sourceSide;

    @TableField("source_row_number")
    private Integer sourceRowNumber;

    @TableField("source_layer")
    private Integer sourceLayer;

    @TableField("board_count_snapshot")
    private Integer boardCountSnapshot;

    @TableField("piece_count_snapshot")
    private Integer pieceCountSnapshot;

    @TableField("total_pieces")
    private Integer totalPieces;

    @TableField("pieces_per_pallet")
    private Integer piecesPerPallet;

    @TableField("source_task_id")
    private Integer sourceTaskId;

    @TableField("source_flow_id")
    private Long sourceFlowId;

    @TableField("created_by")
    private Integer createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("remark")
    private String remark;
}

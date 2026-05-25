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
@TableName("production_order_output")
public class ProductionOrderOutput {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("production_order_id")
    private Long productionOrderId;

    @TableField("order_no")
    private String orderNo;

    @TableField("product_id")
    private Integer productId;

    @TableField("product_name_snapshot")
    private String productNameSnapshot;

    @TableField("product_status")
    private String productStatus;

    @TableField("production_date")
    private LocalDate productionDate;

    @TableField("board_count")
    private Integer boardCount;

    @TableField("piece_count")
    private Integer pieceCount;

    @TableField("total_pieces")
    private Integer totalPieces;

    @TableField("pieces_per_pallet")
    private Integer piecesPerPallet;

    @TableField("weight_per_piece")
    private BigDecimal weightPerPiece;

    @TableField("total_weight")
    private BigDecimal totalWeight;

    @TableField("required_qr_count")
    private Integer requiredQrCount;

    @TableField("bound_qr_count")
    private Integer boundQrCount;

    @TableField("inbound_qr_count")
    private Integer inboundQrCount;

    @TableField("status")
    private String status;

    @TableField("created_by")
    private Integer createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("remark")
    private String remark;
}

package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("semi_prepare_pool_balance")
public class SemiPreparePoolBalance {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("product_id")
    private Integer productId;

    @TableField("product_name_snapshot")
    private String productNameSnapshot;

    @TableField("production_date")
    private LocalDate productionDate;

    @TableField("screen_mesh_id")
    private Integer screenMeshId;

    @TableField("assay_id")
    private Integer assayId;

    @TableField("in_pieces")
    private Integer inPieces;

    @TableField("consumed_pieces")
    private Integer consumedPieces;

    @TableField("remaining_pieces")
    private Integer remainingPieces;

    @TableField("pieces_per_pallet")
    private Integer piecesPerPallet;

    @TableField("weight_per_piece")
    private BigDecimal weightPerPiece;

    @TableField("remaining_weight")
    private BigDecimal remainingWeight;

    @TableField("status")
    private String status;

    @TableField("first_in_at")
    private LocalDateTime firstInAt;

    @TableField("last_in_at")
    private LocalDateTime lastInAt;

    @TableField("last_consumed_at")
    private LocalDateTime lastConsumedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

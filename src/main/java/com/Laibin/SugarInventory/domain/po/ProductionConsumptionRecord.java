package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("production_consumption_record")
public class ProductionConsumptionRecord {
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

    @TableField("consume_pieces")
    private Integer consumePieces;

    @TableField("balance_id")
    private Long balanceId;

    @TableField("source_batch_id")
    private String sourceBatchId;

    @TableField("source_task_id")
    private String sourceTaskId;

    @TableField("source_text")
    private String sourceText;

    @TableField("created_by")
    private Integer createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("remark")
    private String remark;
}

package com.Laibin.SugarInventory.production.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("production_order_label_batch")
public class ProductionOrderLabelBatch {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("production_order_id")
    private Long productionOrderId;

    @TableField("order_no")
    private String orderNo;

    @TableField("batch_no")
    private String batchNo;

    @TableField("product_id")
    private Integer productId;

    @TableField("product_name_snapshot")
    private String productNameSnapshot;

    @TableField("reserved_count")
    private Integer reservedCount;

    @TableField("used_count")
    private Integer usedCount;

    @TableField("recycled_count")
    private Integer recycledCount;

    @TableField("status")
    private String status;

    @TableField("printed_at")
    private LocalDateTime printedAt;

    @TableField("closed_at")
    private LocalDateTime closedAt;

    @TableField("created_by")
    private Integer createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("remark")
    private String remark;
}

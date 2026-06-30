package com.Laibin.SugarInventory.production.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("production_boiling_batch_usage")
public class ProductionBoilingBatchUsage {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("batch_id")
    private Long batchId;

    @TableField("batch_no")
    private String batchNo;

    @TableField("production_order_id")
    private Long productionOrderId;

    @TableField("order_no")
    private String orderNo;

    @TableField("usage_unit")
    private String usageUnit;

    @TableField("usage_quantity")
    private BigDecimal usageQuantity;

    @TableField("bucket_quantity")
    private BigDecimal bucketQuantity;

    @TableField("weight_kg")
    private BigDecimal weightKg;

    @TableField("status")
    private String status;

    @TableField("created_by")
    private Integer createdBy;

    @TableField("created_by_name")
    private String createdByName;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("remark")
    private String remark;
}

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
@TableName("production_boiling_batch")
public class ProductionBoilingBatch {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("batch_no")
    private String batchNo;

    @TableField("boiling_date")
    private LocalDate boilingDate;

    @TableField("team_name")
    private String teamName;

    @TableField("sugar_type")
    private String sugarType;

    @TableField("product_id")
    private Integer productId;

    @TableField("product_name_snapshot")
    private String productNameSnapshot;

    @TableField("pot_count")
    private BigDecimal potCount;

    @TableField("bucket_count")
    private BigDecimal bucketCount;

    @TableField("kg_per_bucket")
    private BigDecimal kgPerBucket;

    @TableField("total_weight_kg")
    private BigDecimal totalWeightKg;

    @TableField("status")
    private String status;

    @TableField("source_text")
    private String sourceText;

    @TableField("remark")
    private String remark;

    @TableField("created_by")
    private Integer createdBy;

    @TableField("created_by_name")
    private String createdByName;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("canceled_by")
    private Integer canceledBy;

    @TableField("canceled_at")
    private LocalDateTime canceledAt;
}

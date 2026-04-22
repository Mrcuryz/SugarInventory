package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("quality_standards")
public class QualityStandard extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("standard_code")
    private String standardCode;

    @TableField("product_type")
    private String productType; // '黄冰糖' 或 '白冰糖'

    @TableField("standard_name")
    private String standardName;

    @TableField("standard_level")
    private String standardLevel;

    @TableField("version")
    private Integer version;

    @TableField("status")
    private String status;

    // Legacy flat metric fields are kept as non-persistent properties so older
    // service code can compile during the refactor. Real data now lives in
    // quality_standard_item.
    @TableField(exist = false)
    private BigDecimal colorMin;

    @TableField(exist = false)
    private BigDecimal colorMax;

    @TableField(exist = false)
    private BigDecimal reducingSugarMin;

    @TableField(exist = false)
    private BigDecimal reducingSugarMax;

    @TableField(exist = false)
    private BigDecimal dryWeightMin;

    @TableField(exist = false)
    private BigDecimal dryWeightMax;

    @TableField(exist = false)
    private BigDecimal conductivityAshMin;

    @TableField(exist = false)
    private BigDecimal conductivityAshMax;

    @TableField(exist = false)
    private BigDecimal sucroseMin;

    @TableField(exist = false)
    private BigDecimal sucroseMax;

    @TableField(exist = false)
    private BigDecimal insolubleImpurityMax;

    @TableField(exist = false)
    private BigDecimal insolubleImpurityMin;

    @TableField(exist = false)
    private BigDecimal phMin;

    @TableField(exist = false)
    private BigDecimal phMax;

    @TableField("remark")
    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("created_by")
    private Integer createdBy;

    @TableField("updated_by")
    private Integer updatedBy;

    @TableField(exist = false)
    private List<QualityStandardItem> items;
}

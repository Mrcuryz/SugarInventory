package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("quality_standards")
public class QualityStandard extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("product_type")
    private String productType; // '黄冰糖' 或 '白冰糖'

    @TableField("standard_name")
    private String standardName;

    @TableField("color_min")
    private BigDecimal colorMin;

    @TableField("color_max")
    private BigDecimal colorMax;

    @TableField("reducing_sugar_min")
    private BigDecimal reducingSugarMin;

    @TableField("reducing_sugar_max")
    private BigDecimal reducingSugarMax;

    @TableField("dry_weight_min")
    private BigDecimal dryWeightMin;

    @TableField("dry_weight_max")
    private BigDecimal dryWeightMax;

    @TableField("conductivity_ash_min")
    private BigDecimal conductivityAshMin;

    @TableField("conductivity_ash_max")
    private BigDecimal conductivityAshMax;

    @TableField("sucrose_min")
    private BigDecimal sucroseMin;

    @TableField("sucrose_max")
    private BigDecimal sucroseMax;

    @TableField("insoluble_impurity_max")
    private BigDecimal insolubleImpurityMax;

    @TableField("insoluble_impurity_min")
    private BigDecimal insolubleImpurityMin;

    @TableField("ph_min")
    private BigDecimal phMin;

    @TableField("ph_max")
    private BigDecimal phMax;
}


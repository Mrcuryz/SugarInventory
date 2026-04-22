package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@Data
@TableName("assay")
public class Assay extends BaseEntity implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField(value = "product_id")
    private Integer productId;

    @TableField(value = "sample_date")
    private LocalDate sampleDate;

    @TableField(value = "color_value")
    private BigDecimal colorValue;

    @TableField(value = "reducing_sugar")
    private BigDecimal reducingSugar;

    @TableField(value = "dry_weight")
    private BigDecimal dryWeight;

    @TableField(value = "conductivity_ash")
    private BigDecimal conductivityAsh;

    @TableField(value = "sucrose")
    private BigDecimal sucrose;

    @TableField(value = "insoluble_impurity")
    private BigDecimal insolubleImpurity;

    @TableField(value = "ph_value")
    private BigDecimal phValue;

    @TableField(value = "tested_by")
    private Integer testedBy;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField("qualified_standards")
    private String qualifiedStandards;

    @TableField("applied_standard_id")
    private Integer appliedStandardId;

    @TableField("applied_standard_name")
    private String appliedStandardName;

    @TableField("applied_standard_version")
    private Integer appliedStandardVersion;

    @TableField("judge_result")
    private String judgeResult;

    @TableField("failed_metric_count")
    private Integer failedMetricCount;

    @TableField("failed_metrics_json")
    private String failedMetricsJson;

    @TableField("standard_snapshot_json")
    private String standardSnapshotJson;

    @TableField("judge_message")
    private String judgeMessage;

    @TableField(value = "is_qualified")
    private String isQualified;

    @TableField(value = "version")
    private Integer version;

    @Override
    public Integer getId() {
        return id;
    }
}

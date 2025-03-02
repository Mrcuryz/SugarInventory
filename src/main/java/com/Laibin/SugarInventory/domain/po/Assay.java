package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @TableField(value = "ph_value")
    private BigDecimal phValue;

    @TableField(value = "tested_by")
    private Integer testedBy;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "version")
    private Integer version;

    @Override
    public Integer getId() {
        return id;
    }
}

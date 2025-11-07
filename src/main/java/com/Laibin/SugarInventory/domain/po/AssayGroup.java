package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("assay_group")
public class AssayGroup extends BaseEntity implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField(value = "related_products")
    private String relatedProducts;

    @TableField(value = "standard_name")
    private String standardName;

    @TableField(value = "remark")
    private String remark;

    @TableField("created_by")
    private Integer createdBy;

    @TableField("updated_by")
    private Integer updatedBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

}

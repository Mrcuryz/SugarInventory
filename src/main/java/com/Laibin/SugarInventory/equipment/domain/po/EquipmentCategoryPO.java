package com.Laibin.SugarInventory.equipment.domain.po;

import com.Laibin.SugarInventory.domain.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("equipment_category")
public class EquipmentCategoryPO extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String categoryName;
    private String majorCode;
    private Integer defaultSequenceStart;
    private Integer defaultSequenceEnd;
    private Integer sortOrder;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

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
@TableName("equipment_unit")
public class EquipmentUnitPO extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String unitCode;
    private String unitName;
    private Integer sortOrder;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

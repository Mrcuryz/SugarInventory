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
@TableName("equipment_code_rule")
public class EquipmentCodeRulePO extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String companyCode;
    private Integer unitId;
    private Integer categoryId;
    private String sectionCode;
    private Integer sequenceStart;
    private Integer sequenceEnd;
    private Integer nextSequence;
    private Boolean enabled;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

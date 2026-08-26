package com.Laibin.SugarInventory.equipment.domain.po;

import com.Laibin.SugarInventory.domain.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("equipment_repair_record")
public class EquipmentRepairRecordPO extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer equipmentId;
    private LocalDate repairDate;
    private Integer repairTypeId;
    private String repairPerson;
    private String acceptancePerson;
    private String repairContent;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer createdBy;
    private Integer updatedBy;
}

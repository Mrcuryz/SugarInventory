package com.Laibin.SugarInventory.equipment.domain.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EquipmentRepairVO {
    private Integer id;
    private Integer equipmentId;
    private String equipmentCode;
    private String equipmentName;
    private Integer unitId;
    private String unitName;
    private Integer categoryId;
    private String categoryName;
    private LocalDate repairDate;
    private Integer repairTypeId;
    private String repairTypeName;
    private String repairPerson;
    private String acceptancePerson;
    private String repairContent;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.Laibin.SugarInventory.equipment.domain.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class EquipmentRepairQueryDTO {
    private String keyword;
    private Integer equipmentId;
    private Integer unitId;
    private Integer categoryId;
    private Integer repairTypeId;
    private String repairPerson;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer page = 1;
    private Integer size = 10;
}

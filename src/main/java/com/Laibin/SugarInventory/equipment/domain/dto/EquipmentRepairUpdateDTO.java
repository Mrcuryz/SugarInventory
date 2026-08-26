package com.Laibin.SugarInventory.equipment.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EquipmentRepairUpdateDTO {
    @NotNull
    private Integer version;
    @NotNull
    private Integer equipmentId;
    @NotNull
    private LocalDate repairDate;
    @NotNull
    private Integer repairTypeId;
    @Size(max = 100)
    private String repairPerson;
    @Size(max = 100)
    private String acceptancePerson;
    @NotBlank
    private String repairContent;
}

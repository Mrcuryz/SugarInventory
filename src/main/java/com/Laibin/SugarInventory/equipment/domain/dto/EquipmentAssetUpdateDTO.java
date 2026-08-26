package com.Laibin.SugarInventory.equipment.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EquipmentAssetUpdateDTO {
    @NotNull
    private Integer version;
    private Integer renovationTypeId;
    private Integer parentEquipmentId;
    private Integer manufacturerId;
    @NotBlank
    @Size(max = 100)
    private String equipmentName;
    @Size(max = 100)
    private String model;
    @DecimalMin("0")
    private BigDecimal ratedPowerKw;
    @DecimalMin("0")
    private BigDecimal ratedVoltageV;
    @DecimalMin("0")
    private BigDecimal ratedCurrentA;
    @DecimalMin("0")
    private BigDecimal ratedSpeedRpm;
    @DecimalMin("0")
    private BigDecimal price;
    @DecimalMin("0")
    private BigDecimal installationCost;
    @Size(max = 255)
    private String installationLocation;
    @Size(max = 100)
    private String factorySerialNo;
    private LocalDate productionDate;
    private LocalDate arrivalDate;
    private LocalDate commissioningDate;
    private String remark;
}

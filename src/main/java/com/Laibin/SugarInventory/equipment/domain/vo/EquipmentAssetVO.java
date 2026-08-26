package com.Laibin.SugarInventory.equipment.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EquipmentAssetVO {
    private Integer id;
    private String equipmentCode;
    private String equipmentSubNo;
    private Integer unitId;
    private String unitCode;
    private String unitName;
    private Integer categoryId;
    private String categoryName;
    private String majorCode;
    private Integer renovationTypeId;
    private String renovationTypeName;
    private Integer parentEquipmentId;
    private String parentEquipmentCode;
    private String parentEquipmentName;
    private Integer manufacturerId;
    private String manufacturerCode;
    private String manufacturerName;
    private String equipmentName;
    private String model;
    private BigDecimal ratedPowerKw;
    private BigDecimal ratedVoltageV;
    private BigDecimal ratedCurrentA;
    private BigDecimal ratedSpeedRpm;
    private BigDecimal price;
    private BigDecimal installationCost;
    private String installationLocation;
    private String factorySerialNo;
    private LocalDate productionDate;
    private LocalDate arrivalDate;
    private LocalDate commissioningDate;
    private String remark;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

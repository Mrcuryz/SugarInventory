package com.Laibin.SugarInventory.equipment.domain.po;

import com.Laibin.SugarInventory.domain.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("equipment_asset")
public class EquipmentAssetPO extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String equipmentCode;
    private String equipmentSubNo;
    private Integer unitId;
    private Integer categoryId;
    private Integer renovationTypeId;
    private Integer parentEquipmentId;
    private Integer manufacturerId;
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
    private Integer createdBy;
    private Integer updatedBy;
}

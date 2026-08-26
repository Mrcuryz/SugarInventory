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
@TableName("equipment_manufacturer")
public class EquipmentManufacturerPO extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String manufacturerCode;
    private String manufacturerName;
    private String address;
    private String contactPerson;
    private String phone;
    private String fax;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer createdBy;
    private Integer updatedBy;
}

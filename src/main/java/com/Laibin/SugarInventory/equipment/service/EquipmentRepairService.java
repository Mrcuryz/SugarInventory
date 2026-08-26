package com.Laibin.SugarInventory.equipment.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentRepairCreateDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentRepairQueryDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentRepairUpdateDTO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentRepairVO;

import java.util.List;

public interface EquipmentRepairService {
    PageResult<EquipmentRepairVO> page(EquipmentRepairQueryDTO query);
    EquipmentRepairVO detail(Integer id);
    List<EquipmentRepairVO> listByEquipment(Integer equipmentId);
    EquipmentRepairVO create(EquipmentRepairCreateDTO dto, Integer operatorId);
    EquipmentRepairVO update(Integer id, EquipmentRepairUpdateDTO dto, Integer operatorId);
    void delete(Integer id);
    byte[] export(EquipmentRepairQueryDTO query);
}

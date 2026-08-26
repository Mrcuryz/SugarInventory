package com.Laibin.SugarInventory.equipment.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentAssetCreateDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentAssetQueryDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentAssetUpdateDTO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentAssetDetailVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentAssetOptionVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentAssetVO;

import java.util.List;

public interface EquipmentAssetService {
    PageResult<EquipmentAssetVO> page(EquipmentAssetQueryDTO query);
    EquipmentAssetDetailVO detail(Integer id);
    List<EquipmentAssetOptionVO> options(String keyword);
    EquipmentAssetVO create(EquipmentAssetCreateDTO dto, Integer operatorId);
    EquipmentAssetVO update(Integer id, EquipmentAssetUpdateDTO dto, Integer operatorId);
    void delete(Integer id);
    byte[] export(EquipmentAssetQueryDTO query);
}

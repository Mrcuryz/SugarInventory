package com.Laibin.SugarInventory.equipment;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentCodeRulePO;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentAssetMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentCategoryMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentCodeRuleMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentManufacturerMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentRenovationTypeMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentRepairRecordMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentRepairTypeMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentUnitMapper;
import com.Laibin.SugarInventory.equipment.service.impl.EquipmentBasicDataServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EquipmentBasicDataServiceImplTest {
    @Test
    void enablingSecondRuleForSameUnitAndCategoryIsRejected() {
        EquipmentCodeRuleMapper codeRuleMapper = mock(EquipmentCodeRuleMapper.class);
        EquipmentBasicDataServiceImpl service = new EquipmentBasicDataServiceImpl(
                mock(EquipmentUnitMapper.class),
                mock(EquipmentCategoryMapper.class),
                mock(EquipmentManufacturerMapper.class),
                mock(EquipmentRenovationTypeMapper.class),
                mock(EquipmentRepairTypeMapper.class),
                codeRuleMapper,
                mock(EquipmentAssetMapper.class),
                mock(EquipmentRepairRecordMapper.class));
        EquipmentCodeRulePO rule = new EquipmentCodeRulePO();
        rule.setId(3); rule.setUnitId(1); rule.setCategoryId(2); rule.setEnabled(false);
        when(codeRuleMapper.selectById(3)).thenReturn(rule);
        when(codeRuleMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.setCodeRuleEnabled(3, true))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.EQUIPMENT_CODE_RULE_AMBIGUOUS.getCode());
        verify(codeRuleMapper, never()).updateById(any());
    }
}

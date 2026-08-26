package com.Laibin.SugarInventory.equipment;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentAssetCreateDTO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentAssetPO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentCategoryPO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentCodeRulePO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentUnitPO;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentAssetMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentCategoryMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentCodeRuleMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentManufacturerMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentRenovationTypeMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentRepairRecordMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentUnitMapper;
import com.Laibin.SugarInventory.equipment.service.EquipmentRepairService;
import com.Laibin.SugarInventory.equipment.service.impl.EquipmentAssetServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EquipmentAssetServiceImplTest {
    private EquipmentAssetMapper assetMapper;
    private EquipmentUnitMapper unitMapper;
    private EquipmentCategoryMapper categoryMapper;
    private EquipmentCodeRuleMapper codeRuleMapper;
    private EquipmentRepairRecordMapper repairRecordMapper;
    private EquipmentAssetServiceImpl service;
    private EquipmentUnitPO unit;
    private EquipmentCategoryPO category;

    @BeforeEach
    void setUp() {
        assetMapper = mock(EquipmentAssetMapper.class);
        unitMapper = mock(EquipmentUnitMapper.class);
        categoryMapper = mock(EquipmentCategoryMapper.class);
        codeRuleMapper = mock(EquipmentCodeRuleMapper.class);
        repairRecordMapper = mock(EquipmentRepairRecordMapper.class);
        service = new EquipmentAssetServiceImpl(
                assetMapper,
                unitMapper,
                categoryMapper,
                mock(EquipmentManufacturerMapper.class),
                mock(EquipmentRenovationTypeMapper.class),
                codeRuleMapper,
                repairRecordMapper,
                mock(EquipmentRepairService.class));

        unit = new EquipmentUnitPO();
        unit.setId(1); unit.setUnitCode("YZ"); unit.setUnitName("压榨车间"); unit.setEnabled(true);
        category = new EquipmentCategoryPO();
        category.setId(2); category.setMajorCode("D"); category.setCategoryName("电机"); category.setEnabled(true);
        when(unitMapper.selectById(1)).thenReturn(unit);
        when(categoryMapper.selectById(2)).thenReturn(category);
        when(unitMapper.selectBatchIds(anyCollection())).thenReturn(List.of(unit));
        when(categoryMapper.selectBatchIds(anyCollection())).thenReturn(List.of(category));
    }

    @Test
    void createGeneratesLegacyCompatibleCodeAndAdvancesLockedRule() {
        EquipmentCodeRulePO rule = new EquipmentCodeRulePO();
        rule.setId(8); rule.setCompanyCode("LBYX"); rule.setUnitId(1); rule.setCategoryId(2);
        rule.setSectionCode("1"); rule.setSequenceStart(0); rule.setSequenceEnd(3999); rule.setNextSequence(1); rule.setEnabled(true);
        when(codeRuleMapper.selectEnabledForUpdate(1, 2)).thenReturn(List.of(rule));
        when(codeRuleMapper.advance(8, 1)).thenReturn(1);
        when(assetMapper.insert(any(EquipmentAssetPO.class))).thenAnswer(invocation -> {
            EquipmentAssetPO po = invocation.getArgument(0);
            po.setId(101);
            return 1;
        });

        var result = service.create(createDTO(), 7);

        assertThat(result.getEquipmentCode()).isEqualTo("LBYX-D-YZ-10001");
        assertThat(result.getEquipmentSubNo()).isEqualTo("10001");
        ArgumentCaptor<EquipmentAssetPO> captor = ArgumentCaptor.forClass(EquipmentAssetPO.class);
        verify(assetMapper).insert(captor.capture());
        assertThat(captor.getValue().getEquipmentName()).isEqualTo("1号电机");
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(7);
        verify(codeRuleMapper).advance(8, 1);
    }

    @Test
    void createRejectsExhaustedSequenceRange() {
        EquipmentCodeRulePO rule = new EquipmentCodeRulePO();
        rule.setSequenceEnd(3999); rule.setNextSequence(4000);
        when(codeRuleMapper.selectEnabledForUpdate(1, 2)).thenReturn(List.of(rule));

        assertThatThrownBy(() -> service.create(createDTO(), 7))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.EQUIPMENT_CODE_RANGE_EXHAUSTED.getCode());
    }

    @Test
    void createRejectsInvalidDateOrderBeforeAllocatingCode() {
        EquipmentAssetCreateDTO dto = createDTO();
        dto.setProductionDate(LocalDate.of(2020, 2, 1));
        dto.setArrivalDate(LocalDate.of(2020, 1, 1));

        assertThatThrownBy(() -> service.create(dto, 7))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("生产日期");
    }

    @Test
    void createRejectsWhenNoEnabledRuleMatchesUnitAndCategory() {
        when(codeRuleMapper.selectEnabledForUpdate(1, 2)).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(createDTO(), 7))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.EQUIPMENT_CODE_RULE_NOT_FOUND.getCode());
    }

    @Test
    void createRejectsAmbiguousEnabledRulesInsteadOfGuessingASection() {
        EquipmentCodeRulePO first = new EquipmentCodeRulePO(); first.setSectionCode("1");
        EquipmentCodeRulePO second = new EquipmentCodeRulePO(); second.setSectionCode("2");
        when(codeRuleMapper.selectEnabledForUpdate(1, 2)).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.create(createDTO(), 7))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.EQUIPMENT_CODE_RULE_AMBIGUOUS.getCode());
    }

    @Test
    void deleteRejectsEquipmentWithAuxiliaryChildren() {
        EquipmentAssetPO asset = new EquipmentAssetPO(); asset.setId(10);
        when(assetMapper.selectById(10)).thenReturn(asset);
        when(assetMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(10))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.EQUIPMENT_DATA_IN_USE.getCode());
    }

    private EquipmentAssetCreateDTO createDTO() {
        EquipmentAssetCreateDTO dto = new EquipmentAssetCreateDTO();
        dto.setUnitId(1); dto.setCategoryId(2); dto.setEquipmentName("1号电机");
        return dto;
    }
}

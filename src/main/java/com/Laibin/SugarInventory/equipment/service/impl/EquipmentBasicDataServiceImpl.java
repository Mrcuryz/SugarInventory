package com.Laibin.SugarInventory.equipment.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.equipment.domain.assembler.EquipmentAssembler;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentBasicDataDTOs.EquipmentBasicQueryDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentBasicDataDTOs.EquipmentCategorySaveDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentBasicDataDTOs.EquipmentCodeRuleSaveDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentBasicDataDTOs.EquipmentManufacturerSaveDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentBasicDataDTOs.EquipmentTypeSaveDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentBasicDataDTOs.EquipmentUnitSaveDTO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentAssetPO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentCategoryPO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentCodeRulePO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentManufacturerPO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentRenovationTypePO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentRepairRecordPO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentRepairTypePO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentUnitPO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentCategoryVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentCodeRuleVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentManufacturerVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentOptionVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentTypeVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentUnitVO;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentAssetMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentCategoryMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentCodeRuleMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentManufacturerMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentRenovationTypeMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentRepairRecordMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentRepairTypeMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentUnitMapper;
import com.Laibin.SugarInventory.equipment.service.EquipmentBasicDataService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EquipmentBasicDataServiceImpl implements EquipmentBasicDataService {
    private final EquipmentUnitMapper unitMapper;
    private final EquipmentCategoryMapper categoryMapper;
    private final EquipmentManufacturerMapper manufacturerMapper;
    private final EquipmentRenovationTypeMapper renovationTypeMapper;
    private final EquipmentRepairTypeMapper repairTypeMapper;
    private final EquipmentCodeRuleMapper codeRuleMapper;
    private final EquipmentAssetMapper assetMapper;
    private final EquipmentRepairRecordMapper repairRecordMapper;

    @Override
    public PageResult<EquipmentUnitVO> pageUnits(EquipmentBasicQueryDTO query) {
        EquipmentBasicQueryDTO source = source(query);
        String keyword = trim(source.getKeyword());
        Page<EquipmentUnitPO> page = unitMapper.selectPage(paging(source), new LambdaQueryWrapper<EquipmentUnitPO>()
                .and(StringUtils.hasText(keyword), value -> value.like(EquipmentUnitPO::getUnitCode, keyword)
                        .or().like(EquipmentUnitPO::getUnitName, keyword))
                .eq(source.getEnabled() != null, EquipmentUnitPO::getEnabled, source.getEnabled())
                .orderByAsc(EquipmentUnitPO::getSortOrder).orderByAsc(EquipmentUnitPO::getId));
        return result(page, EquipmentAssembler::toUnitVO);
    }

    @Override
    public List<EquipmentOptionVO> unitOptions() {
        return unitMapper.selectList(new LambdaQueryWrapper<EquipmentUnitPO>().eq(EquipmentUnitPO::getEnabled, true)
                        .orderByAsc(EquipmentUnitPO::getSortOrder).orderByAsc(EquipmentUnitPO::getId))
                .stream().map(item -> new EquipmentOptionVO(item.getId(), item.getUnitCode(), item.getUnitName(), item.getEnabled())).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EquipmentUnitVO saveUnit(Integer id, EquipmentUnitSaveDTO dto) {
        EquipmentUnitPO po = id == null ? new EquipmentUnitPO() : require(unitMapper.selectById(id));
        String code = code(dto.getUnitCode()); String name = dto.getUnitName().trim();
        if (unitMapper.selectCount(new LambdaQueryWrapper<EquipmentUnitPO>()
                .ne(id != null, EquipmentUnitPO::getId, id)
                .and(value -> value.eq(EquipmentUnitPO::getUnitCode, code).or().eq(EquipmentUnitPO::getUnitName, name))) > 0) duplicate();
        po.setUnitCode(code); po.setUnitName(name); po.setSortOrder(order(dto.getSortOrder())); po.setEnabled(enabled(dto.getEnabled()));
        save(id, po, unitMapper::insert, unitMapper::updateById);
        return EquipmentAssembler.toUnitVO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setUnitEnabled(Integer id, boolean enabled) {
        EquipmentUnitPO po = require(unitMapper.selectById(id)); po.setEnabled(enabled); po.setUpdatedAt(LocalDateTime.now()); unitMapper.updateById(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUnit(Integer id) {
        require(unitMapper.selectById(id));
        if (assetMapper.selectCount(new LambdaQueryWrapper<EquipmentAssetPO>().eq(EquipmentAssetPO::getUnitId, id)) > 0
                || codeRuleMapper.selectCount(new LambdaQueryWrapper<EquipmentCodeRulePO>().eq(EquipmentCodeRulePO::getUnitId, id)) > 0) inUse();
        delete(() -> unitMapper.deleteById(id));
    }

    @Override
    public PageResult<EquipmentCategoryVO> pageCategories(EquipmentBasicQueryDTO query) {
        EquipmentBasicQueryDTO source = source(query); String keyword = trim(source.getKeyword());
        Page<EquipmentCategoryPO> page = categoryMapper.selectPage(paging(source), new LambdaQueryWrapper<EquipmentCategoryPO>()
                .and(StringUtils.hasText(keyword), value -> value.like(EquipmentCategoryPO::getCategoryName, keyword)
                        .or().like(EquipmentCategoryPO::getMajorCode, keyword))
                .eq(source.getEnabled() != null, EquipmentCategoryPO::getEnabled, source.getEnabled())
                .orderByAsc(EquipmentCategoryPO::getSortOrder).orderByAsc(EquipmentCategoryPO::getId));
        return result(page, EquipmentAssembler::toCategoryVO);
    }

    @Override
    public List<EquipmentOptionVO> categoryOptions() {
        return categoryMapper.selectList(new LambdaQueryWrapper<EquipmentCategoryPO>().eq(EquipmentCategoryPO::getEnabled, true)
                        .orderByAsc(EquipmentCategoryPO::getSortOrder).orderByAsc(EquipmentCategoryPO::getId))
                .stream().map(item -> new EquipmentOptionVO(item.getId(), item.getMajorCode(), item.getCategoryName(), item.getEnabled())).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EquipmentCategoryVO saveCategory(Integer id, EquipmentCategorySaveDTO dto) {
        EquipmentCategoryPO po = id == null ? new EquipmentCategoryPO() : require(categoryMapper.selectById(id));
        String name = dto.getCategoryName().trim();
        if (categoryMapper.selectCount(new LambdaQueryWrapper<EquipmentCategoryPO>().ne(id != null, EquipmentCategoryPO::getId, id)
                .eq(EquipmentCategoryPO::getCategoryName, name)) > 0) duplicate();
        po.setCategoryName(name); po.setMajorCode(code(dto.getMajorCode()));
        po.setDefaultSequenceStart(dto.getDefaultSequenceStart()); po.setDefaultSequenceEnd(dto.getDefaultSequenceEnd());
        po.setSortOrder(order(dto.getSortOrder())); po.setEnabled(enabled(dto.getEnabled()));
        save(id, po, categoryMapper::insert, categoryMapper::updateById);
        return EquipmentAssembler.toCategoryVO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setCategoryEnabled(Integer id, boolean enabled) {
        EquipmentCategoryPO po = require(categoryMapper.selectById(id)); po.setEnabled(enabled); po.setUpdatedAt(LocalDateTime.now()); categoryMapper.updateById(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Integer id) {
        require(categoryMapper.selectById(id));
        if (assetMapper.selectCount(new LambdaQueryWrapper<EquipmentAssetPO>().eq(EquipmentAssetPO::getCategoryId, id)) > 0
                || codeRuleMapper.selectCount(new LambdaQueryWrapper<EquipmentCodeRulePO>().eq(EquipmentCodeRulePO::getCategoryId, id)) > 0) inUse();
        delete(() -> categoryMapper.deleteById(id));
    }

    @Override
    public PageResult<EquipmentManufacturerVO> pageManufacturers(EquipmentBasicQueryDTO query) {
        EquipmentBasicQueryDTO source = source(query); String keyword = trim(source.getKeyword());
        Page<EquipmentManufacturerPO> page = manufacturerMapper.selectPage(paging(source), new LambdaQueryWrapper<EquipmentManufacturerPO>()
                .and(StringUtils.hasText(keyword), value -> value.like(EquipmentManufacturerPO::getManufacturerCode, keyword)
                        .or().like(EquipmentManufacturerPO::getManufacturerName, keyword)
                        .or().like(EquipmentManufacturerPO::getContactPerson, keyword))
                .orderByAsc(EquipmentManufacturerPO::getManufacturerCode));
        return result(page, EquipmentAssembler::toManufacturerVO);
    }

    @Override
    public List<EquipmentOptionVO> manufacturerOptions() {
        return manufacturerMapper.selectList(new LambdaQueryWrapper<EquipmentManufacturerPO>().orderByAsc(EquipmentManufacturerPO::getManufacturerCode))
                .stream().map(item -> new EquipmentOptionVO(item.getId(), item.getManufacturerCode(), item.getManufacturerName(), true)).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EquipmentManufacturerVO saveManufacturer(Integer id, EquipmentManufacturerSaveDTO dto, Integer operatorId) {
        EquipmentManufacturerPO po = id == null ? new EquipmentManufacturerPO() : require(manufacturerMapper.selectById(id));
        String manufacturerCode = code(dto.getManufacturerCode());
        if (manufacturerMapper.selectCount(new LambdaQueryWrapper<EquipmentManufacturerPO>()
                .ne(id != null, EquipmentManufacturerPO::getId, id)
                .eq(EquipmentManufacturerPO::getManufacturerCode, manufacturerCode)) > 0) duplicate();
        po.setManufacturerCode(manufacturerCode); po.setManufacturerName(dto.getManufacturerName().trim()); po.setAddress(trim(dto.getAddress()));
        po.setContactPerson(trim(dto.getContactPerson())); po.setPhone(trim(dto.getPhone())); po.setFax(trim(dto.getFax())); po.setRemark(trim(dto.getRemark()));
        if (id == null) { po.setCreatedBy(operatorId); po.setCreatedAt(LocalDateTime.now()); }
        po.setUpdatedBy(operatorId); po.setUpdatedAt(LocalDateTime.now());
        save(id, po, manufacturerMapper::insert, manufacturerMapper::updateById);
        return EquipmentAssembler.toManufacturerVO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteManufacturer(Integer id) {
        require(manufacturerMapper.selectById(id));
        if (assetMapper.selectCount(new LambdaQueryWrapper<EquipmentAssetPO>().eq(EquipmentAssetPO::getManufacturerId, id)) > 0) inUse();
        delete(() -> manufacturerMapper.deleteById(id));
    }

    @Override
    public PageResult<EquipmentTypeVO> pageRenovationTypes(EquipmentBasicQueryDTO query) {
        EquipmentBasicQueryDTO source = source(query); String keyword = trim(source.getKeyword());
        Page<EquipmentRenovationTypePO> page = renovationTypeMapper.selectPage(paging(source), new LambdaQueryWrapper<EquipmentRenovationTypePO>()
                .like(StringUtils.hasText(keyword), EquipmentRenovationTypePO::getTypeName, keyword)
                .eq(source.getEnabled() != null, EquipmentRenovationTypePO::getEnabled, source.getEnabled())
                .orderByAsc(EquipmentRenovationTypePO::getSortOrder).orderByAsc(EquipmentRenovationTypePO::getId));
        return result(page, EquipmentAssembler::toRenovationTypeVO);
    }

    @Override
    public List<EquipmentOptionVO> renovationTypeOptions() {
        return renovationTypeMapper.selectList(new LambdaQueryWrapper<EquipmentRenovationTypePO>().eq(EquipmentRenovationTypePO::getEnabled, true)
                        .orderByAsc(EquipmentRenovationTypePO::getSortOrder).orderByAsc(EquipmentRenovationTypePO::getId))
                .stream().map(item -> new EquipmentOptionVO(item.getId(), null, item.getTypeName(), item.getEnabled())).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EquipmentTypeVO saveRenovationType(Integer id, EquipmentTypeSaveDTO dto) {
        EquipmentRenovationTypePO po = id == null ? new EquipmentRenovationTypePO() : require(renovationTypeMapper.selectById(id));
        String name = dto.getTypeName().trim();
        if (renovationTypeMapper.selectCount(new LambdaQueryWrapper<EquipmentRenovationTypePO>().ne(id != null, EquipmentRenovationTypePO::getId, id)
                .eq(EquipmentRenovationTypePO::getTypeName, name)) > 0) duplicate();
        po.setTypeName(name); po.setSortOrder(order(dto.getSortOrder())); po.setEnabled(enabled(dto.getEnabled()));
        save(id, po, renovationTypeMapper::insert, renovationTypeMapper::updateById);
        return EquipmentAssembler.toRenovationTypeVO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setRenovationTypeEnabled(Integer id, boolean enabled) {
        EquipmentRenovationTypePO po = require(renovationTypeMapper.selectById(id)); po.setEnabled(enabled); po.setUpdatedAt(LocalDateTime.now()); renovationTypeMapper.updateById(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRenovationType(Integer id) {
        require(renovationTypeMapper.selectById(id));
        if (assetMapper.selectCount(new LambdaQueryWrapper<EquipmentAssetPO>().eq(EquipmentAssetPO::getRenovationTypeId, id)) > 0) inUse();
        delete(() -> renovationTypeMapper.deleteById(id));
    }

    @Override
    public PageResult<EquipmentTypeVO> pageRepairTypes(EquipmentBasicQueryDTO query) {
        EquipmentBasicQueryDTO source = source(query); String keyword = trim(source.getKeyword());
        Page<EquipmentRepairTypePO> page = repairTypeMapper.selectPage(paging(source), new LambdaQueryWrapper<EquipmentRepairTypePO>()
                .like(StringUtils.hasText(keyword), EquipmentRepairTypePO::getTypeName, keyword)
                .eq(source.getEnabled() != null, EquipmentRepairTypePO::getEnabled, source.getEnabled())
                .orderByAsc(EquipmentRepairTypePO::getSortOrder).orderByAsc(EquipmentRepairTypePO::getId));
        return result(page, EquipmentAssembler::toRepairTypeVO);
    }

    @Override
    public List<EquipmentOptionVO> repairTypeOptions() {
        return repairTypeMapper.selectList(new LambdaQueryWrapper<EquipmentRepairTypePO>().eq(EquipmentRepairTypePO::getEnabled, true)
                        .orderByAsc(EquipmentRepairTypePO::getSortOrder).orderByAsc(EquipmentRepairTypePO::getId))
                .stream().map(item -> new EquipmentOptionVO(item.getId(), null, item.getTypeName(), item.getEnabled())).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EquipmentTypeVO saveRepairType(Integer id, EquipmentTypeSaveDTO dto) {
        EquipmentRepairTypePO po = id == null ? new EquipmentRepairTypePO() : require(repairTypeMapper.selectById(id));
        String name = dto.getTypeName().trim();
        if (repairTypeMapper.selectCount(new LambdaQueryWrapper<EquipmentRepairTypePO>().ne(id != null, EquipmentRepairTypePO::getId, id)
                .eq(EquipmentRepairTypePO::getTypeName, name)) > 0) duplicate();
        po.setTypeName(name); po.setSortOrder(order(dto.getSortOrder())); po.setEnabled(enabled(dto.getEnabled()));
        save(id, po, repairTypeMapper::insert, repairTypeMapper::updateById);
        return EquipmentAssembler.toRepairTypeVO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setRepairTypeEnabled(Integer id, boolean enabled) {
        EquipmentRepairTypePO po = require(repairTypeMapper.selectById(id)); po.setEnabled(enabled); po.setUpdatedAt(LocalDateTime.now()); repairTypeMapper.updateById(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRepairType(Integer id) {
        require(repairTypeMapper.selectById(id));
        if (repairRecordMapper.selectCount(new LambdaQueryWrapper<EquipmentRepairRecordPO>().eq(EquipmentRepairRecordPO::getRepairTypeId, id)) > 0) inUse();
        delete(() -> repairTypeMapper.deleteById(id));
    }

    @Override
    public PageResult<EquipmentCodeRuleVO> pageCodeRules(EquipmentBasicQueryDTO query) {
        EquipmentBasicQueryDTO source = source(query); String keyword = trim(source.getKeyword());
        Page<EquipmentCodeRulePO> page = codeRuleMapper.selectPage(paging(source), new LambdaQueryWrapper<EquipmentCodeRulePO>()
                .and(StringUtils.hasText(keyword), value -> value.like(EquipmentCodeRulePO::getCompanyCode, keyword)
                        .or().like(EquipmentCodeRulePO::getSectionCode, keyword))
                .eq(source.getEnabled() != null, EquipmentCodeRulePO::getEnabled, source.getEnabled())
                .orderByAsc(EquipmentCodeRulePO::getUnitId).orderByAsc(EquipmentCodeRulePO::getCategoryId)
                .orderByAsc(EquipmentCodeRulePO::getSectionCode));
        Map<Integer, EquipmentUnitPO> units = map(unitMapper.selectList(null));
        Map<Integer, EquipmentCategoryPO> categories = map(categoryMapper.selectList(null));
        return new PageResult<>(page.getTotal(), page.getRecords().stream()
                .map(item -> EquipmentAssembler.toCodeRuleVO(item, units, categories)).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EquipmentCodeRuleVO saveCodeRule(Integer id, EquipmentCodeRuleSaveDTO dto) {
        EquipmentCodeRulePO po = id == null ? new EquipmentCodeRulePO() : require(codeRuleMapper.selectById(id));
        EquipmentUnitPO unit = require(unitMapper.selectById(dto.getUnitId()));
        EquipmentCategoryPO category = require(categoryMapper.selectById(dto.getCategoryId()));
        if (!Boolean.TRUE.equals(unit.getEnabled()) || !Boolean.TRUE.equals(category.getEnabled())) {
            throw new BusinessException(ErrorCode.EQUIPMENT_REFERENCE_INVALID);
        }
        String section = code(dto.getSectionCode());
        if (codeRuleMapper.selectCount(new LambdaQueryWrapper<EquipmentCodeRulePO>().ne(id != null, EquipmentCodeRulePO::getId, id)
                .eq(EquipmentCodeRulePO::getUnitId, dto.getUnitId()).eq(EquipmentCodeRulePO::getCategoryId, dto.getCategoryId())
                .eq(EquipmentCodeRulePO::getSectionCode, section)) > 0) duplicate();
        if (id != null && dto.getNextSequence() < po.getNextSequence()) {
            throw new BusinessException(400, "编号游标不能回退，以免产生重复设备编号");
        }
        boolean ruleEnabled = enabled(dto.getEnabled());
        if (ruleEnabled) ensureNoOtherEnabledCodeRule(id, dto.getUnitId(), dto.getCategoryId());
        po.setCompanyCode(code(dto.getCompanyCode())); po.setUnitId(dto.getUnitId()); po.setCategoryId(dto.getCategoryId());
        po.setSectionCode(section); po.setSequenceStart(dto.getSequenceStart()); po.setSequenceEnd(dto.getSequenceEnd());
        po.setNextSequence(dto.getNextSequence()); po.setEnabled(ruleEnabled);
        if (id == null) po.setVersion(0);
        save(id, po, codeRuleMapper::insert, codeRuleMapper::updateById);
        return EquipmentAssembler.toCodeRuleVO(po, Map.of(unit.getId(), unit), Map.of(category.getId(), category));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setCodeRuleEnabled(Integer id, boolean enabled) {
        EquipmentCodeRulePO po = require(codeRuleMapper.selectById(id));
        if (enabled) ensureNoOtherEnabledCodeRule(id, po.getUnitId(), po.getCategoryId());
        po.setEnabled(enabled); po.setUpdatedAt(LocalDateTime.now()); codeRuleMapper.updateById(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCodeRule(Integer id) {
        EquipmentCodeRulePO po = require(codeRuleMapper.selectById(id));
        if (po.getNextSequence() > po.getSequenceStart()) inUse();
        delete(() -> codeRuleMapper.deleteById(id));
    }

    private void ensureNoOtherEnabledCodeRule(Integer id, Integer unitId, Integer categoryId) {
        if (codeRuleMapper.selectCount(new LambdaQueryWrapper<EquipmentCodeRulePO>()
                .ne(id != null, EquipmentCodeRulePO::getId, id)
                .eq(EquipmentCodeRulePO::getUnitId, unitId)
                .eq(EquipmentCodeRulePO::getCategoryId, categoryId)
                .eq(EquipmentCodeRulePO::getEnabled, true)) > 0) {
            throw new BusinessException(ErrorCode.EQUIPMENT_CODE_RULE_AMBIGUOUS);
        }
    }

    private EquipmentBasicQueryDTO source(EquipmentBasicQueryDTO query) { return query == null ? new EquipmentBasicQueryDTO() : query; }
    private <T> Page<T> paging(EquipmentBasicQueryDTO query) { return new Page<>(page(query.getPage()), size(query.getSize())); }
    private int page(Integer value) { return value == null || value < 1 ? 1 : value; }
    private int size(Integer value) { return value == null ? 10 : Math.min(100, Math.max(1, value)); }
    private int order(Integer value) { return value == null ? 0 : value; }
    private boolean enabled(Boolean value) { return value == null || value; }
    private String trim(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private String code(String value) { return value.trim().toUpperCase(Locale.ROOT); }

    private <T, V> PageResult<V> result(Page<T> page, Function<T, V> converter) {
        return new PageResult<>(page.getTotal(), page.getRecords().stream().map(converter).toList());
    }

    private <T extends com.Laibin.SugarInventory.domain.po.BaseEntity> T require(T value) {
        if (value == null) throw new BusinessException(ErrorCode.EQUIPMENT_BASIC_DATA_NOT_FOUND);
        return value;
    }

    private <T extends com.Laibin.SugarInventory.domain.po.BaseEntity> Map<Integer, T> map(List<T> rows) {
        if (rows == null || rows.isEmpty()) return Collections.emptyMap();
        return rows.stream().collect(Collectors.toMap(T::getId, Function.identity()));
    }

    private <T extends com.Laibin.SugarInventory.domain.po.BaseEntity> void save(
            Integer id, T po, Function<T, Integer> insert, Function<T, Integer> update) {
        LocalDateTime now = LocalDateTime.now();
        try {
            if (id == null) {
                setCreatedAt(po, now);
                insert.apply(po);
            } else {
                setUpdatedAt(po, now);
                update.apply(po);
            }
        } catch (DuplicateKeyException e) {
            duplicate();
        }
    }

    private void setCreatedAt(Object po, LocalDateTime now) {
        if (po instanceof EquipmentUnitPO item) { item.setCreatedAt(now); item.setUpdatedAt(now); }
        else if (po instanceof EquipmentCategoryPO item) { item.setCreatedAt(now); item.setUpdatedAt(now); }
        else if (po instanceof EquipmentManufacturerPO item) { if (item.getCreatedAt() == null) item.setCreatedAt(now); item.setUpdatedAt(now); }
        else if (po instanceof EquipmentRenovationTypePO item) { item.setCreatedAt(now); item.setUpdatedAt(now); }
        else if (po instanceof EquipmentRepairTypePO item) { item.setCreatedAt(now); item.setUpdatedAt(now); }
        else if (po instanceof EquipmentCodeRulePO item) { item.setCreatedAt(now); item.setUpdatedAt(now); }
    }

    private void setUpdatedAt(Object po, LocalDateTime now) {
        if (po instanceof EquipmentUnitPO item) item.setUpdatedAt(now);
        else if (po instanceof EquipmentCategoryPO item) item.setUpdatedAt(now);
        else if (po instanceof EquipmentManufacturerPO item) item.setUpdatedAt(now);
        else if (po instanceof EquipmentRenovationTypePO item) item.setUpdatedAt(now);
        else if (po instanceof EquipmentRepairTypePO item) item.setUpdatedAt(now);
        else if (po instanceof EquipmentCodeRulePO item) item.setUpdatedAt(now);
    }

    private void delete(Runnable action) {
        try { action.run(); }
        catch (DataIntegrityViolationException e) { inUse(); }
    }

    private void duplicate() { throw new BusinessException(ErrorCode.EQUIPMENT_DUPLICATE_DATA); }
    private void inUse() { throw new BusinessException(ErrorCode.EQUIPMENT_DATA_IN_USE); }
}

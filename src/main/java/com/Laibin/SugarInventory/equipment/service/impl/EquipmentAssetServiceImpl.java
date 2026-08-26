package com.Laibin.SugarInventory.equipment.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.BaseEntity;
import com.Laibin.SugarInventory.equipment.domain.assembler.EquipmentAssembler;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentAssetCreateDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentAssetQueryDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentAssetUpdateDTO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentAssetPO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentCategoryPO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentCodeRulePO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentManufacturerPO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentRenovationTypePO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentUnitPO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentAssetDetailVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentAssetOptionVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentAssetVO;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentAssetMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentCategoryMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentCodeRuleMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentManufacturerMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentRenovationTypeMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentRepairRecordMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentUnitMapper;
import com.Laibin.SugarInventory.equipment.service.EquipmentAssetService;
import com.Laibin.SugarInventory.equipment.service.EquipmentRepairService;
import com.Laibin.SugarInventory.equipment.service.EquipmentWorkbookExporter;
import com.Laibin.SugarInventory.service.LoggableService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EquipmentAssetServiceImpl implements EquipmentAssetService, LoggableService<EquipmentAssetPO> {
    private final EquipmentAssetMapper assetMapper;
    private final EquipmentUnitMapper unitMapper;
    private final EquipmentCategoryMapper categoryMapper;
    private final EquipmentManufacturerMapper manufacturerMapper;
    private final EquipmentRenovationTypeMapper renovationTypeMapper;
    private final EquipmentCodeRuleMapper codeRuleMapper;
    private final EquipmentRepairRecordMapper repairRecordMapper;
    private final EquipmentRepairService repairService;

    @Override
    public EquipmentAssetPO findById(Integer id) {
        return assetMapper.selectById(id);
    }

    @Override
    public String getTableName() {
        return "设备台账";
    }

    @Override
    public PageResult<EquipmentAssetVO> page(EquipmentAssetQueryDTO query) {
        EquipmentAssetQueryDTO source = query == null ? new EquipmentAssetQueryDTO() : query;
        Page<EquipmentAssetPO> result = assetMapper.selectPage(
                new Page<>(page(source.getPage()), size(source.getSize())), wrapper(source));
        return new PageResult<>(result.getTotal(), assemble(result.getRecords()));
    }

    @Override
    public EquipmentAssetDetailVO detail(Integer id) {
        EquipmentAssetPO asset = requireAsset(id);
        List<EquipmentAssetPO> auxiliary = assetMapper.selectList(new LambdaQueryWrapper<EquipmentAssetPO>()
                .eq(EquipmentAssetPO::getParentEquipmentId, id)
                .orderByAsc(EquipmentAssetPO::getEquipmentCode));
        EquipmentAssetDetailVO detail = new EquipmentAssetDetailVO();
        detail.setAsset(assemble(List.of(asset)).get(0));
        detail.setAuxiliaryEquipment(assemble(auxiliary));
        detail.setRepairRecords(repairService.listByEquipment(id));
        return detail;
    }

    @Override
    public List<EquipmentAssetOptionVO> options(String keyword) {
        String text = trim(keyword);
        return assetMapper.selectList(new LambdaQueryWrapper<EquipmentAssetPO>()
                        .and(StringUtils.hasText(text), value -> value
                                .like(EquipmentAssetPO::getEquipmentCode, text)
                                .or().like(EquipmentAssetPO::getEquipmentName, text))
                        .orderByAsc(EquipmentAssetPO::getEquipmentCode)
                        .last("LIMIT 300"))
                .stream()
                .map(item -> new EquipmentAssetOptionVO(item.getId(), item.getEquipmentCode(), item.getEquipmentName()))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EquipmentAssetVO create(EquipmentAssetCreateDTO dto, Integer operatorId) {
        EquipmentUnitPO unit = requireEnabledUnit(dto.getUnitId());
        EquipmentCategoryPO category = requireEnabledCategory(dto.getCategoryId());
        requireManufacturer(dto.getManufacturerId());
        requireRenovationType(dto.getRenovationTypeId());
        requireParent(dto.getParentEquipmentId(), null);
        validateDates(dto.getProductionDate(), dto.getArrivalDate(), dto.getCommissioningDate());

        List<EquipmentCodeRulePO> rules = codeRuleMapper.selectEnabledForUpdate(dto.getUnitId(), dto.getCategoryId());
        if (rules.isEmpty()) throw new BusinessException(ErrorCode.EQUIPMENT_CODE_RULE_NOT_FOUND);
        if (rules.size() > 1) throw new BusinessException(ErrorCode.EQUIPMENT_CODE_RULE_AMBIGUOUS);
        EquipmentCodeRulePO rule = rules.get(0);
        String sectionCode = normalizeCode(rule.getSectionCode());
        int sequence = rule.getNextSequence();
        if (sequence > rule.getSequenceEnd()) throw new BusinessException(ErrorCode.EQUIPMENT_CODE_RANGE_EXHAUSTED);
        String subNo = sectionCode + String.format(Locale.ROOT, "%04d", sequence);
        String equipmentCode = normalizeCode(rule.getCompanyCode()) + "-" + normalizeCode(category.getMajorCode())
                + "-" + normalizeCode(unit.getUnitCode()) + "-" + subNo;

        EquipmentAssetPO po = new EquipmentAssetPO();
        po.setEquipmentCode(equipmentCode);
        po.setEquipmentSubNo(subNo);
        po.setUnitId(dto.getUnitId());
        po.setCategoryId(dto.getCategoryId());
        copyMutable(dto, po);
        po.setVersion(0);
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(po.getCreatedAt());
        po.setCreatedBy(operatorId);
        po.setUpdatedBy(operatorId);
        try {
            assetMapper.insert(po);
            if (codeRuleMapper.advance(rule.getId(), sequence) != 1) {
                throw new BusinessException(ErrorCode.EQUIPMENT_CONCURRENT_MODIFICATION);
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.EQUIPMENT_CONCURRENT_MODIFICATION);
        }
        return assemble(List.of(po)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EquipmentAssetVO update(Integer id, EquipmentAssetUpdateDTO dto, Integer operatorId) {
        requireAsset(id);
        requireManufacturer(dto.getManufacturerId());
        requireRenovationType(dto.getRenovationTypeId());
        requireParent(dto.getParentEquipmentId(), id);
        validateDates(dto.getProductionDate(), dto.getArrivalDate(), dto.getCommissioningDate());

        LambdaUpdateWrapper<EquipmentAssetPO> update = new LambdaUpdateWrapper<EquipmentAssetPO>()
                .eq(EquipmentAssetPO::getId, id)
                .eq(EquipmentAssetPO::getVersion, dto.getVersion())
                .set(EquipmentAssetPO::getRenovationTypeId, dto.getRenovationTypeId())
                .set(EquipmentAssetPO::getParentEquipmentId, dto.getParentEquipmentId())
                .set(EquipmentAssetPO::getManufacturerId, dto.getManufacturerId())
                .set(EquipmentAssetPO::getEquipmentName, dto.getEquipmentName().trim())
                .set(EquipmentAssetPO::getModel, trim(dto.getModel()))
                .set(EquipmentAssetPO::getRatedPowerKw, dto.getRatedPowerKw())
                .set(EquipmentAssetPO::getRatedVoltageV, dto.getRatedVoltageV())
                .set(EquipmentAssetPO::getRatedCurrentA, dto.getRatedCurrentA())
                .set(EquipmentAssetPO::getRatedSpeedRpm, dto.getRatedSpeedRpm())
                .set(EquipmentAssetPO::getPrice, dto.getPrice())
                .set(EquipmentAssetPO::getInstallationCost, dto.getInstallationCost())
                .set(EquipmentAssetPO::getInstallationLocation, trim(dto.getInstallationLocation()))
                .set(EquipmentAssetPO::getFactorySerialNo, trim(dto.getFactorySerialNo()))
                .set(EquipmentAssetPO::getProductionDate, dto.getProductionDate())
                .set(EquipmentAssetPO::getArrivalDate, dto.getArrivalDate())
                .set(EquipmentAssetPO::getCommissioningDate, dto.getCommissioningDate())
                .set(EquipmentAssetPO::getRemark, trim(dto.getRemark()))
                .set(EquipmentAssetPO::getUpdatedBy, operatorId)
                .set(EquipmentAssetPO::getUpdatedAt, LocalDateTime.now())
                .setSql("version = version + 1");
        if (assetMapper.update(null, update) != 1) {
            throw new BusinessException(ErrorCode.EQUIPMENT_CONCURRENT_MODIFICATION);
        }
        return assemble(List.of(requireAsset(id))).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        requireAsset(id);
        Long children = assetMapper.selectCount(new LambdaQueryWrapper<EquipmentAssetPO>()
                .eq(EquipmentAssetPO::getParentEquipmentId, id));
        Long repairs = repairRecordMapper.selectCount(new LambdaQueryWrapper<com.Laibin.SugarInventory.equipment.domain.po.EquipmentRepairRecordPO>()
                .eq(com.Laibin.SugarInventory.equipment.domain.po.EquipmentRepairRecordPO::getEquipmentId, id));
        if (children > 0 || repairs > 0) throw new BusinessException(ErrorCode.EQUIPMENT_DATA_IN_USE);
        try {
            assetMapper.deleteById(id);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.EQUIPMENT_DATA_IN_USE);
        }
    }

    @Override
    public byte[] export(EquipmentAssetQueryDTO query) {
        EquipmentAssetQueryDTO source = query == null ? new EquipmentAssetQueryDTO() : query;
        return EquipmentWorkbookExporter.assets(assemble(assetMapper.selectList(wrapper(source))));
    }

    private LambdaQueryWrapper<EquipmentAssetPO> wrapper(EquipmentAssetQueryDTO query) {
        String keyword = trim(query.getKeyword());
        LambdaQueryWrapper<EquipmentAssetPO> wrapper = new LambdaQueryWrapper<EquipmentAssetPO>()
                .and(StringUtils.hasText(keyword), value -> value
                        .like(EquipmentAssetPO::getEquipmentCode, keyword)
                        .or().like(EquipmentAssetPO::getEquipmentSubNo, keyword)
                        .or().like(EquipmentAssetPO::getEquipmentName, keyword))
                .eq(query.getUnitId() != null, EquipmentAssetPO::getUnitId, query.getUnitId())
                .eq(query.getCategoryId() != null, EquipmentAssetPO::getCategoryId, query.getCategoryId())
                .eq(query.getManufacturerId() != null, EquipmentAssetPO::getManufacturerId, query.getManufacturerId())
                .eq(query.getRenovationTypeId() != null, EquipmentAssetPO::getRenovationTypeId, query.getRenovationTypeId())
                .orderByAsc(EquipmentAssetPO::getEquipmentCode);
        if (Boolean.TRUE.equals(query.getAuxiliaryOnly())) wrapper.isNotNull(EquipmentAssetPO::getParentEquipmentId);
        if (Boolean.FALSE.equals(query.getAuxiliaryOnly())) wrapper.isNull(EquipmentAssetPO::getParentEquipmentId);
        return wrapper;
    }

    private List<EquipmentAssetVO> assemble(List<EquipmentAssetPO> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        Set<Integer> unitIds = ids(rows, EquipmentAssetPO::getUnitId);
        Set<Integer> categoryIds = ids(rows, EquipmentAssetPO::getCategoryId);
        Set<Integer> manufacturerIds = ids(rows, EquipmentAssetPO::getManufacturerId);
        Set<Integer> renovationTypeIds = ids(rows, EquipmentAssetPO::getRenovationTypeId);
        Set<Integer> parentIds = ids(rows, EquipmentAssetPO::getParentEquipmentId);
        Map<Integer, EquipmentUnitPO> units = map(unitMapper.selectBatchIds(unitIds));
        Map<Integer, EquipmentCategoryPO> categories = map(categoryMapper.selectBatchIds(categoryIds));
        Map<Integer, EquipmentManufacturerPO> manufacturers = manufacturerIds.isEmpty()
                ? Collections.emptyMap() : map(manufacturerMapper.selectBatchIds(manufacturerIds));
        Map<Integer, EquipmentRenovationTypePO> renovationTypes = renovationTypeIds.isEmpty()
                ? Collections.emptyMap() : map(renovationTypeMapper.selectBatchIds(renovationTypeIds));
        Map<Integer, EquipmentAssetPO> parents = parentIds.isEmpty()
                ? Collections.emptyMap() : map(assetMapper.selectBatchIds(parentIds));
        return rows.stream().map(row -> EquipmentAssembler.toAssetVO(row, units, categories, manufacturers, renovationTypes, parents)).toList();
    }

    private void copyMutable(EquipmentAssetCreateDTO dto, EquipmentAssetPO po) {
        po.setRenovationTypeId(dto.getRenovationTypeId()); po.setParentEquipmentId(dto.getParentEquipmentId());
        po.setManufacturerId(dto.getManufacturerId()); po.setEquipmentName(dto.getEquipmentName().trim());
        po.setModel(trim(dto.getModel())); po.setRatedPowerKw(dto.getRatedPowerKw()); po.setRatedVoltageV(dto.getRatedVoltageV());
        po.setRatedCurrentA(dto.getRatedCurrentA()); po.setRatedSpeedRpm(dto.getRatedSpeedRpm()); po.setPrice(dto.getPrice());
        po.setInstallationCost(dto.getInstallationCost()); po.setInstallationLocation(trim(dto.getInstallationLocation()));
        po.setFactorySerialNo(trim(dto.getFactorySerialNo())); po.setProductionDate(dto.getProductionDate());
        po.setArrivalDate(dto.getArrivalDate()); po.setCommissioningDate(dto.getCommissioningDate()); po.setRemark(trim(dto.getRemark()));
    }

    private EquipmentAssetPO requireAsset(Integer id) {
        EquipmentAssetPO po = id == null ? null : assetMapper.selectById(id);
        if (po == null) throw new BusinessException(ErrorCode.EQUIPMENT_NOT_FOUND);
        return po;
    }

    private EquipmentUnitPO requireEnabledUnit(Integer id) {
        EquipmentUnitPO po = id == null ? null : unitMapper.selectById(id);
        if (po == null || !Boolean.TRUE.equals(po.getEnabled())) throw new BusinessException(ErrorCode.EQUIPMENT_REFERENCE_INVALID);
        return po;
    }

    private EquipmentCategoryPO requireEnabledCategory(Integer id) {
        EquipmentCategoryPO po = id == null ? null : categoryMapper.selectById(id);
        if (po == null || !Boolean.TRUE.equals(po.getEnabled())) throw new BusinessException(ErrorCode.EQUIPMENT_REFERENCE_INVALID);
        return po;
    }

    private void requireManufacturer(Integer id) {
        if (id != null && manufacturerMapper.selectById(id) == null) throw new BusinessException(ErrorCode.EQUIPMENT_REFERENCE_INVALID);
    }

    private void requireRenovationType(Integer id) {
        if (id == null) return;
        EquipmentRenovationTypePO po = renovationTypeMapper.selectById(id);
        if (po == null || !Boolean.TRUE.equals(po.getEnabled())) throw new BusinessException(ErrorCode.EQUIPMENT_REFERENCE_INVALID);
    }

    private void requireParent(Integer parentId, Integer currentId) {
        if (parentId == null) return;
        if (Objects.equals(parentId, currentId)) throw new BusinessException(ErrorCode.EQUIPMENT_REFERENCE_INVALID);
        EquipmentAssetPO current = assetMapper.selectById(parentId);
        if (current == null) throw new BusinessException(ErrorCode.EQUIPMENT_REFERENCE_INVALID);
        int depth = 0;
        while (current.getParentEquipmentId() != null) {
            if (Objects.equals(current.getParentEquipmentId(), currentId) || depth++ > 100) {
                throw new BusinessException(ErrorCode.EQUIPMENT_REFERENCE_INVALID);
            }
            current = assetMapper.selectById(current.getParentEquipmentId());
            if (current == null) throw new BusinessException(ErrorCode.EQUIPMENT_REFERENCE_INVALID);
        }
    }

    private void validateDates(LocalDate production, LocalDate arrival, LocalDate commissioning) {
        if (production != null && arrival != null && production.isAfter(arrival)) {
            throw new BusinessException(400, "生产日期不能晚于进厂日期");
        }
        if (arrival != null && commissioning != null && arrival.isAfter(commissioning)) {
            throw new BusinessException(400, "进厂日期不能晚于启用日期");
        }
    }

    private int page(Integer value) { return value == null || value < 1 ? 1 : value; }
    private int size(Integer value) { return value == null ? 10 : Math.min(100, Math.max(1, value)); }
    private String trim(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private String normalizeCode(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }

    private <T> Set<Integer> ids(List<T> rows, Function<T, Integer> getter) {
        return rows.stream().map(getter).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private <T extends BaseEntity> Map<Integer, T> map(List<T> rows) {
        if (rows == null || rows.isEmpty()) return Collections.emptyMap();
        return rows.stream().collect(Collectors.toMap(T::getId, Function.identity()));
    }
}

package com.Laibin.SugarInventory.equipment.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.equipment.domain.assembler.EquipmentAssembler;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentRepairCreateDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentRepairQueryDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentRepairUpdateDTO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentAssetPO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentCategoryPO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentRepairRecordPO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentRepairTypePO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentUnitPO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentRepairVO;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentAssetMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentCategoryMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentRepairRecordMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentRepairTypeMapper;
import com.Laibin.SugarInventory.equipment.mapper.EquipmentUnitMapper;
import com.Laibin.SugarInventory.equipment.service.EquipmentRepairService;
import com.Laibin.SugarInventory.equipment.service.EquipmentWorkbookExporter;
import com.Laibin.SugarInventory.service.LoggableService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EquipmentRepairServiceImpl implements EquipmentRepairService, LoggableService<EquipmentRepairRecordPO> {
    private final EquipmentRepairRecordMapper repairMapper;
    private final EquipmentRepairTypeMapper repairTypeMapper;
    private final EquipmentAssetMapper assetMapper;
    private final EquipmentUnitMapper unitMapper;
    private final EquipmentCategoryMapper categoryMapper;

    @Override
    public EquipmentRepairRecordPO findById(Integer id) {
        return repairMapper.selectById(id);
    }

    @Override
    public String getTableName() {
        return "设备修理记录";
    }

    @Override
    public PageResult<EquipmentRepairVO> page(EquipmentRepairQueryDTO query) {
        EquipmentRepairQueryDTO source = query == null ? new EquipmentRepairQueryDTO() : query;
        validateDates(source);
        List<Integer> assetIds = filteredAssetIds(source);
        if (assetIds != null && assetIds.isEmpty()) return new PageResult<>(0L, List.of());
        Page<EquipmentRepairRecordPO> result = repairMapper.selectPage(
                new Page<>(page(source.getPage()), size(source.getSize())), wrapper(source, assetIds));
        return new PageResult<>(result.getTotal(), assemble(result.getRecords()));
    }

    @Override
    public EquipmentRepairVO detail(Integer id) {
        EquipmentRepairRecordPO po = requireRepair(id);
        return assemble(List.of(po)).get(0);
    }

    @Override
    public List<EquipmentRepairVO> listByEquipment(Integer equipmentId) {
        List<EquipmentRepairRecordPO> rows = repairMapper.selectList(new LambdaQueryWrapper<EquipmentRepairRecordPO>()
                .eq(EquipmentRepairRecordPO::getEquipmentId, equipmentId)
                .orderByDesc(EquipmentRepairRecordPO::getRepairDate)
                .orderByDesc(EquipmentRepairRecordPO::getId));
        return assemble(rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EquipmentRepairVO create(EquipmentRepairCreateDTO dto, Integer operatorId) {
        requireAsset(dto.getEquipmentId());
        requireEnabledType(dto.getRepairTypeId());
        EquipmentRepairRecordPO po = new EquipmentRepairRecordPO();
        po.setEquipmentId(dto.getEquipmentId());
        po.setRepairDate(dto.getRepairDate());
        po.setRepairTypeId(dto.getRepairTypeId());
        po.setRepairPerson(trim(dto.getRepairPerson()));
        po.setAcceptancePerson(trim(dto.getAcceptancePerson()));
        po.setRepairContent(dto.getRepairContent().trim());
        po.setVersion(0);
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(po.getCreatedAt());
        po.setCreatedBy(operatorId);
        po.setUpdatedBy(operatorId);
        repairMapper.insert(po);
        return detail(po.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EquipmentRepairVO update(Integer id, EquipmentRepairUpdateDTO dto, Integer operatorId) {
        requireRepair(id);
        requireAsset(dto.getEquipmentId());
        requireEnabledType(dto.getRepairTypeId());
        LambdaUpdateWrapper<EquipmentRepairRecordPO> update = new LambdaUpdateWrapper<EquipmentRepairRecordPO>()
                .eq(EquipmentRepairRecordPO::getId, id)
                .eq(EquipmentRepairRecordPO::getVersion, dto.getVersion())
                .set(EquipmentRepairRecordPO::getEquipmentId, dto.getEquipmentId())
                .set(EquipmentRepairRecordPO::getRepairDate, dto.getRepairDate())
                .set(EquipmentRepairRecordPO::getRepairTypeId, dto.getRepairTypeId())
                .set(EquipmentRepairRecordPO::getRepairPerson, trim(dto.getRepairPerson()))
                .set(EquipmentRepairRecordPO::getAcceptancePerson, trim(dto.getAcceptancePerson()))
                .set(EquipmentRepairRecordPO::getRepairContent, dto.getRepairContent().trim())
                .set(EquipmentRepairRecordPO::getUpdatedBy, operatorId)
                .set(EquipmentRepairRecordPO::getUpdatedAt, LocalDateTime.now())
                .setSql("version = version + 1");
        if (repairMapper.update(null, update) != 1) {
            throw new BusinessException(ErrorCode.EQUIPMENT_CONCURRENT_MODIFICATION);
        }
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        requireRepair(id);
        repairMapper.deleteById(id);
    }

    @Override
    public byte[] export(EquipmentRepairQueryDTO query) {
        EquipmentRepairQueryDTO source = query == null ? new EquipmentRepairQueryDTO() : query;
        validateDates(source);
        List<Integer> assetIds = filteredAssetIds(source);
        if (assetIds != null && assetIds.isEmpty()) return EquipmentWorkbookExporter.repairs(List.of());
        List<EquipmentRepairRecordPO> rows = repairMapper.selectList(wrapper(source, assetIds));
        return EquipmentWorkbookExporter.repairs(assemble(rows));
    }

    private LambdaQueryWrapper<EquipmentRepairRecordPO> wrapper(EquipmentRepairQueryDTO query, List<Integer> assetIds) {
        return new LambdaQueryWrapper<EquipmentRepairRecordPO>()
                .eq(query.getEquipmentId() != null, EquipmentRepairRecordPO::getEquipmentId, query.getEquipmentId())
                .in(assetIds != null, EquipmentRepairRecordPO::getEquipmentId, assetIds == null ? List.of() : assetIds)
                .eq(query.getRepairTypeId() != null, EquipmentRepairRecordPO::getRepairTypeId, query.getRepairTypeId())
                .like(StringUtils.hasText(query.getRepairPerson()), EquipmentRepairRecordPO::getRepairPerson, trim(query.getRepairPerson()))
                .ge(query.getStartDate() != null, EquipmentRepairRecordPO::getRepairDate, query.getStartDate())
                .le(query.getEndDate() != null, EquipmentRepairRecordPO::getRepairDate, query.getEndDate())
                .orderByDesc(EquipmentRepairRecordPO::getRepairDate)
                .orderByDesc(EquipmentRepairRecordPO::getId);
    }

    private List<Integer> filteredAssetIds(EquipmentRepairQueryDTO query) {
        boolean filtered = StringUtils.hasText(query.getKeyword()) || query.getUnitId() != null || query.getCategoryId() != null;
        if (!filtered) return null;
        String keyword = trim(query.getKeyword());
        List<Integer> ids = assetMapper.selectList(new LambdaQueryWrapper<EquipmentAssetPO>()
                        .and(StringUtils.hasText(keyword), value -> value
                                .like(EquipmentAssetPO::getEquipmentCode, keyword)
                                .or().like(EquipmentAssetPO::getEquipmentName, keyword))
                        .eq(query.getUnitId() != null, EquipmentAssetPO::getUnitId, query.getUnitId())
                        .eq(query.getCategoryId() != null, EquipmentAssetPO::getCategoryId, query.getCategoryId())
                        .select(EquipmentAssetPO::getId))
                .stream().map(EquipmentAssetPO::getId).toList();
        if (query.getEquipmentId() != null && !ids.contains(query.getEquipmentId())) return List.of();
        return ids;
    }

    private List<EquipmentRepairVO> assemble(List<EquipmentRepairRecordPO> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        Set<Integer> assetIds = rows.stream().map(EquipmentRepairRecordPO::getEquipmentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, EquipmentAssetPO> assets = map(assetMapper.selectBatchIds(assetIds));
        Set<Integer> unitIds = assets.values().stream().map(EquipmentAssetPO::getUnitId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Integer> categoryIds = assets.values().stream().map(EquipmentAssetPO::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Integer> typeIds = rows.stream().map(EquipmentRepairRecordPO::getRepairTypeId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, EquipmentUnitPO> units = map(unitMapper.selectBatchIds(unitIds));
        Map<Integer, EquipmentCategoryPO> categories = map(categoryMapper.selectBatchIds(categoryIds));
        Map<Integer, EquipmentRepairTypePO> types = map(repairTypeMapper.selectBatchIds(typeIds));
        return rows.stream().map(row -> EquipmentAssembler.toRepairVO(row, assets, units, categories, types)).toList();
    }

    private EquipmentRepairRecordPO requireRepair(Integer id) {
        EquipmentRepairRecordPO po = id == null ? null : repairMapper.selectById(id);
        if (po == null) throw new BusinessException(ErrorCode.EQUIPMENT_REPAIR_NOT_FOUND);
        return po;
    }

    private EquipmentAssetPO requireAsset(Integer id) {
        EquipmentAssetPO po = id == null ? null : assetMapper.selectById(id);
        if (po == null) throw new BusinessException(ErrorCode.EQUIPMENT_NOT_FOUND);
        return po;
    }

    private void requireEnabledType(Integer id) {
        EquipmentRepairTypePO po = id == null ? null : repairTypeMapper.selectById(id);
        if (po == null || !Boolean.TRUE.equals(po.getEnabled())) {
            throw new BusinessException(ErrorCode.EQUIPMENT_REFERENCE_INVALID);
        }
    }

    private void validateDates(EquipmentRepairQueryDTO query) {
        if (query.getStartDate() != null && query.getEndDate() != null && query.getStartDate().isAfter(query.getEndDate())) {
            throw new BusinessException(400, "修理日期起点不能晚于终点");
        }
    }

    private int page(Integer value) {
        return value == null || value < 1 ? 1 : value;
    }

    private int size(Integer value) {
        return value == null ? 10 : Math.min(100, Math.max(1, value));
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private <T extends com.Laibin.SugarInventory.domain.po.BaseEntity> Map<Integer, T> map(List<T> rows) {
        if (rows == null || rows.isEmpty()) return Collections.emptyMap();
        return rows.stream().collect(Collectors.toMap(T::getId, Function.identity()));
    }
}

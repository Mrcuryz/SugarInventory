package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.QualityStandardDTO;
import com.Laibin.SugarInventory.domain.dto.QualityStandardItemDTO;
import com.Laibin.SugarInventory.domain.po.QualityStandard;
import com.Laibin.SugarInventory.domain.po.QualityStandardItem;
import com.Laibin.SugarInventory.domain.vo.QualityStandardItemVO;
import com.Laibin.SugarInventory.domain.vo.QualityStandardRelatedProductVO;
import com.Laibin.SugarInventory.domain.vo.QualityStandardVO;
import com.Laibin.SugarInventory.mapper.AssayMapper;
import com.Laibin.SugarInventory.mapper.ProductQualityStandardRelationMapper;
import com.Laibin.SugarInventory.mapper.QualityStandardItemMapper;
import com.Laibin.SugarInventory.mapper.QualityStandardMapper;
import com.Laibin.SugarInventory.service.LoggableService;
import com.Laibin.SugarInventory.service.QualityStandardService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QualityStandardServiceImpl implements QualityStandardService, LoggableService<QualityStandard> {

    private static final List<MetricPreset> FIXED_METRIC_PRESETS = List.of(
            new MetricPreset("color_value", "色值", "IU"),
            new MetricPreset("reducing_sugar", "还原糖分", "g/100g"),
            new MetricPreset("dry_weight_loss", "干燥失重", "g/100g"),
            new MetricPreset("conductivity_ash", "电导灰分", "g/100g"),
            new MetricPreset("sucrose", "蔗糖分", "g/100g"),
            new MetricPreset("insoluble_impurity", "不溶于水杂质", "mg/kg"),
            new MetricPreset("ph", "pH", "")
    );

    private static final Map<String, MetricPreset> METRIC_PRESET_MAP = FIXED_METRIC_PRESETS.stream()
            .collect(Collectors.toMap(MetricPreset::metricCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));

    @Autowired
    private QualityStandardMapper qualityStandardMapper;

    @Autowired
    private QualityStandardItemMapper qualityStandardItemMapper;

    @Autowired
    private ProductQualityStandardRelationMapper relationMapper;

    @Autowired
    private AssayMapper assayMapper;

    @Override
    public List<QualityStandardVO> listQualityStandards(String productType, String standardName, String status) {
        List<QualityStandard> standards = qualityStandardMapper.selectByConditions(productType, standardName, status);
        standards.forEach(this::attachItems);
        Map<Integer, List<QualityStandardRelatedProductVO>> relatedProductMap = loadRelatedProductMap(standards);
        return standards.stream()
                .map(standard -> convertToVO(standard, relatedProductMap.getOrDefault(standard.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<QualityStandardVO> pageQualityStandards(String productType, String standardName, String status, Integer page, Integer size) {
        List<QualityStandardVO> records = listQualityStandards(productType, standardName, status);
        int effectivePage = page == null || page < 1 ? 1 : page;
        int effectiveSize = size == null || size < 1 ? 10 : size;
        int fromIndex = Math.min((effectivePage - 1) * effectiveSize, records.size());
        int toIndex = Math.min(fromIndex + effectiveSize, records.size());
        return new PageResult<>((long) records.size(), records.subList(fromIndex, toIndex));
    }

    @Override
    public QualityStandardVO getQualityStandardById(Integer id) {
        QualityStandard standard = qualityStandardMapper.selectById(id);
        if (standard == null) {
            throw new BusinessException("化验标准不存在");
        }
        attachItems(standard);
        return convertToVO(standard, loadRelatedProducts(standard.getId()));
    }

    @Override
    @Transactional
    public QualityStandard addQualityStandard(QualityStandardDTO dto) {
        validateDuplicate(dto, null);
        List<QualityStandardItemDTO> normalizedItems = normalizeItems(dto.getItems());
        QualityStandard standard = convertToEntity(dto);
        standard.setVersion(dto.getVersion() == null ? 1 : dto.getVersion());
        standard.setStatus(dto.getStatus() == null ? "ENABLED" : dto.getStatus());
        standard.setCreatedAt(LocalDateTime.now());
        standard.setUpdatedAt(LocalDateTime.now());
        qualityStandardMapper.insert(standard);
        replaceItems(standard.getId(), normalizedItems);
        attachItems(standard);
        return standard;
    }

    @Override
    @Transactional
    public QualityStandard updateQualityStandard(Integer id, QualityStandardDTO dto) {
        QualityStandard existing = qualityStandardMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("化验标准不存在");
        }
        validateDuplicate(dto, id);
        List<QualityStandardItemDTO> normalizedItems = normalizeItems(dto.getItems());
        QualityStandard updated = convertToEntity(dto);
        updated.setId(id);
        updated.setVersion(dto.getVersion() == null ? existing.getVersion() : dto.getVersion());
        updated.setStatus(dto.getStatus() == null ? existing.getStatus() : dto.getStatus());
        updated.setCreatedAt(existing.getCreatedAt());
        updated.setCreatedBy(existing.getCreatedBy());
        updated.setUpdatedAt(LocalDateTime.now());
        qualityStandardMapper.updateById(updated);
        replaceItems(id, normalizedItems);
        attachItems(updated);
        return updated;
    }

    @Override
    @Transactional
    public void deleteQualityStandard(Integer id) {
        ensureStandardExists(id);
        if (isStandardInUse(id)) {
            throw new BusinessException("当前标准正在被使用！");
        }
        deleteStandardData(id);
    }

    @Override
    @Transactional
    public void forceDeleteQualityStandard(Integer id) {
        ensureStandardExists(id);
        if (assayMapper.countByAppliedStandardId(id) > 0) {
            throw new BusinessException("当前标准已被历史化验记录引用，不能强制删除");
        }
        relationMapper.deleteByQualityStandardId(id);
        deleteStandardData(id);
    }

    private boolean isStandardInUse(Integer id) {
        return relationMapper.countByQualityStandardId(id) > 0 || assayMapper.countByAppliedStandardId(id) > 0;
    }

    private void ensureStandardExists(Integer id) {
        if (qualityStandardMapper.selectById(id) == null) {
            throw new BusinessException("删除失败，记录不存在");
        }
    }

    private void deleteStandardData(Integer id) {
        qualityStandardItemMapper.deleteByQualityStandardId(id);
        int deleted = qualityStandardMapper.deleteById(id);
        if (deleted == 0) {
            throw new BusinessException("删除失败，记录不存在");
        }
    }

    private QualityStandardVO convertToVO(QualityStandard standard, List<QualityStandardRelatedProductVO> relatedProducts) {
        QualityStandardVO vo = new QualityStandardVO();
        BeanUtils.copyProperties(standard, vo);
        if (CollectionUtils.isNotEmpty(standard.getItems())) {
            vo.setItems(standard.getItems().stream().map(this::convertItemToVO).collect(Collectors.toList()));
        } else {
            vo.setItems(Collections.emptyList());
        }
        vo.setRelatedProducts(relatedProducts == null ? Collections.emptyList() : relatedProducts);
        return vo;
    }

    private Map<Integer, List<QualityStandardRelatedProductVO>> loadRelatedProductMap(List<QualityStandard> standards) {
        if (CollectionUtils.isEmpty(standards)) {
            return Collections.emptyMap();
        }
        List<Integer> standardIds = standards.stream().map(QualityStandard::getId).toList();
        return relationMapper.selectRelatedProductsByStandardIds(standardIds).stream()
                .collect(Collectors.groupingBy(QualityStandardRelatedProductVO::getQualityStandardId, LinkedHashMap::new, Collectors.toList()));
    }

    private List<QualityStandardRelatedProductVO> loadRelatedProducts(Integer standardId) {
        if (standardId == null) {
            return Collections.emptyList();
        }
        return relationMapper.selectRelatedProductsByStandardIds(List.of(standardId));
    }

    private QualityStandardItemVO convertItemToVO(QualityStandardItem item) {
        QualityStandardItemVO vo = new QualityStandardItemVO();
        BeanUtils.copyProperties(item, vo);
        return vo;
    }

    private QualityStandard convertToEntity(QualityStandardDTO dto) {
        QualityStandard entity = new QualityStandard();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private QualityStandardItem convertItemToEntity(Integer qualityStandardId, QualityStandardItemDTO dto) {
        QualityStandardItem entity = new QualityStandardItem();
        BeanUtils.copyProperties(dto, entity);
        entity.setId(null);
        entity.setQualityStandardId(qualityStandardId);
        entity.setCompareType(inferCompareType(dto));
        entity.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        return entity;
    }

    private List<QualityStandardItemDTO> normalizeItems(List<QualityStandardItemDTO> items) {
        if (CollectionUtils.isEmpty(items)) {
            throw new BusinessException("请完整配置 7 项固定指标");
        }
        Map<String, QualityStandardItemDTO> itemMap = new LinkedHashMap<>();
        for (QualityStandardItemDTO item : items) {
            if (item == null || item.getMetricCode() == null || item.getMetricCode().isBlank()) {
                throw new BusinessException("指标编码不能为空");
            }
            MetricPreset preset = METRIC_PRESET_MAP.get(item.getMetricCode());
            if (preset == null) {
                throw new BusinessException("存在不支持的指标：" + item.getMetricCode());
            }
            if (itemMap.putIfAbsent(item.getMetricCode(), item) != null) {
                throw new BusinessException("指标不能重复配置：" + preset.metricName());
            }
        }
        if (itemMap.size() != FIXED_METRIC_PRESETS.size()) {
            throw new BusinessException("请完整配置 7 项固定指标");
        }

        return FIXED_METRIC_PRESETS.stream()
                .map(preset -> buildNormalizedItem(preset, itemMap.get(preset.metricCode())))
                .collect(Collectors.toList());
    }

    private QualityStandardItemDTO buildNormalizedItem(MetricPreset preset, QualityStandardItemDTO source) {
        if (source == null) {
            throw new BusinessException("缺少固定指标：" + preset.metricName());
        }
        validateRange(preset.metricName(), source.getMinValue(), source.getMaxValue());

        QualityStandardItemDTO normalized = new QualityStandardItemDTO();
        normalized.setId(source.getId());
        normalized.setMetricCode(preset.metricCode());
        normalized.setMetricName(preset.metricName());
        normalized.setMinValue(source.getMinValue());
        normalized.setMaxValue(source.getMaxValue());
        normalized.setUnit(preset.unit());
        normalized.setCompareType(inferCompareType(source));
        normalized.setSortOrder(indexOfMetric(preset.metricCode()));
        normalized.setRemark(source.getRemark());
        return normalized;
    }

    private Integer indexOfMetric(String metricCode) {
        for (int i = 0; i < FIXED_METRIC_PRESETS.size(); i++) {
            if (Objects.equals(FIXED_METRIC_PRESETS.get(i).metricCode(), metricCode)) {
                return (i + 1) * 10;
            }
        }
        return 0;
    }

    private void validateRange(String metricName, BigDecimal minValue, BigDecimal maxValue) {
        if (minValue != null && maxValue != null && minValue.compareTo(maxValue) > 0) {
            throw new BusinessException(metricName + "下限不能大于上限");
        }
    }

    private String inferCompareType(QualityStandardItemDTO dto) {
        if (dto.getMinValue() != null && dto.getMaxValue() == null) {
            return "gte";
        }
        if (dto.getMinValue() == null && dto.getMaxValue() != null) {
            return "lte";
        }
        return "range";
    }

    private void attachItems(QualityStandard standard) {
        standard.setItems(qualityStandardItemMapper.selectByQualityStandardId(standard.getId()));
    }

    private void replaceItems(Integer qualityStandardId, List<QualityStandardItemDTO> items) {
        qualityStandardItemMapper.deleteByQualityStandardId(qualityStandardId);
        if (CollectionUtils.isEmpty(items)) {
            return;
        }
        for (QualityStandardItemDTO item : items) {
            qualityStandardItemMapper.insert(convertItemToEntity(qualityStandardId, item));
        }
    }

    private void validateDuplicate(QualityStandardDTO dto, Integer currentId) {
        QueryWrapper<QualityStandard> wrapper = new QueryWrapper<>();
        wrapper.eq("product_type", dto.getProductType())
                .eq("standard_name", dto.getStandardName())
                .eq("version", dto.getVersion() == null ? 1 : dto.getVersion());
        if (currentId != null) {
            wrapper.ne("id", currentId);
        }
        if (qualityStandardMapper.selectOne(wrapper) != null) {
            throw new BusinessException("同产品类型下相同标准名称和版本已存在");
        }
        if (dto.getStandardCode() != null && !dto.getStandardCode().isBlank()) {
            QueryWrapper<QualityStandard> codeWrapper = new QueryWrapper<>();
            codeWrapper.eq("standard_code", dto.getStandardCode());
            if (currentId != null) {
                codeWrapper.ne("id", currentId);
            }
            if (qualityStandardMapper.selectOne(codeWrapper) != null) {
                throw new BusinessException("标准编号已存在");
            }
        }
    }

    @Override
    public QualityStandard findById(Integer id) {
        return qualityStandardMapper.selectById(id);
    }

    @Override
    public String getTableName() {
        return "化验标准";
    }

    private record MetricPreset(String metricCode, String metricName, String unit) {
    }
}

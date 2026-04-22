package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.ProductQualityStandardRelationDTO;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.ProductQualityStandardRelation;
import com.Laibin.SugarInventory.domain.po.QualityStandard;
import com.Laibin.SugarInventory.domain.vo.ProductQualityStandardRelationVO;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.ProductQualityStandardRelationMapper;
import com.Laibin.SugarInventory.mapper.QualityStandardMapper;
import com.Laibin.SugarInventory.service.ProductQualityStandardRelationService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProductQualityStandardRelationServiceImpl implements ProductQualityStandardRelationService {

    @Autowired
    private ProductQualityStandardRelationMapper relationMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private QualityStandardMapper qualityStandardMapper;

    @Override
    public List<ProductQualityStandardRelationVO> listByProductId(Integer productId) {
        ensureProductExists(productId);
        return relationMapper.selectViewByProductId(productId);
    }

    @Override
    @Transactional
    public ProductQualityStandardRelationVO bindRelation(ProductQualityStandardRelationDTO dto, Integer operatorId) {
        Product product = ensureProductExists(dto.getProductId());
        QualityStandard standard = ensureStandardExists(dto.getQualityStandardId());
        validateRelation(product, standard);

        QueryWrapper<ProductQualityStandardRelation> wrapper = new QueryWrapper<>();
        wrapper.eq("product_id", dto.getProductId()).eq("quality_standard_id", dto.getQualityStandardId());
        if (relationMapper.selectOne(wrapper) != null) {
            throw new BusinessException("该产品与标准关系已存在");
        }

        ProductQualityStandardRelation relation = new ProductQualityStandardRelation();
        BeanUtils.copyProperties(dto, relation);
        relation.setPriority(dto.getPriority() == null ? 100 : dto.getPriority());
        relation.setEnabled(dto.getEnabled() == null ? Boolean.TRUE : dto.getEnabled());
        relation.setIsDefault(dto.getIsDefault() == null ? relationMapper.countEnabledByProductId(dto.getProductId()) == 0 : dto.getIsDefault());
        relation.setCreatedAt(LocalDateTime.now());
        relation.setUpdatedAt(LocalDateTime.now());
        relation.setCreatedBy(operatorId);
        relation.setUpdatedBy(operatorId);
        relationMapper.insert(relation);

        if (Boolean.TRUE.equals(relation.getIsDefault())) {
            applyDefault(relation.getId(), relation.getProductId(), operatorId);
        }
        return listByProductId(dto.getProductId()).stream()
                .filter(item -> item.getId().equals(relation.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("产品标准关系创建后读取失败"));
    }

    @Override
    @Transactional
    public ProductQualityStandardRelationVO updateRelation(Integer id, ProductQualityStandardRelationDTO dto, Integer operatorId) {
        ProductQualityStandardRelation existing = relationMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("产品标准关系不存在");
        }
        ensureProductExists(existing.getProductId());
        ensureStandardExists(dto.getQualityStandardId() == null ? existing.getQualityStandardId() : dto.getQualityStandardId());

        QueryWrapper<ProductQualityStandardRelation> wrapper = new QueryWrapper<>();
        wrapper.eq("product_id", existing.getProductId())
                .eq("quality_standard_id", dto.getQualityStandardId() == null ? existing.getQualityStandardId() : dto.getQualityStandardId())
                .ne("id", id);
        if (relationMapper.selectOne(wrapper) != null) {
            throw new BusinessException("该产品与标准关系已存在");
        }

        ProductQualityStandardRelation updated = new ProductQualityStandardRelation();
        BeanUtils.copyProperties(existing, updated);
        if (dto.getQualityStandardId() != null) {
            updated.setQualityStandardId(dto.getQualityStandardId());
        }
        if (dto.getPriority() != null) {
            updated.setPriority(dto.getPriority());
        }
        if (dto.getEnabled() != null) {
            updated.setEnabled(dto.getEnabled());
        }
        if (dto.getEffectiveFrom() != null || dto.getEffectiveTo() != null) {
            updated.setEffectiveFrom(dto.getEffectiveFrom());
            updated.setEffectiveTo(dto.getEffectiveTo());
        }
        if (dto.getRemark() != null) {
            updated.setRemark(dto.getRemark());
        }
        if (dto.getIsDefault() != null) {
            updated.setIsDefault(dto.getIsDefault());
        }
        updated.setUpdatedAt(LocalDateTime.now());
        updated.setUpdatedBy(operatorId);
        relationMapper.updateById(updated);

        if (Boolean.TRUE.equals(updated.getIsDefault())) {
            applyDefault(updated.getId(), updated.getProductId(), operatorId);
        }
        return listByProductId(updated.getProductId()).stream()
                .filter(item -> item.getId().equals(updated.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("产品标准关系更新后读取失败"));
    }

    @Override
    @Transactional
    public ProductQualityStandardRelationVO setDefaultRelation(Integer id, Integer operatorId) {
        ProductQualityStandardRelation existing = relationMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("产品标准关系不存在");
        }
        applyDefault(id, existing.getProductId(), operatorId);
        return listByProductId(existing.getProductId()).stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException("默认标准更新后读取失败"));
    }

    @Override
    @Transactional
    public void deleteRelation(Integer id) {
        ProductQualityStandardRelation existing = relationMapper.selectById(id);
        if (existing == null) {
            return;
        }
        if (relationMapper.countEnabledByProductId(existing.getProductId()) <= 1 && Boolean.TRUE.equals(existing.getEnabled())) {
            throw new BusinessException("不能删除产品最后一个启用中的标准关系");
        }
        relationMapper.deleteById(id);
    }

    private Product ensureProductExists(Integer productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("产品不存在");
        }
        return product;
    }

    private QualityStandard ensureStandardExists(Integer qualityStandardId) {
        QualityStandard standard = qualityStandardMapper.selectById(qualityStandardId);
        if (standard == null) {
            throw new BusinessException("化验标准不存在");
        }
        return standard;
    }

    private void validateRelation(Product product, QualityStandard standard) {
        if (product.getProductType() != null && standard.getProductType() != null
                && !product.getProductType().equals(standard.getProductType())) {
            throw new BusinessException("产品类型与标准适用品类不一致");
        }
    }

    private void applyDefault(Integer relationId, Integer productId, Integer operatorId) {
        relationMapper.clearDefaultByProductId(productId, operatorId);
        ProductQualityStandardRelation relation = relationMapper.selectById(relationId);
        relation.setIsDefault(Boolean.TRUE);
        relation.setUpdatedAt(LocalDateTime.now());
        relation.setUpdatedBy(operatorId);
        relationMapper.updateById(relation);
    }
}

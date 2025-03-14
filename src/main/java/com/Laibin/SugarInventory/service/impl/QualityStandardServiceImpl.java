package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.QualityStandardDTO;
import com.Laibin.SugarInventory.domain.po.QualityStandard;
import com.Laibin.SugarInventory.domain.po.ScreenMesh;
import com.Laibin.SugarInventory.domain.vo.QualityStandardVO;
import com.Laibin.SugarInventory.mapper.QualityStandardMapper;
import com.Laibin.SugarInventory.service.LoggableService;
import com.Laibin.SugarInventory.service.QualityStandardService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class QualityStandardServiceImpl implements QualityStandardService, LoggableService<QualityStandard> {

    @Autowired
    private QualityStandardMapper qualityStandardMapper;

    @Override
    public List<QualityStandardVO> listQualityStandards(String productType, String standardName) {
        List<QualityStandard> standards = qualityStandardMapper
                .selectByProduct(productType, standardName);
        return standards.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public QualityStandardVO getQualityStandardById(Integer id) {
        QualityStandard standard = qualityStandardMapper.selectById(id);
        if (standard == null) {
            throw new BusinessException("检验标准不存在");
        }
        return convertToVO(standard);
    }

    @Override
    @Transactional
    public QualityStandard addQualityStandard(QualityStandardDTO dto) {
        // 检查是否已存在相同的标准
        QueryWrapper<QualityStandard> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("product_type", dto.getProductType())
                .eq("standard_name", dto.getStandardName());

        if (qualityStandardMapper.selectOne(queryWrapper) != null) {
            throw new BusinessException("该产品类型下的检验标准已存在");
        }

        QualityStandard standard = convertToEntity(dto);
        qualityStandardMapper.insert(standard);
        return standard;
    }

    @Override
    @Transactional
    public QualityStandard updateQualityStandard(Integer id, QualityStandardDTO dto) {
        QualityStandard existing = qualityStandardMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("检验标准不存在");
        }

        QualityStandard updatedStandard = convertToEntity(dto);
        updatedStandard.setId(id);
        qualityStandardMapper.updateById(updatedStandard);
        return updatedStandard;
    }

    @Override
    @Transactional
    public void deleteQualityStandard(Integer id) {
        int deleted = qualityStandardMapper.deleteById(id);
        if (deleted == 0) {
            throw new BusinessException("删除失败，可能记录不存在");
        }
    }

    private QualityStandardVO convertToVO(QualityStandard standard) {
        QualityStandardVO vo = new QualityStandardVO();
        BeanUtils.copyProperties(standard, vo);
        return vo;
    }

    private QualityStandard convertToEntity(QualityStandardDTO dto) {
        QualityStandard entity = new QualityStandard();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    @Override
    public QualityStandard findById(Integer id) {
        return qualityStandardMapper.selectById(id);
    }

    @Override
    public String getTableName() {
        return "检验标准";
    }
}

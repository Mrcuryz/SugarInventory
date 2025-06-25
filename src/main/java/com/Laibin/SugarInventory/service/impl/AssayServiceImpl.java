package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.AssayCheckDTO;
import com.Laibin.SugarInventory.domain.dto.AssayQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.domain.po.Assay;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.QualityStandard;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.AssayVO;
import com.Laibin.SugarInventory.mapper.AssayMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.QualityStandardMapper;
import com.Laibin.SugarInventory.service.AssayService;
import com.Laibin.SugarInventory.service.LoggableService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@Service
public class AssayServiceImpl extends ServiceImpl<AssayMapper, Assay> implements AssayService, LoggableService<Assay> {
    @Autowired
    private AssayMapper assayMapper;

    @Autowired
    private QualityStandardMapper qualityStandardMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional
    public void importAssays(List<AssaySubmitDTO> dtos, Integer operatorId) {
        List<Assay> assays = dtos.stream()
                .map(dto -> {
                    Product product = productMapper.selectById(dto.getProductId());
                    List<QualityStandard> standards = qualityStandardMapper.selectByProductType(product.getProductType());
                    if (standards.isEmpty()) {
                        throw new BusinessException("未找到该产品的质量标准");
                    }

                    List<String> qualifiedStandards = new ArrayList<>();
                    boolean isQualified = false;

                    for (QualityStandard standard : standards) {
                        if (checkStandardCompliance(dto, standard)) {
                            qualifiedStandards.add(standard.getStandardName());
                            isQualified = true;  // 只要有一个标准符合，就算合格
                        }
                    }

                    Assay assay = new Assay();
                    BeanUtils.copyProperties(dto, assay);
                    assay.setTestedBy(operatorId);
                    assay.setSucrose(dto.getSucrose());
                    assay.setIsQualified(isQualified? "合格" : "不合格");
                    if(!isQualified)
                        qualifiedStandards.add("无");
                    assay.setCreatedAt(LocalDateTime.now());
                    try {
                        assay.setQualifiedStandards(objectMapper.writeValueAsString(qualifiedStandards));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }

                    assayMapper.insert(assay);
                    return assay;
                })
                .toList();

        if (assays.isEmpty()) {
            throw new BusinessException(ErrorCode.DATA_IMPORT_FAILED);
        }
    }

    @Override
    public PageResult<AssayVO> queryAssays(AssayQueryDTO query) {
        List<AssayVO> records = assayMapper.selectAssayList(
                query,
                (query.getPage()-1)*query.getSize(),
                query.getSize()
        );

        Long total = assayMapper.countAssay(query);
        if (records == null) {
            throw new BusinessException(ErrorCode.ASSAY_NOT_FOUND);
        }

        return new PageResult<>(total, records);
    }

    @Override
    @Transactional
    public AssayVO updateAssay(Integer id, AssaySubmitDTO dto, User operator) throws JsonProcessingException {
        Assay latestAssay = new Assay();

        try {
            latestAssay = assayMapper.selectByProductIdAndDate(dto.getProductId(), dto.getSampleDate());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.ASSAY_RECORD_NOT_FOUND);
        }

        Product product = productMapper.selectById(dto.getProductId());
        List<QualityStandard> standards = qualityStandardMapper.selectByProductType(product.getProductType());
        if (standards.isEmpty()) {
            throw new BusinessException("未找到该产品的质量标准");
        }

        List<String> qualifiedStandards = new ArrayList<>();
        boolean isQualified = false;

        for (QualityStandard standard : standards) {
            if (checkStandardCompliance(dto, standard)) {
                qualifiedStandards.add(standard.getStandardName());
                isQualified = true;  // 只要有一个标准符合，就算合格
            }
        }
        Assay newAssay = new Assay();
        newAssay.setProductId(dto.getProductId());
        BeanUtils.copyProperties(dto, newAssay);
        newAssay.setTestedBy(operator.getId());
        newAssay.setIsQualified(isQualified? "合格" : "不合格");
        if(!isQualified)
            qualifiedStandards.add("无");
        newAssay.setCreatedAt(LocalDateTime.now());
        newAssay.setQualifiedStandards(objectMapper.writeValueAsString(qualifiedStandards));
        newAssay.setVersion(latestAssay.getVersion() + 1);  // 版本递增

        // 插入新的记录
        int rows = assayMapper.insert(newAssay);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.RECORD_CREATE_FAILED);
        }
        // 返回更新后的记录
        AssayVO assayVO = new AssayVO();
        BeanUtils.copyProperties(newAssay, assayVO);
        return assayVO;
    }

    @Override
    public void deleteAssay(Integer id) {
        try {
            int rows = assayMapper.deleteById(id);
            if (rows == 0) {
                throw new BusinessException("记录删除失败");
            }
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("该记录已被其他数据关联，无法删除");
        }
    }

    @Override
    public Boolean existedAssay(AssayCheckDTO dto) {
        return assayMapper.existsByProductIdAndDate(dto.getProductId(), dto.getEntryDate());
    }

    private boolean checkStandardCompliance(AssaySubmitDTO assay, QualityStandard standard) {
        return checkValue(assay.getColorValue(), standard.getColorMin(), standard.getColorMax()) &&
                checkValue(assay.getReducingSugar(), standard.getReducingSugarMin(), standard.getReducingSugarMax()) &&
                checkValue(assay.getDryWeight(), standard.getDryWeightMin(), standard.getDryWeightMax()) &&
                checkValue(assay.getConductivityAsh(), standard.getConductivityAshMin(), standard.getConductivityAshMax()) &&
                checkValue(assay.getSucrose(), standard.getSucroseMin(), standard.getSucroseMax()) &&
                checkValue(assay.getInsolubleImpurity(), standard.getInsolubleImpurityMin(), standard.getInsolubleImpurityMax()) &&
                checkValue(assay.getPhValue(), standard.getPhMin(), standard.getPhMax());
    }

    //校验某个数值是否符合指标
    private boolean checkValue(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null && min == null && max == null) {
            return true; // 化验数据为空，且标准里上下限都为空，则无需校验
        } else if (value == null) {
            return false; // 化验数据为空，则不合格
        }
        if (min != null && max == null) {
            return value.compareTo(min) >= 0;  // 只有下限，必须大于等于下限
        }
        if (min == null && max != null) {
            return value.compareTo(max) <= 0;  // 只有上限，必须小于等于上限
        }
        if (min != null && max != null) {
            return value.compareTo(min) >= 0 && value.compareTo(max) <= 0; // 同时存在上下限
        }
        return true; // 如果标准里上下限都为空，则默认合格
    }

    @Override
    public Assay findById(Integer id) {
        return assayMapper.selectById(id);
    }

    @Override
    public String getTableName() {
        return "化验数据";
    }
}

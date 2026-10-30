package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.AssayCheckDTO;
import com.Laibin.SugarInventory.domain.dto.AssayQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.Assay;
import com.Laibin.SugarInventory.domain.po.AssayGroup;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.AssayFailedMetricVO;
import com.Laibin.SugarInventory.domain.vo.AssayStandardSnapshotVO;
import com.Laibin.SugarInventory.domain.vo.AssayVO;
import com.Laibin.SugarInventory.mapper.AssayMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.service.AssayGroupService;
import com.Laibin.SugarInventory.service.AssayService;
import com.Laibin.SugarInventory.service.AssayWriteService;
import com.Laibin.SugarInventory.service.LoggableService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class AssayServiceImpl extends ServiceImpl<AssayMapper, Assay> implements AssayService, LoggableService<Assay> {
    @Autowired
    private AssayMapper assayMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Resource
    private AssayGroupService assayGroupService;

    @Autowired
    private AssayWriteService assayWriteService;

    @Override
    @Transactional
    public void importAssays(List<AssaySubmitDTO> dtos, Integer operatorId) {
        if (dtos == null || dtos.isEmpty() || dtos.stream().anyMatch(java.util.Objects::isNull)) {
            throw new BusinessException("化验导入数据不能为空");
        }
        if (dtos.get(0).getSelectType() == null || dtos.get(0).getSelectType() == 1) {
            if (dtos.stream().anyMatch(dto -> dto.getSelectType() != null && dto.getSelectType() != 1)) {
                throw new BusinessException(ErrorCode.ASSAY_ENTRY_INPUT_INVALID);
            }
            assayWriteService.lockProducts(dtos.stream().map(AssaySubmitDTO::getProductId).toList());
            dtos.forEach(dto -> assayWriteService.append(dto, operatorId));
            return;
        }

        AssaySubmitDTO dto = dtos.get(0);
        if (dtos.size() != 1) {
            throw new BusinessException(ErrorCode.ASSAY_ENTRY_INPUT_INVALID.getCode(), "批量化验组请一次提交一组检测值");
        }
        if (dto.getSelectType() != 2 || dto.getRelatedId() == null) {
            throw new BusinessException("请选择批量化验组");
        }
        AssayGroup assayGroup = assayGroupService.getById(dto.getRelatedId());
        if (assayGroup == null) {
            throw new BusinessException("批量化验组不存在");
        }
        if (StringUtils.isBlank(assayGroup.getRelatedProducts())) {
            throw new BusinessException(ErrorCode.ASSAY_ENTRY_INPUT_INVALID.getCode(), "批量化验组没有产品，无法录入");
        }
        List<Integer> productIds;
        try {
            productIds = Arrays.stream(assayGroup.getRelatedProducts().split(",", -1))
                    .map(String::trim).map(Integer::valueOf).distinct().sorted().toList();
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.ASSAY_ENTRY_INPUT_INVALID.getCode(), "批量化验组产品信息异常，请核对后重试");
        }
        assayWriteService.lockProducts(productIds);
        for (Integer productId : productIds) {
            AssaySubmitDTO item = new AssaySubmitDTO();
            BeanUtils.copyProperties(dto, item);
            item.setProductId(productId);
            assayWriteService.append(item, operatorId);
        }
    }

    @Override
    public PageResult<AssayVO> queryAssays(AssayQueryDTO query) {
        List<AssayVO> records = assayMapper.selectAssayList(
                query,
                (query.getPage() - 1) * query.getSize(),
                query.getSize()
        );
        Long total = assayMapper.countAssay(query);
        if (records == null) {
            throw new BusinessException(ErrorCode.ASSAY_NOT_FOUND);
        }
        records.forEach(this::enrichAssayVO);
        return new PageResult<>(total, records);
    }

    @Override
    @Transactional
    public AssayVO updateAssay(Integer id, AssaySubmitDTO dto, User operator) throws JsonProcessingException {
        if (dto == null || dto.getProductId() == null || dto.getSampleDate() == null) {
            throw new BusinessException(ErrorCode.ASSAY_ENTRY_INPUT_INVALID);
        }
        assayWriteService.lockProducts(List.of(dto.getProductId()));
        Assay target = assayMapper.selectById(id);
        if (target == null || !java.util.Objects.equals(target.getProductId(), dto.getProductId())
                || !java.util.Objects.equals(target.getSampleDate(), dto.getSampleDate())) {
            throw new BusinessException(ErrorCode.ASSAY_RECORD_NOT_FOUND);
        }
        Assay newAssay = assayWriteService.append(dto, operator.getId());

        AssayVO assayVO = new AssayVO();
        BeanUtils.copyProperties(newAssay, assayVO);
        enrichAssayVO(assayVO);
        return assayVO;
    }

    @Override
    public AssayVO getAssayById(Integer id) {
        Assay assay = assayMapper.selectById(id);
        if (assay == null) {
            throw new BusinessException(ErrorCode.ASSAY_RECORD_NOT_FOUND);
        }
        AssayVO assayVO = new AssayVO();
        BeanUtils.copyProperties(assay, assayVO);
        Product product = productMapper.selectById(assay.getProductId());
        if (product != null) {
            assayVO.setProductName(product.getProductName());
        }
        enrichAssayVO(assayVO);
        return assayVO;
    }

    @Override
    public AssayVO getLatestByProductIdAndDate(Integer productId, LocalDate productionDate) {
        if (productId == null || productionDate == null) {
            return null;
        }
        Assay assay = assayMapper.selectByProductIdAndDate(productId, productionDate);
        if (assay == null) {
            return null;
        }
        AssayVO assayVO = new AssayVO();
        BeanUtils.copyProperties(assay, assayVO);
        Product product = productMapper.selectById(assay.getProductId());
        if (product != null) {
            assayVO.setProductName(product.getProductName());
        }
        enrichAssayVO(assayVO);
        return assayVO;
    }

    @Override
    @Transactional
    public void deleteAssay(Integer id) {
        Assay target = assayMapper.selectById(id);
        if (target == null) throw new BusinessException(ErrorCode.ASSAY_RECORD_NOT_FOUND);
        assayWriteService.lockProducts(List.of(target.getProductId()));
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

    @Override
    public Assay findById(Integer id) {
        return assayMapper.selectById(id);
    }

    @Override
    public String getTableName() {
        return "化验数据";
    }

    private void enrichAssayVO(AssayVO vo) {
        vo.setMatchedStandards(parseJson(vo.getQualifiedStandards(), new TypeReference<List<String>>() {}, Collections.emptyList()));
        vo.setFailedMetrics(parseJson(vo.getFailedMetricsJson(), new TypeReference<List<AssayFailedMetricVO>>() {}, Collections.emptyList()));
        vo.setStandardSnapshot(parseJson(vo.getStandardSnapshotJson(), new TypeReference<AssayStandardSnapshotVO>() {}, null));
        if (vo.getAppliedStandardId() != null) {
            var appliedStandard = new com.Laibin.SugarInventory.domain.vo.AssayAppliedStandardVO();
            appliedStandard.setId(vo.getAppliedStandardId());
            appliedStandard.setStandardName(vo.getAppliedStandardName());
            appliedStandard.setVersion(vo.getAppliedStandardVersion());
            vo.setAppliedStandard(appliedStandard);
        }
    }

    private <T> T parseJson(String json, TypeReference<T> typeReference, T defaultValue) {
        if (StringUtils.isBlank(json)) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (Exception ex) {
            return defaultValue;
        }
    }
}

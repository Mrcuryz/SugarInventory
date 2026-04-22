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
import com.Laibin.SugarInventory.service.AssayStandardJudgeService;
import com.Laibin.SugarInventory.service.LoggableService;
import com.Laibin.SugarInventory.service.ProductService;
import com.Laibin.SugarInventory.service.model.AssayJudgeOutcome;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AssayServiceImpl extends ServiceImpl<AssayMapper, Assay> implements AssayService, LoggableService<Assay> {
    @Autowired
    private AssayMapper assayMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductService productService;

    @Autowired
    private ObjectMapper objectMapper;

    @Resource
    private AssayGroupService assayGroupService;

    @Autowired
    private AssayStandardJudgeService assayStandardJudgeService;

    @Override
    @Transactional
    public void importAssays(List<AssaySubmitDTO> dtos, Integer operatorId) {
        if (dtos == null || dtos.isEmpty()) {
            throw new BusinessException("化验导入数据不能为空");
        }
        if (dtos.get(0).getSelectType() == null || dtos.get(0).getSelectType() == 1) {
            List<Assay> assays = dtos.stream()
                    .map(dto -> createAndInsertAssay(dto, dto.getProductId(), operatorId, false))
                    .toList();
            if (assays.isEmpty()) {
                throw new BusinessException(ErrorCode.DATA_IMPORT_FAILED);
            }
            return;
        }

        AssaySubmitDTO dto = dtos.get(0);
        if (dto.getSelectType() != 2 || dto.getRelatedId() == null) {
            throw new BusinessException("请选择批量化验组");
        }
        AssayGroup assayGroup = assayGroupService.getById(dto.getRelatedId());
        if (assayGroup == null) {
            throw new BusinessException("批量化验组不存在");
        }
        if (StringUtils.isBlank(assayGroup.getRelatedProducts())) {
            return;
        }
        Collection<Product> products = productService.getMapByIds(Arrays.stream(assayGroup.getRelatedProducts().split(","))
                        .map(Integer::valueOf)
                        .collect(Collectors.toList()))
                .values();
        for (Product product : products) {
            createAndInsertAssay(dto, product.getId(), operatorId, false);
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
        Assay latestAssay;
        try {
            latestAssay = assayMapper.selectByProductIdAndDate(dto.getProductId(), dto.getSampleDate());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.ASSAY_RECORD_NOT_FOUND);
        }
        if (latestAssay == null) {
            throw new BusinessException(ErrorCode.ASSAY_RECORD_NOT_FOUND);
        }
        Assay newAssay = createAndInsertAssay(dto, dto.getProductId(), operator.getId(), true);
        newAssay.setVersion(latestAssay.getVersion() + 1);
        assayMapper.updateById(newAssay);

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

    @Override
    public Assay findById(Integer id) {
        return assayMapper.selectById(id);
    }

    @Override
    public String getTableName() {
        return "化验数据";
    }

    private Assay createAndInsertAssay(AssaySubmitDTO dto, Integer productId, Integer operatorId, boolean forceNextVersion) {
        if (productId == null) {
            throw new BusinessException("请选择产品");
        }
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("产品不存在");
        }
        AssayJudgeOutcome outcome = assayStandardJudgeService.judge(product, dto);
        Assay assay = new Assay();
        BeanUtils.copyProperties(dto, assay);
        assay.setProductId(productId);
        assay.setTestedBy(operatorId);
        assay.setCreatedAt(LocalDateTime.now());
        applyJudgeOutcome(assay, outcome);
        assay.setVersion(resolveVersion(productId, dto, forceNextVersion));
        assayMapper.insert(assay);
        return assay;
    }

    private Integer resolveVersion(Integer productId, AssaySubmitDTO dto, boolean forceNextVersion) {
        Assay latestAssay = assayMapper.selectByProductIdAndDate(productId, dto.getSampleDate());
        if (latestAssay == null) {
            return 1;
        }
        if (forceNextVersion) {
            return latestAssay.getVersion() + 1;
        }
        return latestAssay.getVersion() + 1;
    }

    private void applyJudgeOutcome(Assay assay, AssayJudgeOutcome outcome) {
        assay.setQualifiedStandards(outcome.getQualifiedStandardsJson());
        assay.setIsQualified(outcome.getCompatibleConclusion());
        assay.setAppliedStandardId(outcome.getAppliedStandardId());
        assay.setAppliedStandardName(outcome.getAppliedStandardName());
        assay.setAppliedStandardVersion(outcome.getAppliedStandardVersion());
        assay.setJudgeResult(outcome.getJudgeResult());
        assay.setJudgeMessage(outcome.getJudgeMessage());
        assay.setFailedMetricCount(outcome.getFailedMetricCount());
        assay.setFailedMetricsJson(outcome.getFailedMetricsJson());
        assay.setStandardSnapshotJson(outcome.getStandardSnapshotJson());
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

package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.AssayQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.domain.po.Assay;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.AssayVO;
import com.Laibin.SugarInventory.mapper.AssayMapper;
import com.Laibin.SugarInventory.service.AssayService;
import com.Laibin.SugarInventory.service.LoggableService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.time.LocalDateTime;
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

    @Override
    @Transactional
    public void importAssays(List<AssaySubmitDTO> dtos, Integer operatorId) {
        List<Assay> assays = dtos.stream()
                .map(dto -> {
                    Assay assay = new Assay();
                    BeanUtils.copyProperties(dto, assay);
                    assay.setTestedBy(operatorId);
                    assay.setCreatedAt(LocalDateTime.now());
                    return assay;
                })
                .collect(Collectors.toList());

        if (!batchInsert(assays)) {
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
    public AssayVO updateAssay(Integer id, AssaySubmitDTO dto, User operator) {
        Assay latestAssay = assayMapper.selectByProductIdAndDate(dto.getProductId(), dto.getSampleDate());
        Assay newAssay = new Assay();
        newAssay.setProductId(dto.getProductId());
        newAssay.setSampleDate(dto.getSampleDate());
        newAssay.setColorValue(dto.getColorValue());
        newAssay.setReducingSugar(dto.getReducingSugar());
        newAssay.setPhValue(dto.getPhValue());
        newAssay.setTestedBy(operator.getId());
        newAssay.setCreatedAt(LocalDateTime.now());
        newAssay.setVersion(latestAssay.getVersion() + 1);  // 版本递增

        // 插入新的记录
        assayMapper.insert(newAssay);

        // 返回更新后的记录
        AssayVO assayVO = new AssayVO();
        BeanUtils.copyProperties(newAssay, assayVO);
        return assayVO;
    }

    private boolean batchInsert(List<Assay> assays) {
        try {
            return assayMapper.insertBatch(assays) == assays.size();
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_ASSAY_RECORD);
        }
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

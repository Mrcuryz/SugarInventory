package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.AssayGroupQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssayGroupSubmitDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.AssayGroup;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.AssayGroupVO;
import com.Laibin.SugarInventory.mapper.AssayGroupMapper;
import com.Laibin.SugarInventory.service.AssayGroupService;
import com.Laibin.SugarInventory.service.LoggableService;
import com.Laibin.SugarInventory.service.ProductService;
import com.Laibin.SugarInventory.service.SemiProductRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@Service
public class AssayGroupServiceImpl extends ServiceImpl<AssayGroupMapper, AssayGroup> implements AssayGroupService, LoggableService<AssayGroup> {

    @Resource
    private AssayGroupMapper assayGroupMapper;

    @Resource
    private ProductService productService;

    @Override
    public void addAssays(AssayGroupSubmitDTO dto, Integer userId) {
        AssayGroup assay = new AssayGroup();
        BeanUtils.copyProperties(dto, assay);
        assay.setCreatedBy(userId);
        assay.setCreatedAt(LocalDateTime.now());
        assayGroupMapper.insert(assay);
    }

    @Override
    public PageResult<AssayGroupVO> queryAssays(AssayGroupQueryDTO query) {
        List<AssayGroupVO> records = assayGroupMapper.selectAssayList(
                query,
                (query.getPage() - 1) * query.getSize(),
                query.getSize()
        );
        if (CollectionUtils.isNotEmpty(records)) {
            List<Integer> productAllIds = Arrays.stream(records.stream().map(AssayGroupVO::getRelatedProducts)
                            .filter(StringUtils::isNoneBlank)
                            .distinct()
                            .collect(Collectors.joining(",")).split(","))
                    .map(Integer::valueOf).toList();
            Map<Integer, Product> productMap = productService.getMapByIds(productAllIds);

            records.forEach(assay -> {
                if (StringUtils.isNotBlank(assay.getRelatedProducts())) {
                    String[] productIds = assay.getRelatedProducts().split(",");
                    List<Product> relatedProductList = Arrays.stream(productIds).map(Integer::valueOf)
                            .map(productMap::get).toList();
                    assay.setRelatedProductList(relatedProductList);
                }
            });
        }
        Long total = assayGroupMapper.countAssay(query);
        if (records == null) {
            throw new BusinessException(ErrorCode.ASSAY_NOT_FOUND);
        }

        return new PageResult<>(total, records);
    }

    @Override
    public List<AssayGroup> listByProductId(Integer productId) {
        if (productId == null || productId <= 0) {
            throw new BusinessException(400, "产品不能为空");
        }
        return assayGroupMapper.selectByProductId(productId);
    }

    @Override
    public AssayGroup updateAssay(Integer id, AssayGroupSubmitDTO dto, User user) {
        AssayGroup assayGroup = assayGroupMapper.selectById(id);
        if (assayGroup == null) {
            throw new BusinessException(ErrorCode.ASSAY_GROUP_RECORD_NOT_FOUND);
        }
        AssayGroup assay = new AssayGroup();
        BeanUtils.copyProperties(dto, assay);
        assay.setUpdatedBy(user.getId());
        assay.setUpdatedAt(LocalDateTime.now());
        assayGroupMapper.updateById(assay);
        return assay;
    }

    @Override
    public void deleteAssay(Integer id) {
        AssayGroup assayGroup = assayGroupMapper.selectById(id);
        if (assayGroup == null) {
            return;
        }
        int rows = assayGroupMapper.deleteById(id);
        if (rows == 0) {
            throw new BusinessException("记录删除失败");
        }
    }

    @Override
    public AssayGroup findById(Integer id) {
        return assayGroupMapper.selectById(id);
    }

    @Override
    public String getTableName() {
        return "assay_group";
    }
}

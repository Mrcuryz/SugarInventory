package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.Assay;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.dto.ProductCreateDTO;
import com.Laibin.SugarInventory.domain.vo.ProductInfoVO;
import com.Laibin.SugarInventory.domain.dto.ProductUpdateDTO;
import com.Laibin.SugarInventory.domain.vo.ProductVO;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.service.LoggableService;
import com.Laibin.SugarInventory.service.ProductService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService, LoggableService<Product> {
    @Autowired
    private ProductMapper productMapper;

    @Override
    public List<Product> getProductsByCondition(String name, String type, String status) {
        return productMapper.selectProductsByName(name, type, status);
    }

    @Override
    public List<ProductInfoVO> getSemiProductNames() {
        List<ProductInfoVO> list = productMapper.selectSemiProductNames();
        for(ProductInfoVO vo : list){
            if(vo.getPackagingMethod() == null || vo.getPackagingMethod().isEmpty()){
                vo.setPackagingMethod("件");
            }
            vo.setProductName(vo.getProductName() + "(" + vo.getWeightPerPiece() + "kg/" + vo.getPackagingMethod() + ")");
        }
        return list;
    }

    @Override
    public List<ProductInfoVO> getSemiProductsByCondition(String name, String type) {
        // 校验产品类型合法性
        if (type != null && !Arrays.asList("黄冰糖", "白冰糖").contains(type)) {
            throw new IllegalArgumentException("无效的产品类型");
        }
        List<ProductInfoVO> list = productMapper.selectSemiProductsByCondition(name, type);
        for(ProductInfoVO vo : list){
            if(vo.getPackagingMethod() == null || vo.getPackagingMethod().isEmpty()){
                vo.setPackagingMethod("件");
            }
            vo.setProductName(vo.getProductName() + "(" + vo.getWeightPerPiece() + "kg/" + vo.getPackagingMethod() + ")");
        }
        return list;
    }

    @Override
    public List<ProductInfoVO> getFinishedProductNames() {
        List<ProductInfoVO> list = productMapper.selectFinishedProductNames();
        for(ProductInfoVO vo : list){
            if(vo.getPackagingMethod() == null || vo.getPackagingMethod().isEmpty()){
                vo.setPackagingMethod("件");
            }
            vo.setProductName(vo.getProductName() + "(" + vo.getWeightPerPiece() + "kg/" + vo.getPackagingMethod() + ")");
        }
        return list;
    }

    @Override
    public List<ProductInfoVO> getFinishedProductsByCondition(String name, String type) {
        // 校验产品类型合法性
        if (type != null && !Arrays.asList("黄冰糖", "白冰糖").contains(type)) {
            throw new IllegalArgumentException("无效的产品类型");
        }
        List<ProductInfoVO> list = productMapper.selectFinishedProductsByCondition(name, type);
        for(ProductInfoVO vo : list){
            if(vo.getPackagingMethod() == null || vo.getPackagingMethod().isEmpty()){
                vo.setPackagingMethod("件");
            }
            vo.setProductName(vo.getProductName() + "(" + vo.getWeightPerPiece() + "kg/" + vo.getPackagingMethod() + ")");
        }
        return list;
    }

    @Override
    public void createProduct(ProductCreateDTO vo, User operator) {
        // 1. 校验名称唯一性
        if (productMapper.existsByName(vo.getProductName())) {
            throw new BusinessException(ErrorCode.PRODUCT_NAME_EXISTS);
        }

        // 2. 构建PO对象
        Product po = new Product();
        BeanUtils.copyProperties(vo, po);
        po.setCreatedBy(operator.getId());

        // 3. 持久化数据
        productMapper.insert(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(Integer productId, Integer operatorId) {
        // 1. 校验产品存在性
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        // 2. 执行删除
        productMapper.deleteById(productId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Product updateProduct(ProductUpdateDTO dto, Integer currentUserId) {
        // 1. 验证产品存在性
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        // 2. 验证枚举值合法性
        validateEnums(dto.getProductType(), dto.getStatus(), dto.getPackagingMethod());

        // 3. 执行动态更新
        int rows = productMapper.dynamicUpdate(
                dto.getProductId(),
                dto.getProductName(),
                dto.getProductType(),
                dto.getStatus(),
                dto.getPackagingMethod(),
                dto.getWeightPerPiece(),
                dto.getPiecesPerPallet(),
                currentUserId,
                LocalDateTime.now(),
                dto.getCanStack()
        );

        if (rows == 0) {
            throw new BusinessException("更新失败");
        }

        // 4. 返回更新后的产品信息
        return productMapper.selectById(dto.getProductId());
    }

    private void validateEnums(String type, String status, String packaging) {
        if (type != null && !Arrays.asList("黄冰糖", "白冰糖").contains(type)) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_TYPE);
        }
        if (status != null && !Arrays.asList("半成品", "成品").contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_STATUS);
        }
        if (packaging != null && !Arrays.asList("箱", "袋", "罐", "").contains(packaging)) {
            throw new BusinessException(ErrorCode.INVALID_PACKAGING_METHOD);
        }
    }

    @Override
    public Product findById(Integer id) {
        return productMapper.selectById(id);
    }

    @Override
    public String getTableName() {
        return "产品";
    }
}

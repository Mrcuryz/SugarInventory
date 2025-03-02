package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.dto.ProductCreateDTO;
import com.Laibin.SugarInventory.domain.vo.ProductInfoVO;
import com.Laibin.SugarInventory.domain.dto.ProductUpdateDTO;
import com.Laibin.SugarInventory.domain.vo.ProductVO;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
public interface ProductService extends IService<Product> {
    List<Product> getProductsByName(String name);

    List<ProductInfoVO> getSemiProductNames();

    List<ProductInfoVO> getSemiProductsByCondition(String name, String type);

    List<ProductInfoVO> getFinishedProductNames();

    List<ProductInfoVO> getFinishedProductsByCondition(String name, String type);

    @Transactional(rollbackFor = Exception.class)
    Product updateProduct(ProductUpdateDTO vo, Integer currentUserId);

    void createProduct(ProductCreateDTO vo, User operator);

    @Transactional(rollbackFor = Exception.class)
    void deleteProduct(Integer id, Integer id1);
}

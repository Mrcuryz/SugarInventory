package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.dto.ProductCreateDTO;
import com.Laibin.SugarInventory.domain.vo.ProductInfoVO;
import com.Laibin.SugarInventory.domain.dto.ProductUpdateDTO;
import com.Laibin.SugarInventory.domain.vo.ProductVO;
import com.Laibin.SugarInventory.domain.vo.VInventorySummary;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
public interface ProductService extends IService<Product> {
    List<Product> getProductsByCondition(String name, String type, String status);

    List<ProductInfoVO> getSemiProductNames();

    List<ProductInfoVO> getSemiProductsByCondition(String name, String type);

    List<ProductInfoVO> getFinishedProductNames();

    List<ProductInfoVO> getFinishedProductsByCondition(String name, String type);

    @Transactional(rollbackFor = Exception.class)
    Product updateProduct(ProductUpdateDTO dto, Integer currentUserId);

    void createProduct(ProductCreateDTO vo, User operator);

    @Transactional(rollbackFor = Exception.class)
    void deleteProduct(Integer id, Integer id1);

    /**
     * 获取产品所有存放的库位
     *
     * @param id 产品ID
     * @return 库位列表
     */
    List<VInventorySummary> getProductWarehouse(Integer id);

    /**
     * 获取产品列表
     *
     * @param productIds 产品ID列表
     * @return 产品列表
     */
    Map<Integer, Product> getMapByIds(List<Integer> productIds);
}

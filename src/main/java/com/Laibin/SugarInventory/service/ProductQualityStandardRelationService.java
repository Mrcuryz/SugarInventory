package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.ProductQualityStandardRelationDTO;
import com.Laibin.SugarInventory.domain.vo.ProductQualityStandardRelationVO;

import java.util.List;

public interface ProductQualityStandardRelationService {
    List<ProductQualityStandardRelationVO> listByProductId(Integer productId);

    ProductQualityStandardRelationVO bindRelation(ProductQualityStandardRelationDTO dto, Integer operatorId);

    ProductQualityStandardRelationVO updateRelation(Integer id, ProductQualityStandardRelationDTO dto, Integer operatorId);

    ProductQualityStandardRelationVO setDefaultRelation(Integer id, Integer operatorId);

    void deleteRelation(Integer id);
}

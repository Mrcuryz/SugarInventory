package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.ProductsWithoutRecentAssayQueryDTO;
import com.Laibin.SugarInventory.domain.vo.ProductsWithoutRecentAssayVO;

public interface ProductsWithoutRecentAssayService {
    ProductsWithoutRecentAssayVO queryProductsWithoutRecentAssay(ProductsWithoutRecentAssayQueryDTO query);
}

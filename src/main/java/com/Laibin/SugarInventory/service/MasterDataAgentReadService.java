package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.ProductCatalogAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.ProductDetailAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.ScreenMeshCatalogAgentQueryDTO;
import com.Laibin.SugarInventory.domain.vo.ProductCatalogAgentVO;
import com.Laibin.SugarInventory.domain.vo.ProductDetailAgentVO;
import com.Laibin.SugarInventory.domain.vo.ScreenMeshCatalogAgentVO;

public interface MasterDataAgentReadService {
    ProductCatalogAgentVO queryProductCatalog(ProductCatalogAgentQueryDTO query);
    ProductDetailAgentVO getProductDetail(ProductDetailAgentQueryDTO query);
    ScreenMeshCatalogAgentVO queryScreenMeshCatalog(ScreenMeshCatalogAgentQueryDTO query);
}

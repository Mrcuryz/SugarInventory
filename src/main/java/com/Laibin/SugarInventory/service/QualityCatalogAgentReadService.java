package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.QualityCatalogAgentQueries;
import com.Laibin.SugarInventory.domain.vo.QualityCatalogAgentVO;

public interface QualityCatalogAgentReadService {
    QualityCatalogAgentVO.AssayGroups queryAssayGroups(QualityCatalogAgentQueries.AssayGroups query);
    QualityCatalogAgentVO.Standards queryStandards(QualityCatalogAgentQueries.Standards query);
    QualityCatalogAgentVO.StandardDetail getStandardDetail(QualityCatalogAgentQueries.StandardDetail query);
    QualityCatalogAgentVO.ProductRelations queryProductRelations(QualityCatalogAgentQueries.ProductRelations query);
}

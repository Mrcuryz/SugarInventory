package com.Laibin.SugarInventory.production.service;

import com.Laibin.SugarInventory.production.domain.dto.ProductionEntityResolveQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionBoilingBatchTraceQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionBoilingBatchListQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionOrderProgressQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionMaterialPickTraceQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionLabelCompletionQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionInProcessMaterialsAgentQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionMaterialCandidatesAgentQueryDTO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionEntityResolutionVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchTraceVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchListVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderProgressVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionMaterialPickTraceVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionLabelCompletionVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionInProcessMaterialsVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionMaterialCandidatesVO;

public interface ProductionAgentReadService {
    ProductionEntityResolutionVO resolveEntities(ProductionEntityResolveQueryDTO query, int userId);

    ProductionOrderProgressVO queryOrderProgress(ProductionOrderProgressQueryDTO query, int userId);

    ProductionBoilingBatchTraceVO queryBoilingBatchTrace(ProductionBoilingBatchTraceQueryDTO query, int userId);

    ProductionBoilingBatchListVO queryBoilingBatches(ProductionBoilingBatchListQueryDTO query, int userId);

    ProductionMaterialPickTraceVO queryMaterialPickTrace(ProductionMaterialPickTraceQueryDTO query, int userId);

    ProductionLabelCompletionVO queryProductionLabelCompletion(ProductionLabelCompletionQueryDTO query, int userId);

    ProductionInProcessMaterialsVO queryInProcessMaterials(ProductionInProcessMaterialsAgentQueryDTO query);

    ProductionMaterialCandidatesVO queryMaterialCandidates(ProductionMaterialCandidatesAgentQueryDTO query, int userId);
}

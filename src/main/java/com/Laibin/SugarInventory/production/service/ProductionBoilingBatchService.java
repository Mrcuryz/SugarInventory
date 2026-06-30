package com.Laibin.SugarInventory.production.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.production.domain.dto.ProductionBoilingBatchQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionBoilingBatchSaveDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionBoilingBatchUsageDTO;
import com.Laibin.SugarInventory.production.domain.po.ProductionOrder;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchTraceNodeVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchUsageVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchVO;

import java.util.List;

public interface ProductionBoilingBatchService {
    PageResult<ProductionBoilingBatchVO> pageBatches(ProductionBoilingBatchQueryDTO query);

    ProductionBoilingBatchVO getDetail(Long id);

    ProductionBoilingBatchVO createBatch(ProductionBoilingBatchSaveDTO dto, Integer operatorId, String operatorName);

    ProductionBoilingBatchVO updateBatch(Long id, ProductionBoilingBatchSaveDTO dto);

    void cancelBatch(Long id, Integer operatorId);

    void reserveForOrder(ProductionOrder order, List<ProductionBoilingBatchUsageDTO> usages, Integer operatorId, String operatorName);

    void consumeReservedByOrder(Long orderId);

    void releaseReservedByOrder(Long orderId);

    List<ProductionBoilingBatchUsageVO> listUsagesByOrder(Long orderId);

    ProductionBoilingBatchTraceNodeVO getTrace(Long id);
}

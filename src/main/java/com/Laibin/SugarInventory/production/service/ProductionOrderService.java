package com.Laibin.SugarInventory.production.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.production.domain.dto.ProductionMaterialCandidateQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionInProcessMaterialQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionMaterialPickDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionFinishDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionLabelReserveDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionOrderCreateDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionOrderQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionOutputBindQrDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionOutputCreateDTO;
import com.Laibin.SugarInventory.production.domain.po.ProductionOrder;
import com.Laibin.SugarInventory.production.domain.po.ProductionOrderOutputCode;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBindQrResultVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionLabelBatchVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionMaterialCandidateVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionMaterialVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderDetailVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderOptionVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderPageVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOutputVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionQuantitySplitVO;

import java.util.List;

public interface ProductionOrderService {
    PageResult<ProductionOrderPageVO> pageOrders(ProductionOrderQueryDTO query);

    ProductionOrder createOrder(ProductionOrderCreateDTO dto, Integer operatorId, String operatorName);

    ProductionOrderDetailVO getOrderDetail(Long id);

    List<ProductionOrderOptionVO> listActiveOptions(String orderType);

    PageResult<ProductionMaterialCandidateVO> pageMaterialCandidates(Long orderId, ProductionMaterialCandidateQueryDTO query);

    PageResult<ProductionMaterialVO> pageInProcessMaterials(ProductionInProcessMaterialQueryDTO query);

    void pickMaterials(Long orderId, ProductionMaterialPickDTO dto, Integer operatorId, String operatorName);

    void finishMaterials(Long orderId, Integer operatorId);

    ProductionOutputVO addOutput(Long orderId, ProductionOutputCreateDTO dto, Integer operatorId);

    ProductionOutputVO updateOutput(Long outputId, ProductionOutputCreateDTO dto, Integer operatorId);

    void deleteOutput(Long outputId, Integer operatorId);

    void deleteOrder(Long orderId, Integer operatorId);

    void cancelOrder(Long orderId, Integer operatorId);

    ProductionBindQrResultVO bindFixedQrs(Long outputId, ProductionOutputBindQrDTO dto, Integer operatorId);

    List<ProductionOrderOutputCode> markOutputPrinted(Long outputId);

    List<ProductionLabelBatchVO> reserveLabels(Long orderId, ProductionLabelReserveDTO dto, Integer operatorId);

    List<ProductionLabelBatchVO> listLabelBatches(Long orderId);

    byte[] printLabelBatch(Long batchId);

    ProductionOrderDetailVO finishProduction(Long orderId, ProductionFinishDTO dto, Integer operatorId);

    ProductionQuantitySplitVO splitQuantity(Integer boardCount, Integer pieceCount, Integer piecesPerPallet);

    void syncInboundByTask(Integer palletTaskId, Integer inventoryId);
}

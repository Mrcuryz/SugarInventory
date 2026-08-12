package com.Laibin.SugarInventory.production.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.po.Inventory;
import com.Laibin.SugarInventory.domain.po.OutStock;
import com.Laibin.SugarInventory.domain.po.PalletCode;
import com.Laibin.SugarInventory.domain.po.PalletFlowRecord;
import com.Laibin.SugarInventory.domain.po.PalletTask;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.mapper.InventoryMapper;
import com.Laibin.SugarInventory.mapper.OutStockMapper;
import com.Laibin.SugarInventory.mapper.PalletCodeMapper;
import com.Laibin.SugarInventory.mapper.PalletFlowRecordMapper;
import com.Laibin.SugarInventory.mapper.PalletTaskMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.inventoryhistory.domain.StockMovementEventCommand;
import com.Laibin.SugarInventory.inventoryhistory.service.StockMovementEventService;
import com.Laibin.SugarInventory.production.domain.dto.ProductionMaterialCandidateQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionInProcessMaterialQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionFinishDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionLabelReserveDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionMaterialPickDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionOrderCreateDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionOrderQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionOutputBindQrDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionOutputCreateDTO;
import com.Laibin.SugarInventory.production.domain.po.ProductionOrder;
import com.Laibin.SugarInventory.production.domain.po.ProductionOrderLabelBatch;
import com.Laibin.SugarInventory.production.domain.po.ProductionOrderLabelCode;
import com.Laibin.SugarInventory.production.domain.po.ProductionOrderMaterial;
import com.Laibin.SugarInventory.production.domain.po.ProductionOrderOutput;
import com.Laibin.SugarInventory.production.domain.po.ProductionOrderOutputCode;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBindQrResultVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionLabelBatchVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionLabelCodeVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionMaterialCandidateVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionMaterialVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderBaseVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderDetailVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderOptionVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderPageVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOutputCodeVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOutputVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionQuantitySplitVO;
import com.Laibin.SugarInventory.production.mapper.ProductionOrderMapper;
import com.Laibin.SugarInventory.production.mapper.ProductionOrderLabelBatchMapper;
import com.Laibin.SugarInventory.production.mapper.ProductionOrderLabelCodeMapper;
import com.Laibin.SugarInventory.production.mapper.ProductionOrderMaterialMapper;
import com.Laibin.SugarInventory.production.mapper.ProductionOrderOutputCodeMapper;
import com.Laibin.SugarInventory.production.mapper.ProductionOrderOutputMapper;
import com.Laibin.SugarInventory.production.service.ProductionBoilingBatchService;
import com.Laibin.SugarInventory.production.service.ProductionOrderService;
import com.Laibin.SugarInventory.util.PalletQrLabelPdfRenderer;
import com.Laibin.SugarInventory.util.QrCodeUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductionOrderServiceImpl implements ProductionOrderService {
    private static final String ORDER_STATUS_ISSUED = "ISSUED";
    private static final String ORDER_STATUS_MATERIALING = "MATERIALING";
    private static final String ORDER_STATUS_MATERIALED = "MATERIALED";
    private static final String ORDER_STATUS_OUTPUT_BINDING = "OUTPUT_BINDING";
    private static final String ORDER_STATUS_PREPRINTED = "PREPRINTED";
    private static final String ORDER_STATUS_WAIT_INBOUND = "WAIT_INBOUND";
    private static final String ORDER_STATUS_PART_INBOUND = "PART_INBOUND";
    private static final String ORDER_STATUS_COMPLETED = "COMPLETED";
    private static final String ORDER_STATUS_CANCELED = "CANCELED";

    private final ProductionOrderMapper productionOrderMapper;
    private final ProductionOrderMaterialMapper materialMapper;
    private final ProductionOrderOutputMapper outputMapper;
    private final ProductionOrderOutputCodeMapper outputCodeMapper;
    private final ProductionOrderLabelBatchMapper labelBatchMapper;
    private final ProductionOrderLabelCodeMapper labelCodeMapper;
    private final ProductMapper productMapper;
    private final PalletCodeMapper palletCodeMapper;
    private final PalletTaskMapper palletTaskMapper;
    private final PalletFlowRecordMapper palletFlowRecordMapper;
    private final InventoryMapper inventoryMapper;
    private final WarehouseMapper warehouseMapper;
    private final OutStockMapper outStockMapper;
    private final ObjectMapper objectMapper;
    private final ProductionBoilingBatchService boilingBatchService;
    @Autowired
    private StockMovementEventService stockMovementEventService;

    @Override
    public PageResult<ProductionOrderPageVO> pageOrders(ProductionOrderQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        int offset = (page - 1) * size;
        List<ProductionOrder> orders = productionOrderMapper.pageOrders(query, offset, size);
        Long total = productionOrderMapper.countOrders(query);
        List<ProductionOrderPageVO> records = orders.stream().map(this::toPageVO).toList();
        return new PageResult<>(total, records);
    }

    @Override
    @Transactional
    public ProductionOrderBaseVO createOrder(ProductionOrderCreateDTO dto, Integer operatorId, String operatorName) {
        validateOrderType(dto.getOrderType());
        validateBoilingSourcesForOrderType(dto);
        ProductionOrder order = new ProductionOrder();
        order.setOrderNo(generateOrderNo(dto.getProductionDate()));
        order.setOrderType(dto.getOrderType());
        order.setStatus(ORDER_STATUS_ISSUED);
        order.setProductionDate(dto.getProductionDate());
        order.setPlannedMaterialJson(toJson(dto.getPlannedMaterialJson()));
        order.setPlannedOutputJson(toJson(dto.getPlannedOutputJson()));
        order.setTeamName(blankToNull(dto.getTeamName()));
        order.setRemark(blankToNull(dto.getRemark()));
        order.setCreatedBy(operatorId);
        order.setCreatedByName(operatorName);
        order.setCreatedAt(LocalDateTime.now());
        try {
            productionOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            order.setOrderNo(generateOrderNo(dto.getProductionDate()));
            productionOrderMapper.insert(order);
        }
        boilingBatchService.reserveForOrder(order, dto.getBoilingSources(), operatorId, operatorName);
        return toBaseVO(order);
    }

    @Override
    public ProductionOrderDetailVO getOrderDetail(Long id) {
        ProductionOrder order = requireOrder(id);
        List<ProductionMaterialVO> materials = materialMapper.listMaterials(id);
        List<ProductionOutputVO> outputs = outputMapper.listOutputs(id);
        List<ProductionOutputCodeVO> codes = outputCodeMapper.listCodesByOrder(id);
        Map<Long, List<ProductionOutputCodeVO>> codeMap = codes.stream()
                .collect(Collectors.groupingBy(ProductionOutputCodeVO::getOutputId));
        outputs.forEach(output -> output.setCodes(codeMap.getOrDefault(output.getId(), List.of())));

        ProductionOrderDetailVO detail = new ProductionOrderDetailVO();
        detail.setBaseInfo(toBaseVO(order));
        detail.setBoilingSources(boilingBatchService.listUsagesByOrder(id));
        detail.setMaterials(materials);
        detail.setOutputs(outputs);
        detail.setOutputCodes(codes);
        detail.setLabelBatches(listLabelBatches(id));
        return detail;
    }

    @Override
    public List<ProductionOrderOptionVO> listActiveOptions(String orderType) {
        return productionOrderMapper.listActiveOptions(blankToNull(orderType));
    }

    @Override
    public PageResult<ProductionMaterialCandidateVO> pageMaterialCandidates(Long orderId, ProductionMaterialCandidateQueryDTO query) {
        requireOrder(orderId);
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        List<ProductionMaterialCandidateVO> records = materialMapper.pageMaterialCandidates(query, (page - 1) * size, size);
        Long total = materialMapper.countMaterialCandidates(query);
        return new PageResult<>(total, records);
    }

    @Override
    public PageResult<ProductionMaterialVO> pageInProcessMaterials(ProductionInProcessMaterialQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        int offset = (page - 1) * size;
        List<ProductionMaterialVO> records = materialMapper.pageInProcessMaterials(query, offset, size);
        Long total = materialMapper.countInProcessMaterials(query);
        return new PageResult<>(total, records);
    }

    @Override
    @Transactional
    public void pickMaterials(Long orderId, ProductionMaterialPickDTO dto, Integer operatorId, String operatorName) {
        ProductionOrder order = requireOrderForUpdate(orderId);
        ensureOrderOpen(order);
        if (!"FINISH".equals(order.getOrderType())) {
            throw new BusinessException("只有成品生产订单需要领用半成品");
        }
        List<Integer> palletCodeIds = resolvePickPalletCodeIds(dto);
        if (palletCodeIds.isEmpty()) {
            throw new BusinessException("请选择要领用的半成品二维码");
        }
        LocalDateTime now = LocalDateTime.now();
        for (Integer palletCodeId : palletCodeIds) {
            PalletCode palletCode = requirePalletForUpdate(palletCodeId);
            Inventory inventory = requireInventoryForUpdate(palletCode.getId());
            Product product = requireProduct(inventory.getProductId());
            if (!"半成品".equals(inventory.getProductStatus()) || !"半成品".equals(product.getStatus())) {
                throw new BusinessException("只能领用在库半成品二维码");
            }
            Warehouse warehouse = inventory.getWarehouseId() == null ? null : warehouseMapper.selectByIdForUpdate(inventory.getWarehouseId());
            ProductionOrderMaterial material = buildMaterialSnapshot(order, palletCode, inventory, product, warehouse, operatorId, operatorName, now, dto.getRemark());
            materialMapper.insert(material);
            OutStock outStock = insertMaterialOutStock(inventory, product, operatorId, now);
            recordMaterialPickMovement(order, palletCode, inventory, product, outStock, operatorId);
            inventoryMapper.deleteInventoryById(inventory.getId());
            if (warehouse != null) {
                warehouseMapper.updateCurCapacity(warehouse.getId(), Math.max(0, safeInt(warehouse.getCurCapacity()) - 1));
            }
            insertMaterialPickFlow(order, palletCode, inventory, operatorId, dto.getRemark());
            releasePalletToFree(palletCode, operatorId);
        }
        if (ORDER_STATUS_ISSUED.equals(order.getStatus()) || ORDER_STATUS_MATERIALING.equals(order.getStatus())) {
            order.setStatus(ORDER_STATUS_MATERIALED);
        }
        order.setUpdatedAt(now);
        productionOrderMapper.updateById(order);
    }

    @Override
    @Transactional
    public void finishMaterials(Long orderId, Integer operatorId) {
        ProductionOrder order = requireOrderForUpdate(orderId);
        ensureOrderOpen(order);
        if (!ORDER_STATUS_MATERIALING.equals(order.getStatus()) && !ORDER_STATUS_ISSUED.equals(order.getStatus())) {
            throw new BusinessException("当前订单状态不能完成领料");
        }
        order.setStatus(ORDER_STATUS_MATERIALED);
        order.setUpdatedAt(LocalDateTime.now());
        productionOrderMapper.updateById(order);
    }

    @Override
    @Transactional
    public ProductionOutputVO addOutput(Long orderId, ProductionOutputCreateDTO dto, Integer operatorId) {
        ProductionOrder order = requireOrderForUpdate(orderId);
        ensureManualDraftOutputAllowed(order);
        ensureOrderOpen(order);
        Product product = requireProduct(dto.getProductId());
        validateOutputProductStatus(order, product);
        ProductionQuantitySplitVO split = splitQuantity(dto.getBoardCount(), dto.getPieceCount(), product.getPiecesPerPallet());

        ProductionOrderOutput output = new ProductionOrderOutput();
        output.setProductionOrderId(order.getId());
        output.setOrderNo(order.getOrderNo());
        output.setProductId(product.getId());
        output.setProductNameSnapshot(product.getProductName());
        output.setProductStatus(product.getStatus());
        output.setProductionDate(dto.getProductionDate());
        output.setBoardCount(safeInt(dto.getBoardCount()));
        output.setPieceCount(safeInt(dto.getPieceCount()));
        output.setTotalPieces(split.getTotalPieces());
        output.setPiecesPerPallet(product.getPiecesPerPallet());
        output.setWeightPerPiece(product.getWeightPerPiece());
        output.setTotalWeight(product.getWeightPerPiece().multiply(BigDecimal.valueOf(split.getTotalPieces())));
        output.setRequiredQrCount(split.getRequiredQrCount());
        output.setBoundQrCount(0);
        output.setInboundQrCount(0);
        output.setStatus("DRAFT");
        output.setCreatedBy(operatorId);
        output.setCreatedAt(LocalDateTime.now());
        output.setRemark(blankToNull(dto.getRemark()));
        outputMapper.insert(output);
        moveOrderToOutputBinding(order);
        return outputMapper.listOutputs(orderId).stream()
                .filter(item -> Objects.equals(item.getId(), output.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("产出创建失败"));
    }

    @Override
    @Transactional
    public ProductionOutputVO updateOutput(Long outputId, ProductionOutputCreateDTO dto, Integer operatorId) {
        ProductionOrderOutput output = requireOutputForUpdate(outputId);
        ensureOutputEditable(output);
        ProductionOrder order = requireOrderForUpdate(output.getProductionOrderId());
        ensureOrderOpen(order);
        Product product = requireProduct(dto.getProductId());
        validateOutputProductStatus(order, product);
        ProductionQuantitySplitVO split = splitQuantity(dto.getBoardCount(), dto.getPieceCount(), product.getPiecesPerPallet());

        output.setProductId(product.getId());
        output.setProductNameSnapshot(product.getProductName());
        output.setProductStatus(product.getStatus());
        output.setProductionDate(dto.getProductionDate());
        output.setBoardCount(safeInt(dto.getBoardCount()));
        output.setPieceCount(safeInt(dto.getPieceCount()));
        output.setTotalPieces(split.getTotalPieces());
        output.setPiecesPerPallet(product.getPiecesPerPallet());
        output.setWeightPerPiece(product.getWeightPerPiece());
        output.setTotalWeight(product.getWeightPerPiece().multiply(BigDecimal.valueOf(split.getTotalPieces())));
        output.setRequiredQrCount(split.getRequiredQrCount());
        output.setStatus("DRAFT");
        output.setUpdatedAt(LocalDateTime.now());
        output.setRemark(blankToNull(dto.getRemark()));
        outputMapper.updateById(output);
        moveOrderToOutputBinding(order);
        return outputMapper.listOutputs(order.getId()).stream()
                .filter(item -> Objects.equals(item.getId(), output.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("产出更新失败"));
    }

    @Override
    @Transactional
    public void deleteOutput(Long outputId, Integer operatorId) {
        ProductionOrderOutput output = requireOutputForUpdate(outputId);
        ensureOutputEditable(output);
        ProductionOrder order = requireOrderForUpdate(output.getProductionOrderId());
        ensureOrderOpen(order);
        output.setStatus("CANCELED");
        output.setUpdatedAt(LocalDateTime.now());
        outputMapper.updateById(output);
        refreshOrderStatusAfterOutputDraftChange(order.getId());
    }

    @Override
    @Transactional
    public void deleteOrder(Long orderId, Integer operatorId) {
        ProductionOrder order = requireOrderForUpdate(orderId);
        if (!ORDER_STATUS_ISSUED.equals(order.getStatus())) {
            throw new BusinessException("只有已下发状态的生产订单允许删除");
        }
        long materialCount = materialMapper.selectCount(new QueryWrapper<ProductionOrderMaterial>()
                .eq("production_order_id", orderId)
                .ne("status", "CANCELED"));
        long outputCount = outputMapper.selectCount(new QueryWrapper<ProductionOrderOutput>()
                .eq("production_order_id", orderId)
                .ne("status", "CANCELED"));
        if (materialCount > 0 || outputCount > 0) {
            throw new BusinessException("订单已有领用或产出记录，不能删除");
        }
        long labelCount = labelBatchMapper.selectCount(new QueryWrapper<ProductionOrderLabelBatch>()
                .eq("production_order_id", orderId)
                .ne("status", "CANCELED"));
        if (labelCount > 0) {
            throw new BusinessException("订单已有预打印批次，不能直接删除；请先取消订单并回收预留码");
        }
        boilingBatchService.releaseReservedByOrder(orderId);
        productionOrderMapper.deleteById(orderId);
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId, Integer operatorId) {
        ProductionOrder order = requireOrderForUpdate(orderId);
        ensureOrderOpen(order);
        List<ProductionOrderLabelCode> labelCodes = labelCodeMapper.listByOrderForUpdate(orderId);
        boolean hasUsed = labelCodes.stream().anyMatch(code -> "USED".equals(code.getStatus()));
        if (hasUsed) {
            throw new BusinessException("订单已有实际使用标签，不能直接取消，请走异常处理");
        }
        long materialCount = materialMapper.selectCount(new QueryWrapper<ProductionOrderMaterial>()
                .eq("production_order_id", orderId)
                .ne("status", "CANCELED"));
        long outputCount = outputMapper.selectCount(new QueryWrapper<ProductionOrderOutput>()
                .eq("production_order_id", orderId)
                .ne("status", "CANCELED"));
        if (materialCount > 0 || outputCount > 0) {
            throw new BusinessException("订单已有领用或实际产出，不能直接取消，请走异常处理");
        }
        boilingBatchService.releaseReservedByOrder(orderId);
        recycleRemainingReservedLabels(orderId, operatorId, "订单取消，回收全部未使用预打印标签");
        List<ProductionOrderLabelBatch> batches = labelBatchMapper.listByOrderForUpdate(orderId);
        LocalDateTime now = LocalDateTime.now();
        for (ProductionOrderLabelBatch batch : batches) {
            batch.setStatus("CANCELED");
            batch.setClosedAt(now);
            batch.setUpdatedAt(now);
            labelBatchMapper.updateById(batch);
        }
        order.setStatus(ORDER_STATUS_CANCELED);
        order.setUpdatedAt(now);
        productionOrderMapper.updateById(order);
    }

    @Override
    @Transactional
    public ProductionBindQrResultVO bindFixedQrs(Long outputId, ProductionOutputBindQrDTO dto, Integer operatorId) {
        ProductionOrderOutput output = requireOutputForUpdate(outputId);
        ProductionOrder order = requireOrderForUpdate(output.getProductionOrderId());
        ensureOrderOpen(order);
        throw new BusinessException("生产订单已启用预打印流程，请先预分配订单码，再确认生产结束自动核销并创建入库任务");
    }

    @Override
    @Transactional
    public List<ProductionOutputCodeVO> markOutputPrinted(Long outputId) {
        requireOutputForUpdate(outputId);
        outputCodeMapper.markPrintedByOutput(outputId);
        return outputCodeMapper.listCodesByOutput(outputId);
    }

    @Override
    @Transactional
    public List<ProductionLabelBatchVO> reserveLabels(Long orderId, ProductionLabelReserveDTO dto, Integer operatorId) {
        ProductionOrder order = requireOrderForUpdate(orderId);
        ensureOrderOpen(order);
        long finishedLabelCount = labelCodeMapper.selectCount(new QueryWrapper<ProductionOrderLabelCode>()
                .eq("production_order_id", orderId)
                .in("status", List.of("USED", "RECYCLED")));
        if (finishedLabelCount > 0) {
            throw new BusinessException("订单已确认生产结束，不能继续预分配标签");
        }
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException("预打印产品不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        for (ProductionLabelReserveDTO.Item item : dto.getItems()) {
            Product product = requireProduct(item.getProductId());
            validateOutputProductStatus(order, product);
            int qrCount = resolveReserveQrCount(item, product);
            List<PalletCode> palletCodes = labelCodeMapper.selectFreeFixedCodesForUpdate(product.getId(), qrCount);
            if (palletCodes.size() < qrCount) {
                throw new BusinessException(product.getProductName() + " 可用固定产品二维码不足，当前 "
                        + palletCodes.size() + " 个，需要 " + qrCount + " 个");
            }
            ProductionOrderLabelBatch batch = new ProductionOrderLabelBatch();
            batch.setProductionOrderId(order.getId());
            batch.setOrderNo(order.getOrderNo());
            batch.setBatchNo(generateLabelBatchNo(order.getId(), order.getOrderNo()));
            batch.setProductId(product.getId());
            batch.setProductNameSnapshot(product.getProductName());
            batch.setReservedCount(qrCount);
            batch.setUsedCount(0);
            batch.setRecycledCount(0);
            batch.setStatus("RESERVED");
            batch.setCreatedBy(operatorId);
            batch.setCreatedAt(now);
            batch.setRemark(blankToNull(dto.getRemark()));
            labelBatchMapper.insert(batch);
            for (int i = 0; i < palletCodes.size(); i++) {
                PalletCode palletCode = palletCodes.get(i);
                reservePalletCode(order, product, palletCode, operatorId, now, dto.getRemark());
                ProductionOrderLabelCode labelCode = new ProductionOrderLabelCode();
                labelCode.setProductionOrderId(order.getId());
                labelCode.setOrderNo(order.getOrderNo());
                labelCode.setBatchId(batch.getId());
                labelCode.setBatchNo(batch.getBatchNo());
                labelCode.setSequenceNo(i + 1);
                labelCode.setPalletCodeId(palletCode.getId());
                labelCode.setPalletCode(palletCode.getCode());
                labelCode.setProductId(product.getId());
                labelCode.setProductNameSnapshot(product.getProductName());
                labelCode.setLabelToken(UUID.randomUUID().toString().replace("-", ""));
                labelCode.setQrContent("PENDING");
                labelCode.setStatus("RESERVED");
                labelCode.setCreatedAt(now);
                labelCode.setRemark(blankToNull(dto.getRemark()));
                labelCodeMapper.insert(labelCode);
                labelCode.setQrContent(buildLabelQrContent(labelCode));
                labelCodeMapper.updateById(labelCode);
            }
        }
        if (!ORDER_STATUS_WAIT_INBOUND.equals(order.getStatus()) && !ORDER_STATUS_PART_INBOUND.equals(order.getStatus())) {
            order.setStatus(ORDER_STATUS_PREPRINTED);
            order.setUpdatedAt(now);
            productionOrderMapper.updateById(order);
        }
        return listLabelBatches(orderId);
    }

    @Override
    public List<ProductionLabelBatchVO> listLabelBatches(Long orderId) {
        List<ProductionLabelBatchVO> batches = labelBatchMapper.listBatchVOByOrder(orderId);
        if (batches.isEmpty()) {
            return batches;
        }
        Map<Long, List<ProductionLabelCodeVO>> codeMap = labelCodeMapper.listCodeVOByOrder(orderId).stream()
                .collect(Collectors.groupingBy(ProductionLabelCodeVO::getBatchId));
        batches.forEach(batch -> batch.setCodes(codeMap.getOrDefault(batch.getId(), List.of())));
        return batches;
    }

    @Override
    @Transactional
    public byte[] printLabelBatch(Long batchId) {
        ProductionOrderLabelBatch batch = labelBatchMapper.selectOne(new QueryWrapper<ProductionOrderLabelBatch>()
                .eq("id", batchId)
                .last("limit 1 for update"));
        if (batch == null) {
            throw new BusinessException("预打印批次不存在");
        }
        if ("CLOSED".equals(batch.getStatus()) || "CANCELED".equals(batch.getStatus())) {
            throw new BusinessException("预打印批次已失效，不能打印");
        }
        List<ProductionOrderLabelCode> codes = labelCodeMapper.selectList(new QueryWrapper<ProductionOrderLabelCode>()
                .eq("batch_id", batchId)
                .orderByAsc("sequence_no"));
        if (codes.isEmpty()) {
            throw new BusinessException("预打印批次没有二维码");
        }
        labelCodeMapper.markBatchPrinted(batchId);
        List<PalletQrLabelPdfRenderer.LabelPayload> labels = codes.stream()
                .map(code -> new PalletQrLabelPdfRenderer.LabelPayload(
                        code.getQrContent(),
                        batch.getProductNameSnapshot() + " " + batch.getBatchNo() + "-" + code.getSequenceNo(),
                        code.getPalletCode()))
                .toList();
        try {
            return PalletQrLabelPdfRenderer.renderA4LabelsWithTitle(labels);
        } catch (Exception e) {
            throw new BusinessException("生成订单标签PDF失败");
        }
    }

    @Override
    public byte[] getLabelCodeQrPng(Long labelCodeId) {
        ProductionOrderLabelCode labelCode = labelCodeMapper.selectById(labelCodeId);
        if (labelCode == null) {
            throw new BusinessException("预打印订单码不存在");
        }
        if (labelCode.getQrContent() == null || labelCode.getQrContent().isBlank()) {
            throw new BusinessException("预打印订单码内容为空");
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(QrCodeUtils.generateQrCode(labelCode.getQrContent(), 512, 512), "PNG", output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("生成预打印订单二维码失败");
        }
    }
    @Override
    @Transactional
    public ProductionOrderDetailVO finishProduction(Long orderId, ProductionFinishDTO dto, Integer operatorId) {
        ProductionOrder order = requireOrderForUpdate(orderId);
        ensureOrderOpen(order);
        labelBatchMapper.listByOrderForUpdate(orderId);
        List<ProductionOrderLabelCode> allLabelCodes = labelCodeMapper.listByOrderForUpdate(orderId);
        boolean hasConfirmedLabels = allLabelCodes.stream().anyMatch(code ->
                "USED".equals(code.getStatus()) || "RECYCLED".equals(code.getStatus()));
        if (hasConfirmedLabels) {
            return getOrderDetail(orderId);
        }
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException("实际产出不能为空");
        }
        Map<Integer, Integer> requiredByProduct = new LinkedHashMap<>();
        Map<Integer, Product> productMap = new LinkedHashMap<>();
        for (ProductionFinishDTO.Item item : dto.getItems()) {
            Product product = requireProduct(item.getProductId());
            validateOutputProductStatus(order, product);
            ProductionQuantitySplitVO split = splitQuantity(item.getBoardCount(), item.getPieceCount(), product.getPiecesPerPallet());
            requiredByProduct.merge(product.getId(), split.getRequiredQrCount(), Integer::sum);
            productMap.put(product.getId(), product);
        }
        Map<Integer, List<ProductionOrderLabelCode>> reservedByProduct = new LinkedHashMap<>();
        for (Integer productId : requiredByProduct.keySet()) {
            List<ProductionOrderLabelCode> reserved = labelCodeMapper.listReservedByProductForUpdate(orderId, productId);
            if (reserved.size() < requiredByProduct.get(productId)) {
                Product product = productMap.get(productId);
                throw new BusinessException(product.getProductName() + " 预打印标签不足，实际需要 "
                        + requiredByProduct.get(productId) + " 个，当前可用 " + reserved.size() + " 个");
            }
            reservedByProduct.put(productId, reserved);
        }
        LocalDateTime now = LocalDateTime.now();
        Map<Integer, Integer> usedOffsetByProduct = new LinkedHashMap<>();
        for (ProductionFinishDTO.Item item : dto.getItems()) {
            Product product = productMap.get(item.getProductId());
            ProductionOrderOutput output = createOutputEntity(order, product,
                    item.getProductionDate() == null ? order.getProductionDate() : item.getProductionDate(),
                    item.getBoardCount(), item.getPieceCount(), operatorId,
                    appendRemark(dto.getRemark(), "确认生产结束生成实际产出"));
            outputMapper.insert(output);
            ProductionQuantitySplitVO split = splitQuantity(output.getBoardCount(), output.getPieceCount(), product.getPiecesPerPallet());
            int offset = usedOffsetByProduct.getOrDefault(product.getId(), 0);
            List<ProductionOrderLabelCode> selectedLabels = reservedByProduct.get(product.getId())
                    .subList(offset, offset + safeInt(output.getRequiredQrCount()));
            for (int i = 0; i < selectedLabels.size(); i++) {
                ProductionOrderLabelCode labelCode = selectedLabels.get(i);
                PalletCode palletCode = requirePalletForUpdate(labelCode.getPalletCodeId());
                PalletTask task = createInboundTaskFromReserved(order, output, product, palletCode, operatorId, dto.getRemark(), now);
                boolean isBoardCode = i < safeInt(split.getFinalBoardCount());
                ProductionOrderOutputCode outputCode = buildOutputCodeFromLabel(order, output, product, labelCode,
                        palletCode, task, isBoardCode, now, dto.getRemark());
                outputCodeMapper.insert(outputCode);
                labelCodeMapper.markUsed(labelCode.getId(), outputCode.getId());
            }
            outputMapper.refreshOutputProgress(output.getId());
            usedOffsetByProduct.put(product.getId(), offset + safeInt(output.getRequiredQrCount()));
        }
        recycleRemainingReservedLabels(orderId, operatorId, dto.getRemark());
        labelBatchMapper.listByOrderForUpdate(orderId).forEach(batch -> labelBatchMapper.refreshBatchProgress(batch.getId()));
        refreshOrderProgress(orderId);
        return getOrderDetail(orderId);
    }

    @Override
    public ProductionQuantitySplitVO splitQuantity(Integer boardCount, Integer pieceCount, Integer piecesPerPallet) {
        int boards = safeInt(boardCount);
        int pieces = safeInt(pieceCount);
        int perPallet = safeInt(piecesPerPallet);
        if (boards < 0 || pieces < 0) {
            throw new BusinessException("板数和件数不能为负数");
        }
        if (perPallet <= 0) {
            throw new BusinessException("产品未配置每板件数，无法计算二维码数量");
        }
        int finalBoards = boards + pieces / perPallet;
        int finalPieces = pieces % perPallet;
        int totalPieces = finalBoards * perPallet + finalPieces;
        if (totalPieces <= 0) {
            throw new BusinessException("产出数量必须大于0");
        }
        int requiredQrCount = finalBoards + (finalPieces > 0 ? 1 : 0);
        return new ProductionQuantitySplitVO(finalBoards, finalPieces, totalPieces, requiredQrCount);
    }

    private int resolveReserveQrCount(ProductionLabelReserveDTO.Item item, Product product) {
        if (item.getQrCount() != null && item.getQrCount() > 0) {
            return item.getQrCount();
        }
        return splitQuantity(item.getBoardCount(), item.getPieceCount(), product.getPiecesPerPallet()).getRequiredQrCount();
    }

    private ProductionOrderOutput createOutputEntity(ProductionOrder order, Product product, LocalDate productionDate,
                                                     Integer boardCount, Integer pieceCount, Integer operatorId,
                                                     String remark) {
        ProductionQuantitySplitVO split = splitQuantity(boardCount, pieceCount, product.getPiecesPerPallet());
        ProductionOrderOutput output = new ProductionOrderOutput();
        output.setProductionOrderId(order.getId());
        output.setOrderNo(order.getOrderNo());
        output.setProductId(product.getId());
        output.setProductNameSnapshot(product.getProductName());
        output.setProductStatus(product.getStatus());
        output.setProductionDate(productionDate);
        output.setBoardCount(safeInt(boardCount));
        output.setPieceCount(safeInt(pieceCount));
        output.setTotalPieces(split.getTotalPieces());
        output.setPiecesPerPallet(product.getPiecesPerPallet());
        output.setWeightPerPiece(product.getWeightPerPiece());
        output.setTotalWeight(product.getWeightPerPiece().multiply(BigDecimal.valueOf(split.getTotalPieces())));
        output.setRequiredQrCount(split.getRequiredQrCount());
        output.setBoundQrCount(0);
        output.setInboundQrCount(0);
        output.setStatus("DRAFT");
        output.setCreatedBy(operatorId);
        output.setCreatedAt(LocalDateTime.now());
        output.setRemark(blankToNull(remark));
        return output;
    }

    @Override
    @Transactional
    public void syncInboundByTask(Integer palletTaskId, Integer inventoryId) {
        ProductionOrderOutputCode code = outputCodeMapper.selectByTaskId(palletTaskId);
        if (code == null) {
            return;
        }
        outputCodeMapper.markInstock(code.getId(), inventoryId);
        outputMapper.refreshOutputProgress(code.getOutputId());
        refreshOrderProgress(code.getProductionOrderId());
    }

    private ProductionOrderPageVO toPageVO(ProductionOrder order) {
        ProductionOrderPageVO vo = new ProductionOrderPageVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setOrderType(order.getOrderType());
        vo.setStatus(order.getStatus());
        vo.setProductionDate(order.getProductionDate());
        vo.setCreatedByName(order.getCreatedByName());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setTeamName(order.getTeamName());
        vo.setRemark(order.getRemark());
        vo.setPlannedMaterialText(jsonPlanText(order.getPlannedMaterialJson()));
        vo.setPlannedOutputText(jsonPlanText(order.getPlannedOutputJson()));
        List<ProductionMaterialVO> materials = materialMapper.listMaterials(order.getId());
        List<ProductionOutputVO> outputs = outputMapper.listOutputs(order.getId());
        int bound = outputs.stream().mapToInt(item -> safeInt(item.getBoundQrCount())).sum();
        int inbound = outputs.stream().mapToInt(item -> safeInt(item.getInboundQrCount())).sum();
        int required = outputs.stream().mapToInt(item -> safeInt(item.getRequiredQrCount())).sum();
        vo.setActualMaterialText(materialsText(materials));
        vo.setOutputText(outputsText(outputs));
        vo.setActualMaterialCount(materials.size());
        vo.setOutputCount(outputs.size());
        vo.setBoundQrCount(bound);
        vo.setInboundQrCount(inbound);
        vo.setRequiredQrCount(required);
        vo.setInboundProgress(inbound + "/" + bound);
        return vo;
    }

    private ProductionOrderBaseVO toBaseVO(ProductionOrder order) {
        ProductionOrderBaseVO vo = new ProductionOrderBaseVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setOrderType(order.getOrderType());
        vo.setStatus(order.getStatus());
        vo.setProductionDate(order.getProductionDate());
        vo.setPlannedMaterialJson(fromJson(order.getPlannedMaterialJson()));
        vo.setPlannedOutputJson(fromJson(order.getPlannedOutputJson()));
        vo.setPlannedMaterialText(jsonPlanText(order.getPlannedMaterialJson()));
        vo.setPlannedOutputText(jsonPlanText(order.getPlannedOutputJson()));
        vo.setTeamName(order.getTeamName());
        vo.setRemark(order.getRemark());
        vo.setCreatedBy(order.getCreatedBy());
        vo.setCreatedByName(order.getCreatedByName());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setUpdatedAt(order.getUpdatedAt());
        vo.setCompletedAt(order.getCompletedAt());
        return vo;
    }

    private String generateOrderNo(LocalDate productionDate) {
        String prefix = "PO" + productionDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        String latest = productionOrderMapper.selectLatestOrderNoForUpdate(prefix);
        int next = 1;
        if (latest != null && latest.length() >= prefix.length() + 4) {
            next = Integer.parseInt(latest.substring(prefix.length())) + 1;
        }
        return prefix + String.format("%04d", next);
    }

    private String generateLabelBatchNo(Long orderId, String orderNo) {
        String latest = labelBatchMapper.selectLatestBatchNoForUpdate(orderId);
        int next = 1;
        if (latest != null && latest.contains("-LB")) {
            String suffix = latest.substring(latest.lastIndexOf("-LB") + 3);
            next = Integer.parseInt(suffix) + 1;
        }
        return orderNo + "-LB" + String.format("%03d", next);
    }

    private ProductionOrderMaterial buildMaterialSnapshot(ProductionOrder order, PalletCode palletCode, Inventory inventory,
                                                          Product product, Warehouse warehouse, Integer operatorId,
                                                          String operatorName, LocalDateTime now, String remark) {
        int pieces = safeInt(inventory.getPieces());
        String unit = pieces > 0 ? "1" : "0";
        int quantity = pieces > 0 ? pieces : Math.max(1, safeInt(inventory.getQuantity()));
        int totalPieces = "1".equals(unit) ? quantity : quantity * safeInt(product.getPiecesPerPallet());
        ProductionOrderMaterial material = new ProductionOrderMaterial();
        material.setProductionOrderId(order.getId());
        material.setOrderNo(order.getOrderNo());
        material.setPalletCodeId(palletCode.getId());
        material.setPalletCode(palletCode.getCode());
        material.setPalletCycleNo(palletCode.getCurrentCycleNo());
        material.setInventoryId(inventory.getId());
        material.setProductId(product.getId());
        material.setProductNameSnapshot(product.getProductName());
        material.setProductStatus(product.getStatus());
        material.setProductionDate(palletCode.getProductionDate() != null ? palletCode.getProductionDate() : inventory.getEntryDate());
        material.setWarehouseId(inventory.getWarehouseId());
        material.setWarehouseNameSnapshot(warehouse == null ? null : warehouse.getWarehouseName());
        material.setSide(inventory.getSide());
        material.setRowNumber(inventory.getRowNumber());
        material.setLayer(inventory.getLayer());
        material.setQuantity(quantity);
        material.setUnit(unit);
        material.setPieces(pieces);
        material.setPiecesPerPallet(product.getPiecesPerPallet());
        material.setTotalPieces(totalPieces);
        material.setWeightPerPiece(product.getWeightPerPiece());
        material.setTotalWeight(product.getWeightPerPiece().multiply(BigDecimal.valueOf(totalPieces)));
        material.setStatus("PICKED");
        material.setPickedBy(operatorId);
        material.setPickedByName(operatorName);
        material.setPickedAt(now);
        material.setRemark(blankToNull(remark));
        return material;
    }

    private PalletTask createInboundTask(ProductionOrder order, ProductionOrderOutput output, Product product,
                                         PalletCode palletCode, Integer operatorId, String remark, LocalDateTime now) {
        if (!Boolean.TRUE.equals(palletCode.getFixedModeEnabled()) || !Objects.equals(palletCode.getFixedProductId(), output.getProductId())) {
            throw new BusinessException("二维码不是当前产品的固定产品二维码");
        }
        if (!"FREE".equalsIgnoreCase(palletCode.getStatus())) {
            throw new BusinessException("二维码 " + palletCode.getCode() + " 当前不是空闲状态");
        }
        int currentCycleNo = safeInt(palletCode.getCurrentCycleNo());
        if (palletTaskMapper.countPendingInTasks(palletCode.getId(), currentCycleNo) > 0) {
            throw new BusinessException("二维码 " + palletCode.getCode() + " 已存在待入库任务");
        }
        if (palletTaskMapper.countPendingOutTasks(palletCode.getId(), currentCycleNo) > 0
                || palletTaskMapper.countPendingTransferTasks(palletCode.getId(), currentCycleNo) > 0) {
            throw new BusinessException("二维码 " + palletCode.getCode() + " 存在未完成任务，不能分配给生产订单");
        }
        int nextCycle = currentCycleNo + 1;
        PalletTask task = new PalletTask();
        task.setPalletCodeId(palletCode.getId());
        task.setTaskType("半成品".equals(product.getStatus()) ? "SEMI_IN" : "FINISH_IN");
        task.setStatus("PENDING");
        task.setProductId(product.getId());
        task.setProductStatus(product.getStatus());
        task.setProductionDate(output.getProductionDate());
        task.setScreenMeshId(product.getScreenMeshId());
        task.setCreatedBy(operatorId);
        task.setCreatedAt(now);
        task.setRemark(appendRemark(remark, "生产订单[" + order.getOrderNo() + "]产出贴码"));
        task.setCycleNo(nextCycle);
        palletTaskMapper.insert(task);

        palletCode.setStatus("PENDING");
        palletCode.setProductId(product.getId());
        palletCode.setProductStatus(product.getStatus());
        palletCode.setProductionDate(output.getProductionDate());
        palletCode.setScreenMeshId(product.getScreenMeshId());
        palletCode.setCurrentCycleNo(nextCycle);
        palletCode.setUpdatedBy(operatorId);
        palletCode.setUpdatedAt(now);
        palletCodeMapper.updateById(palletCode);

        PalletFlowRecord flow = new PalletFlowRecord();
        flow.setPalletCodeId(palletCode.getId());
        flow.setTaskId(task.getId());
        flow.setOperationType("ORDER_OUTPUT_BIND");
        flow.setOperationName("生产订单产出分配二维码");
        flow.setOperationTime(now);
        flow.setOperatorId(operatorId);
        flow.setProductId(product.getId());
        flow.setProductStatus(product.getStatus());
        flow.setCycleNo(nextCycle);
        flow.setRemark("生产订单[" + order.getOrderNo() + "]产出行ID=" + output.getId());
        palletFlowRecordMapper.insert(flow);
        return task;
    }

    private void reservePalletCode(ProductionOrder order, Product product, PalletCode palletCode,
                                   Integer operatorId, LocalDateTime now, String remark) {
        if (!Boolean.TRUE.equals(palletCode.getFixedModeEnabled()) || !Objects.equals(palletCode.getFixedProductId(), product.getId())) {
            throw new BusinessException("二维码不是当前产品的固定产品二维码");
        }
        if (!"FREE".equalsIgnoreCase(palletCode.getStatus())) {
            throw new BusinessException("二维码 " + palletCode.getCode() + " 当前不是空闲状态");
        }
        int currentCycleNo = safeInt(palletCode.getCurrentCycleNo());
        if (palletTaskMapper.countPendingInTasks(palletCode.getId(), currentCycleNo) > 0
                || palletTaskMapper.countPendingOutTasks(palletCode.getId(), currentCycleNo) > 0
                || palletTaskMapper.countPendingTransferTasks(palletCode.getId(), currentCycleNo) > 0) {
            throw new BusinessException("二维码 " + palletCode.getCode() + " 存在未完成任务，不能预分配");
        }
        palletCode.setStatus("ORDER_RESERVED");
        palletCode.setProductId(product.getId());
        palletCode.setProductStatus(product.getStatus());
        palletCode.setScreenMeshId(product.getScreenMeshId());
        palletCode.setUpdatedBy(operatorId);
        palletCode.setUpdatedAt(now);
        palletCodeMapper.updateById(palletCode);

        PalletFlowRecord flow = new PalletFlowRecord();
        flow.setPalletCodeId(palletCode.getId());
        flow.setOperationType("ORDER_LABEL_RESERVE");
        flow.setOperationName("生产订单预分配标签");
        flow.setOperationTime(now);
        flow.setOperatorId(operatorId);
        flow.setProductId(product.getId());
        flow.setProductStatus(product.getStatus());
        flow.setCycleNo(currentCycleNo);
        flow.setRemark(appendRemark(remark, "生产订单[" + order.getOrderNo() + "]预打印标签"));
        palletFlowRecordMapper.insert(flow);
    }

    private PalletTask createInboundTaskFromReserved(ProductionOrder order, ProductionOrderOutput output, Product product,
                                                     PalletCode palletCode, Integer operatorId, String remark, LocalDateTime now) {
        if (!"ORDER_RESERVED".equalsIgnoreCase(palletCode.getStatus())) {
            throw new BusinessException("二维码 " + palletCode.getCode() + " 当前不是订单预分配状态");
        }
        int currentCycleNo = safeInt(palletCode.getCurrentCycleNo());
        if (palletTaskMapper.countPendingInTasks(palletCode.getId(), currentCycleNo) > 0
                || palletTaskMapper.countPendingOutTasks(palletCode.getId(), currentCycleNo) > 0
                || palletTaskMapper.countPendingTransferTasks(palletCode.getId(), currentCycleNo) > 0) {
            throw new BusinessException("二维码 " + palletCode.getCode() + " 存在未完成任务，不能核销为实际产出");
        }
        int nextCycle = currentCycleNo + 1;
        PalletTask task = new PalletTask();
        task.setPalletCodeId(palletCode.getId());
        task.setTaskType("半成品".equals(product.getStatus()) ? "SEMI_IN" : "FINISH_IN");
        task.setStatus("PENDING");
        task.setProductId(product.getId());
        task.setProductStatus(product.getStatus());
        task.setProductionDate(output.getProductionDate());
        task.setScreenMeshId(product.getScreenMeshId());
        task.setCreatedBy(operatorId);
        task.setCreatedAt(now);
        task.setRemark(appendRemark(remark, "生产订单[" + order.getOrderNo() + "]确认生产结束核销标签"));
        task.setCycleNo(nextCycle);
        palletTaskMapper.insert(task);

        palletCode.setStatus("PENDING");
        palletCode.setProductId(product.getId());
        palletCode.setProductStatus(product.getStatus());
        palletCode.setProductionDate(output.getProductionDate());
        palletCode.setScreenMeshId(product.getScreenMeshId());
        palletCode.setCurrentCycleNo(nextCycle);
        palletCode.setUpdatedBy(operatorId);
        palletCode.setUpdatedAt(now);
        palletCodeMapper.updateById(palletCode);

        PalletFlowRecord flow = new PalletFlowRecord();
        flow.setPalletCodeId(palletCode.getId());
        flow.setTaskId(task.getId());
        flow.setOperationType("ORDER_LABEL_USED");
        flow.setOperationName("生产订单标签核销");
        flow.setOperationTime(now);
        flow.setOperatorId(operatorId);
        flow.setProductId(product.getId());
        flow.setProductStatus(product.getStatus());
        flow.setCycleNo(nextCycle);
        flow.setRemark("生产订单[" + order.getOrderNo() + "]确认实际产出，产出行ID=" + output.getId());
        palletFlowRecordMapper.insert(flow);
        return task;
    }

    private ProductionOrderOutputCode buildOutputCode(ProductionOrder order, ProductionOrderOutput output, Product product,
                                                      PalletCode palletCode, PalletTask task, int sequence,
                                                      LocalDateTime now, String remark) {
        ProductionQuantitySplitVO split = splitQuantity(output.getBoardCount(), output.getPieceCount(), product.getPiecesPerPallet());
        boolean isBoardCode = sequence < safeInt(split.getFinalBoardCount());
        ProductionOrderOutputCode code = new ProductionOrderOutputCode();
        code.setProductionOrderId(order.getId());
        code.setOrderNo(order.getOrderNo());
        code.setOutputId(output.getId());
        code.setPalletCodeId(palletCode.getId());
        code.setPalletCode(palletCode.getCode());
        code.setPalletCycleNo(task.getCycleNo());
        code.setProductId(product.getId());
        code.setProductNameSnapshot(product.getProductName());
        code.setQuantity(isBoardCode ? 1 : safeInt(split.getFinalPieces()));
        code.setUnit(isBoardCode ? "0" : "1");
        code.setPieces(isBoardCode ? 0 : safeInt(split.getFinalPieces()));
        code.setPalletTaskId(task.getId());
        code.setStatus("PENDING_INBOUND");
        code.setCreatedAt(now);
        code.setRemark(blankToNull(remark));
        return code;
    }

    private ProductionOrderOutputCode buildOutputCodeFromLabel(ProductionOrder order, ProductionOrderOutput output, Product product,
                                                               ProductionOrderLabelCode labelCode, PalletCode palletCode,
                                                               PalletTask task, boolean isBoardCode,
                                                               LocalDateTime now, String remark) {
        ProductionQuantitySplitVO split = splitQuantity(output.getBoardCount(), output.getPieceCount(), product.getPiecesPerPallet());
        ProductionOrderOutputCode code = new ProductionOrderOutputCode();
        code.setProductionOrderId(order.getId());
        code.setOrderNo(order.getOrderNo());
        code.setOutputId(output.getId());
        code.setPalletCodeId(palletCode.getId());
        code.setLabelCodeId(labelCode.getId());
        code.setPalletCode(palletCode.getCode());
        code.setPalletCycleNo(task.getCycleNo());
        code.setProductId(product.getId());
        code.setProductNameSnapshot(product.getProductName());
        code.setQuantity(isBoardCode ? 1 : safeInt(split.getFinalPieces()));
        code.setUnit(isBoardCode ? "0" : "1");
        code.setPieces(isBoardCode ? 0 : safeInt(split.getFinalPieces()));
        code.setPalletTaskId(task.getId());
        code.setStatus("PENDING_INBOUND");
        code.setCreatedAt(now);
        code.setRemark(blankToNull(remark));
        return code;
    }

    private void recycleRemainingReservedLabels(Long orderId, Integer operatorId, String remark) {
        List<ProductionOrderLabelCode> allCodes = labelCodeMapper.listByOrderForUpdate(orderId);
        LocalDateTime now = LocalDateTime.now();
        for (ProductionOrderLabelCode labelCode : allCodes) {
            if (!"RESERVED".equals(labelCode.getStatus())) {
                continue;
            }
            PalletCode palletCode = requirePalletForUpdate(labelCode.getPalletCodeId());
            if (!"ORDER_RESERVED".equalsIgnoreCase(palletCode.getStatus())) {
                throw new BusinessException("二维码 " + palletCode.getCode() + " 状态异常，无法回收预打印标签");
            }
            labelCodeMapper.markRecycled(labelCode.getId());
            String productStatus = palletCode.getProductStatus();
            palletCode.setStatus("FREE");
            palletCode.setProductId(null);
            palletCode.setProductStatus(null);
            palletCode.setProductionDate(null);
            palletCode.setScreenMeshId(null);
            palletCode.setAssayId(null);
            palletCode.setUpdatedBy(operatorId);
            palletCode.setUpdatedAt(now);
            palletCodeMapper.updateById(palletCode);

            PalletFlowRecord flow = new PalletFlowRecord();
            flow.setPalletCodeId(palletCode.getId());
            flow.setOperationType("ORDER_LABEL_RECYCLE");
            flow.setOperationName("生产订单未用标签回收");
            flow.setOperationTime(now);
            flow.setOperatorId(operatorId);
            flow.setProductId(labelCode.getProductId());
            flow.setProductStatus(productStatus);
            flow.setCycleNo(palletCode.getCurrentCycleNo());
            flow.setRemark(appendRemark(remark, "生产订单[" + labelCode.getOrderNo() + "]结束，未扫码入库标签自动回收"));
            palletFlowRecordMapper.insert(flow);
        }
    }

    private String buildLabelQrContent(ProductionOrderLabelCode labelCode) {
        return "LB|productionOrderId=" + labelCode.getProductionOrderId()
                + "|batchId=" + labelCode.getBatchId()
                + "|labelCodeId=" + labelCode.getId()
                + "|token=" + labelCode.getLabelToken();
    }

    private OutStock insertMaterialOutStock(Inventory inventory, Product product,
                                            Integer operatorId, LocalDateTime occurredAt) {
        int pieces = safeInt(inventory.getPieces());
        int quantity = pieces > 0 ? 0 : Math.max(1, safeInt(inventory.getQuantity()));
        OutStock outStock = new OutStock();
        outStock.setWarehouseId(inventory.getWarehouseId());
        outStock.setProductId(inventory.getProductId());
        outStock.setQuantity(quantity);
        outStock.setPieces(pieces);
        outStock.setOutType(0);
        outStock.setUnit(pieces > 0 ? "1" : "0");
        outStock.setInDate(inventory.getEntryDate());
        outStock.setOutDate(LocalDate.now());
        outStock.setOperatorId(operatorId);
        outStock.setAssayId(inventory.getAssayId());
        outStock.setCreatedAt(occurredAt);
        int totalPieces = pieces > 0 ? pieces : quantity * safeInt(product.getPiecesPerPallet());
        outStock.setTotalWeight(product.getWeightPerPiece().multiply(BigDecimal.valueOf(totalPieces)));
        outStockMapper.insert(outStock);
        return outStock;
    }

    private void recordMaterialPickMovement(ProductionOrder order, PalletCode palletCode, Inventory inventory,
                                            Product product, OutStock outStock, Integer operatorId) {
        int loosePieces = outStock.getPieces() == null ? 0 : outStock.getPieces();
        int boards = loosePieces > 0 ? 0 : Math.max(0, outStock.getQuantity());
        int totalPieces = loosePieces > 0 ? loosePieces : boards * product.getPiecesPerPallet();
        stockMovementEventService.record(StockMovementEventCommand.builder()
                .eventType("OUTBOUND")
                .sourceType("OUT_STOCK")
                .sourceRecordId(outStock.getId().longValue())
                .occurredAt(outStock.getCreatedAt())
                .productId(product.getId())
                .productStatus(inventory.getProductStatus())
                .productionDate(palletCode.getProductionDate() == null
                        ? inventory.getEntryDate()
                        : palletCode.getProductionDate())
                .fromWarehouseId(inventory.getWarehouseId())
                .palletCodeId(palletCode.getId())
                .boardQuantity(boards)
                .loosePieceQuantity(loosePieces)
                .totalPieces(totalPieces)
                .totalWeightKg(outStock.getTotalWeight())
                .operatorId(operatorId)
                .actionKind("PRODUCTION_MATERIAL_PICK")
                .businessActionId("production_order_" + order.getId() + "_material_pick")
                .build());
    }

    private void insertMaterialPickFlow(ProductionOrder order, PalletCode palletCode, Inventory inventory,
                                        Integer operatorId, String remark) {
        PalletFlowRecord flow = new PalletFlowRecord();
        flow.setPalletCodeId(palletCode.getId());
        flow.setOperationType("ORDER_MATERIAL_PICK");
        flow.setOperationName("生产订单领用");
        flow.setOperationTime(LocalDateTime.now());
        flow.setOperatorId(operatorId);
        flow.setProductId(palletCode.getProductId());
        flow.setProductStatus(palletCode.getProductStatus());
        flow.setAssayId(palletCode.getAssayId());
        flow.setFromWarehouseId(inventory.getWarehouseId());
        flow.setFromSide(inventory.getSide());
        flow.setFromRowNumber(inventory.getRowNumber());
        flow.setFromLayer(inventory.getLayer());
        flow.setCycleNo(palletCode.getCurrentCycleNo());
        flow.setRemark(appendRemark(remark, "生产订单[" + order.getOrderNo() + "]领用，库存已移出，二维码已释放"));
        palletFlowRecordMapper.insert(flow);
    }

    private void releasePalletToFree(PalletCode palletCode, Integer operatorId) {
        palletCode.setStatus("FREE");
        palletCode.setProductId(null);
        palletCode.setProductStatus(null);
        palletCode.setProductionDate(null);
        palletCode.setScreenMeshId(null);
        palletCode.setAssayId(null);
        palletCode.setUpdatedBy(operatorId);
        palletCode.setUpdatedAt(LocalDateTime.now());
        palletCodeMapper.updateById(palletCode);
    }

    private void refreshOrderProgress(Long orderId) {
        ProductionOrder order = productionOrderMapper.selectById(orderId);
        if (order == null || ORDER_STATUS_CANCELED.equals(order.getStatus()) || ORDER_STATUS_COMPLETED.equals(order.getStatus())) {
            return;
        }
        List<ProductionOutputVO> outputs = outputMapper.listOutputs(orderId);
        if (outputs.isEmpty()) {
            return;
        }
        int required = outputs.stream().mapToInt(item -> safeInt(item.getRequiredQrCount())).sum();
        int bound = outputs.stream().mapToInt(item -> safeInt(item.getBoundQrCount())).sum();
        int inbound = outputs.stream().mapToInt(item -> safeInt(item.getInboundQrCount())).sum();
        if (required > 0 && inbound >= required) {
            order.setStatus(ORDER_STATUS_COMPLETED);
            order.setCompletedAt(LocalDateTime.now());
            boilingBatchService.consumeReservedByOrder(orderId);
        } else if (inbound > 0) {
            order.setStatus(ORDER_STATUS_PART_INBOUND);
        } else if (required > 0 && bound >= required) {
            order.setStatus(ORDER_STATUS_WAIT_INBOUND);
        } else {
            order.setStatus(ORDER_STATUS_OUTPUT_BINDING);
        }
        order.setUpdatedAt(LocalDateTime.now());
        productionOrderMapper.updateById(order);
    }

    private void moveOrderToOutputBinding(ProductionOrder order) {
        if (!ORDER_STATUS_CANCELED.equals(order.getStatus()) && !ORDER_STATUS_COMPLETED.equals(order.getStatus())) {
            order.setStatus(ORDER_STATUS_OUTPUT_BINDING);
            order.setUpdatedAt(LocalDateTime.now());
            productionOrderMapper.updateById(order);
        }
    }

    private ProductionBindQrResultVO buildBindResult(Long outputId, int newlyBoundCount) {
        ProductionOrderOutput output = outputMapper.selectById(outputId);
        ProductionBindQrResultVO result = new ProductionBindQrResultVO();
        result.setOutputId(outputId);
        result.setRequiredQrCount(output.getRequiredQrCount());
        result.setNewlyBoundCount(newlyBoundCount);
        result.setBoundQrCount(output.getBoundQrCount());
        result.setCodes(outputCodeMapper.listCodesByOutput(outputId));
        return result;
    }

    private List<Integer> resolvePickPalletCodeIds(ProductionMaterialPickDTO dto) {
        List<Integer> ids = new ArrayList<>();
        if (dto.getPalletCodeIds() != null) {
            ids.addAll(dto.getPalletCodeIds());
        }
        if (dto.getPalletCodes() != null) {
            for (String rawCode : dto.getPalletCodes()) {
                String code = rawCode == null ? null : rawCode.trim().toUpperCase();
                if (code == null || code.isBlank()) {
                    continue;
                }
                PalletCode palletCode = palletCodeMapper.selectOne(new QueryWrapper<PalletCode>().eq("code", code).last("limit 1"));
                if (palletCode == null) {
                    throw new BusinessException("二维码不存在：" + code);
                }
                ids.add(palletCode.getId());
            }
        }
        return ids.stream().distinct().toList();
    }

    private Inventory requireInventoryForUpdate(Integer palletCodeId) {
        Inventory inventory = inventoryMapper.selectOne(new QueryWrapper<Inventory>()
                .eq("pallet_code_id", palletCodeId)
                .last("limit 1 for update"));
        if (inventory == null) {
            throw new BusinessException("二维码不在库，不能领用");
        }
        return inventory;
    }

    private PalletCode requirePalletForUpdate(Integer palletCodeId) {
        PalletCode palletCode = palletCodeMapper.selectOne(new QueryWrapper<PalletCode>()
                .eq("id", palletCodeId)
                .last("limit 1 for update"));
        if (palletCode == null) {
            throw new BusinessException("二维码不存在");
        }
        return palletCode;
    }

    private ProductionOrder requireOrder(Long id) {
        ProductionOrder order = productionOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("生产订单不存在");
        }
        return order;
    }

    private ProductionOrder requireOrderForUpdate(Long id) {
        ProductionOrder order = productionOrderMapper.selectOne(new QueryWrapper<ProductionOrder>()
                .eq("id", id)
                .last("limit 1 for update"));
        if (order == null) {
            throw new BusinessException("生产订单不存在");
        }
        return order;
    }

    private ProductionOrderOutput requireOutputForUpdate(Long id) {
        ProductionOrderOutput output = outputMapper.selectOne(new QueryWrapper<ProductionOrderOutput>()
                .eq("id", id)
                .last("limit 1 for update"));
        if (output == null) {
            throw new BusinessException("产出行不存在");
        }
        return output;
    }

    private Product requireProduct(Integer id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("产品不存在");
        }
        return product;
    }

    private void validateOrderType(String orderType) {
        if (!"SEMI".equals(orderType) && !"FINISH".equals(orderType)) {
            throw new BusinessException("订单类型仅支持 SEMI/FINISH");
        }
    }

    private void validateBoilingSourcesForOrderType(ProductionOrderCreateDTO dto) {
        if ("FINISH".equals(dto.getOrderType())
                && dto.getBoilingSources() != null
                && !dto.getBoilingSources().isEmpty()) {
            throw new BusinessException("煮糖批次来源仅支持半成品生产订单，成品生产订单请通过半成品领用关联来源");
        }
    }
    private void validateOutputProductStatus(ProductionOrder order, Product product) {
        if ("SEMI".equals(order.getOrderType()) && !"半成品".equals(product.getStatus())) {
            throw new BusinessException("半成品生产订单只能产出半成品");
        }
        if ("FINISH".equals(order.getOrderType()) && !"成品".equals(product.getStatus())) {
            throw new BusinessException("成品生产订单只能产出成品");
        }
    }

    private void ensureOrderOpen(ProductionOrder order) {
        if (ORDER_STATUS_COMPLETED.equals(order.getStatus()) || ORDER_STATUS_CANCELED.equals(order.getStatus())) {
            throw new BusinessException("订单已完成或已取消，不能继续操作");
        }
    }

    private void ensureManualDraftOutputAllowed(ProductionOrder order) {
        String status = order == null ? null : order.getStatus();
        if (ORDER_STATUS_PREPRINTED.equals(status)
                || "PRODUCTION_DONE".equals(status)
                || ORDER_STATUS_WAIT_INBOUND.equals(status)
                || ORDER_STATUS_PART_INBOUND.equals(status)
                || ORDER_STATUS_COMPLETED.equals(status)) {
            throw new BusinessException("当前订单已进入预打印/核销流程，不能再手动添加草稿产出。");
        }
    }

    private void ensureOutputEditable(ProductionOrderOutput output) {
        if (safeInt(output.getBoundQrCount()) > 0) {
            throw new BusinessException("产出已使用订单码，不能修改或删除");
        }
        if (!"DRAFT".equals(output.getStatus())) {
            throw new BusinessException("只有待绑定产出允许修改或删除");
        }
    }

    private void refreshOrderStatusAfterOutputDraftChange(Long orderId) {
        ProductionOrder order = productionOrderMapper.selectById(orderId);
        if (order == null || ORDER_STATUS_CANCELED.equals(order.getStatus()) || ORDER_STATUS_COMPLETED.equals(order.getStatus())) {
            return;
        }
        long activeOutputs = outputMapper.selectCount(new QueryWrapper<ProductionOrderOutput>()
                .eq("production_order_id", orderId)
                .ne("status", "CANCELED"));
        if (activeOutputs > 0) {
            order.setStatus(ORDER_STATUS_OUTPUT_BINDING);
        } else if ("FINISH".equals(order.getOrderType())) {
            long materialCount = materialMapper.selectCount(new QueryWrapper<ProductionOrderMaterial>()
                    .eq("production_order_id", orderId)
                    .ne("status", "CANCELED"));
            order.setStatus(materialCount > 0 ? ORDER_STATUS_MATERIALED : ORDER_STATUS_ISSUED);
        } else {
            order.setStatus(ORDER_STATUS_ISSUED);
        }
        order.setUpdatedAt(LocalDateTime.now());
        productionOrderMapper.updateById(order);
    }

    private int countActiveCodes(Long outputId) {
        return Math.toIntExact(outputCodeMapper.selectCount(new QueryWrapper<ProductionOrderOutputCode>()
                .eq("output_id", outputId)
                .ne("status", "CANCELED")));
    }

    private String materialsText(List<ProductionMaterialVO> materials) {
        Map<String, int[]> totals = new LinkedHashMap<>();
        for (ProductionMaterialVO material : materials) {
            int perPallet = material.getTotalPieces() != null && safeInt(material.getQuantity()) > 0 && "0".equals(material.getUnit())
                    ? material.getTotalPieces() / Math.max(1, safeInt(material.getQuantity()))
                    : 1;
            int pieces = safeInt(material.getTotalPieces());
            int[] arr = totals.computeIfAbsent(material.getProductName(), key -> new int[]{0, 0, perPallet});
            arr[0] += perPallet > 0 ? pieces / perPallet : 0;
            arr[1] += perPallet > 0 ? pieces % perPallet : pieces;
        }
        return quantityMapText(totals);
    }

    private String outputsText(List<ProductionOutputVO> outputs) {
        Map<String, int[]> totals = new LinkedHashMap<>();
        for (ProductionOutputVO output : outputs) {
            int[] arr = totals.computeIfAbsent(output.getProductName(), key -> new int[]{0, 0, safeInt(output.getPiecesPerPallet())});
            arr[0] += safeInt(output.getBoardCount());
            arr[1] += safeInt(output.getPieceCount());
        }
        return quantityMapText(totals);
    }

    private String quantityMapText(Map<String, int[]> totals) {
        if (totals.isEmpty()) {
            return "-";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : totals.entrySet()) {
            int boards = entry.getValue()[0];
            int pieces = entry.getValue()[1];
            String text = entry.getKey() + " " + boards + "板";
            if (pieces > 0) {
                text += pieces + "件";
            }
            parts.add(text);
        }
        return String.join("；", parts);
    }

    private String jsonPlanText(String json) {
        Object value = fromJson(json);
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return "-";
        }
        List<String> parts = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String productName = stringValue(firstPresent(map, "productName", "name"));
            int boards = intValue(firstPresent(map, "boardCount", "quantity", "boards"));
            int pieces = intValue(firstPresent(map, "pieceCount", "pieces"));
            String text = (productName == null ? "产品" : productName) + " " + boards + "板";
            if (pieces > 0) {
                text += pieces + "件";
            }
            parts.add(text);
        }
        return parts.isEmpty() ? "-" : String.join("；", parts);
    }

    private Object firstPresent(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private Object fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Object>() {});
        } catch (Exception e) {
            return json;
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("计划用量 JSON 格式错误");
        }
    }

    private String appendRemark(String remark, String trace) {
        return blankToNull(remark) == null ? trace : remark + "；" + trace;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizeSize(Integer size) {
        return size == null || size < 1 ? 10 : Math.min(size, 100);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

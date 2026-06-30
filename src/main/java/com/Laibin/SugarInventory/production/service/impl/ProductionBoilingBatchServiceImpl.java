package com.Laibin.SugarInventory.production.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.production.domain.dto.ProductionBoilingBatchQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionBoilingBatchSaveDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionBoilingBatchUsageDTO;
import com.Laibin.SugarInventory.production.domain.po.ProductionBoilingBatch;
import com.Laibin.SugarInventory.production.domain.po.ProductionBoilingBatchUsage;
import com.Laibin.SugarInventory.production.domain.po.ProductionOrder;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchTraceNodeVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchUsageVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionTraceMaterialRowVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionTraceOutputCodeRowVO;
import com.Laibin.SugarInventory.production.mapper.ProductionBoilingBatchMapper;
import com.Laibin.SugarInventory.production.mapper.ProductionBoilingBatchUsageMapper;
import com.Laibin.SugarInventory.production.mapper.ProductionBoilingBatchTraceMapper;
import com.Laibin.SugarInventory.production.service.ProductionBoilingBatchService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductionBoilingBatchServiceImpl implements ProductionBoilingBatchService {
    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String STATUS_USED_UP = "USED_UP";
    private static final String STATUS_CANCELED = "CANCELED";
    private static final String USAGE_RESERVED = "RESERVED";
    private static final String USAGE_CONSUMED = "CONSUMED";
    private static final String USAGE_RELEASED = "RELEASED";

    private final ProductionBoilingBatchMapper batchMapper;
    private final ProductionBoilingBatchUsageMapper usageMapper;
    private final ProductMapper productMapper;
    private final ProductionBoilingBatchTraceMapper traceMapper;

    @Override
    public PageResult<ProductionBoilingBatchVO> pageBatches(ProductionBoilingBatchQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        List<ProductionBoilingBatchVO> records = batchMapper.pageBatches(query, (page - 1) * size, size);
        records.forEach(this::fillRemaining);
        return new PageResult<>(batchMapper.countBatches(query), records);
    }

    @Override
    public ProductionBoilingBatchVO getDetail(Long id) {
        ProductionBoilingBatch batch = requireBatch(id);
        ProductionBoilingBatchVO vo = toVO(batch);
        vo.setUsages(usageMapper.listByBatch(id));
        return vo;
    }

    @Override
    @Transactional
    public ProductionBoilingBatchVO createBatch(ProductionBoilingBatchSaveDTO dto, Integer operatorId, String operatorName) {
        ProductionBoilingBatch batch = new ProductionBoilingBatch();
        batch.setBatchNo(resolveBatchNo(dto.getBatchNo(), dto.getBoilingDate(), null));
        applySaveDTO(batch, dto);
        batch.setStatus(STATUS_AVAILABLE);
        batch.setCreatedBy(operatorId);
        batch.setCreatedByName(operatorName);
        batch.setCreatedAt(LocalDateTime.now());
        batchMapper.insert(batch);
        return getDetail(batch.getId());
    }

    @Override
    @Transactional
    public ProductionBoilingBatchVO updateBatch(Long id, ProductionBoilingBatchSaveDTO dto) {
        ProductionBoilingBatch batch = requireBatchForUpdate(id);
        if (STATUS_CANCELED.equals(batch.getStatus())) {
            throw new BusinessException("已作废煮糖批次不能编辑");
        }
        batch.setBatchNo(resolveBatchNo(dto.getBatchNo(), dto.getBoilingDate(), id));
        applySaveDTO(batch, dto);
        BigDecimal reserved = sumBucket(id, USAGE_RESERVED);
        BigDecimal consumed = sumBucket(id, USAGE_CONSUMED);
        if (batch.getBucketCount().compareTo(reserved.add(consumed)) < 0) {
            throw new BusinessException("调整后总桶数小于已占用和已消耗数量，不能保存");
        }
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
        refreshBatchStatus(id);
        return getDetail(id);
    }

    @Override
    @Transactional
    public void cancelBatch(Long id, Integer operatorId) {
        ProductionBoilingBatch batch = requireBatchForUpdate(id);
        if (sumBucket(id, USAGE_RESERVED).compareTo(BigDecimal.ZERO) > 0
                || sumBucket(id, USAGE_CONSUMED).compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("煮糖批次已有订单占用或消耗，不能作废");
        }
        batch.setStatus(STATUS_CANCELED);
        batch.setCanceledBy(operatorId);
        batch.setCanceledAt(LocalDateTime.now());
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
    }

    @Override
    @Transactional
    public void reserveForOrder(ProductionOrder order, List<ProductionBoilingBatchUsageDTO> usages,
                                Integer operatorId, String operatorName) {
        if (usages == null || usages.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (ProductionBoilingBatchUsageDTO dto : usages) {
            ProductionBoilingBatch batch = requireBatchForUpdate(dto.getBatchId());
            if (!STATUS_AVAILABLE.equals(batch.getStatus()) && !STATUS_USED_UP.equals(batch.getStatus())) {
                throw new BusinessException("煮糖批次不可引用：" + batch.getBatchNo());
            }
            ProductionBoilingBatchQuantity.UsageQuantity quantity = ProductionBoilingBatchQuantity.convertUsage(
                    dto.getUsageUnit(), dto.getUsageQuantity(), batch.getKgPerBucket());
            ensureEnough(batch, quantity);

            ProductionBoilingBatchUsage usage = new ProductionBoilingBatchUsage();
            usage.setBatchId(batch.getId());
            usage.setBatchNo(batch.getBatchNo());
            usage.setProductionOrderId(order.getId());
            usage.setOrderNo(order.getOrderNo());
            usage.setUsageUnit(quantity.unit());
            usage.setUsageQuantity(quantity.inputQuantity());
            usage.setBucketQuantity(quantity.bucketQuantity());
            usage.setWeightKg(quantity.weightKg());
            usage.setStatus(USAGE_RESERVED);
            usage.setCreatedBy(operatorId);
            usage.setCreatedByName(operatorName);
            usage.setCreatedAt(now);
            usage.setRemark(blankToNull(dto.getRemark()));
            usageMapper.insert(usage);
            refreshBatchStatus(batch.getId());
        }
    }

    @Override
    @Transactional
    public void consumeReservedByOrder(Long orderId) {
        List<ProductionBoilingBatchUsage> usages = usageMapper.listReservedByOrderForUpdate(orderId);
        if (usages.isEmpty()) {
            return;
        }
        usageMapper.updateReservedByOrder(orderId, USAGE_CONSUMED);
        usages.stream().map(ProductionBoilingBatchUsage::getBatchId).distinct().forEach(this::refreshBatchStatus);
    }

    @Override
    @Transactional
    public void releaseReservedByOrder(Long orderId) {
        List<ProductionBoilingBatchUsage> usages = usageMapper.listReservedByOrderForUpdate(orderId);
        if (usages.isEmpty()) {
            return;
        }
        usageMapper.updateReservedByOrder(orderId, USAGE_RELEASED);
        usages.stream().map(ProductionBoilingBatchUsage::getBatchId).distinct().forEach(this::refreshBatchStatus);
    }

    @Override
    public List<ProductionBoilingBatchUsageVO> listUsagesByOrder(Long orderId) {
        return usageMapper.listByOrder(orderId);
    }

    @Override
    public ProductionBoilingBatchTraceNodeVO getTrace(Long id) {
        ProductionBoilingBatchVO batch = getDetail(id);
        String batchProductName = defaultText(batch.getProductName(), batch.getSugarType(), "煮糖原料");
        ProductionBoilingBatchTraceNodeVO graph = node(batch.getBatchNo(), "煮糖批次", batch.getStatus(),
                "剩余 " + format(batch.getRemainingBucketCount()) + "桶 / " + format(batch.getRemainingWeightKg()) + "kg");

        Map<String, ProductionBoilingBatchTraceNodeVO.TraceGraphNodeVO> nodes = new LinkedHashMap<>();
        List<ProductionBoilingBatchTraceNodeVO.TraceGraphEdgeVO> edges = new ArrayList<>();
        List<ProductionBoilingBatchTraceNodeVO.TraceTimelineRecordVO> timeline = new ArrayList<>();
        Set<Long> initialOrderIds = new HashSet<>();
        Set<Long> renderedOutputIds = new HashSet<>();
        Map<String, String> palletNodesByIdentity = new LinkedHashMap<>();
        Map<String, String> warehouseNodesByPalletIdentity = new LinkedHashMap<>();
        Map<Integer, Set<Integer>> producedCycles = new LinkedHashMap<>();

        String batchItemId = "boiling-batch-item-" + batch.getId();
        addNode(nodes, batchItemId, "BOILING_BATCH_ITEM", batchProductName, batch.getBatchNo(),
                batchProductName, format(batch.getBucketCount()) + "桶 / " + format(batch.getTotalWeightKg()) + "kg",
                null, batch.getStatus(), batch.getCreatedAt(), Map.of("batchId", batch.getId()));

        Map<Long, List<ProductionBoilingBatchUsageVO>> byOrder = batch.getUsages().stream()
                .filter(item -> item.getProductionOrderId() != null)
                .collect(Collectors.groupingBy(ProductionBoilingBatchUsageVO::getProductionOrderId,
                        LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<Long, List<ProductionBoilingBatchUsageVO>> entry : byOrder.entrySet()) {
            ProductionBoilingBatchUsageVO first = entry.getValue().get(0);
            initialOrderIds.add(first.getProductionOrderId());
            String orderId = orderNodeId(first.getProductionOrderId(), false);
            String usageSummary = entry.getValue().stream().map(this::usageText).collect(Collectors.joining("；"));
            addNode(nodes, orderId, "PRODUCTION_ORDER", first.getOrderNo(), first.getOrderNo(), null,
                    usageSummary, null, first.getOrderStatus(), first.getCreatedAt(),
                    Map.of("orderId", first.getProductionOrderId()));
            addEdge(edges, batchItemId, orderId, "引用煮糖批次",
                    entry.getValue().stream().map(this::usageTraceText).collect(Collectors.joining("；")),
                    first.getStatus());
            entry.getValue().forEach(usage -> addTimeline(timeline, usage.getCreatedAt(), "引用煮糖批次",
                    usage.getOrderNo(), batchProductName, usageTraceText(usage), null,
                    "煮糖批次" + batch.getBatchNo(), usageStatusText(usage.getStatus())));

            ProductionBoilingBatchTraceNodeVO orderTreeNode = node(first.getOrderNo(), "生产订单",
                    first.getOrderStatus(), usageSummary);
            appendOrderOutputs(first.getProductionOrderId(), orderId, orderTreeNode, nodes, edges, timeline,
                    renderedOutputIds, palletNodesByIdentity, warehouseNodesByPalletIdentity, producedCycles, false);
            graph.getChildren().add(orderTreeNode);
        }

        List<ProductionTraceMaterialRowVO> downstreamMaterials = producedCycles.isEmpty()
                ? List.of()
                : traceMapper.listMaterialsByPalletCodeIds(producedCycles.keySet()).stream()
                .filter(material -> belongsToProducedCycle(material, producedCycles))
                .toList();

        Map<Long, List<ProductionTraceMaterialRowVO>> downstreamByOrder = downstreamMaterials.stream()
                .filter(item -> item.getProductionOrderId() != null)
                .collect(Collectors.groupingBy(ProductionTraceMaterialRowVO::getProductionOrderId,
                        LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<Long, List<ProductionTraceMaterialRowVO>> entry : downstreamByOrder.entrySet()) {
            ProductionTraceMaterialRowVO first = entry.getValue().get(0);
            String downstreamOrderId = orderNodeId(first.getProductionOrderId(), true);
            String materialSummary = entry.getValue().stream()
                    .map(item -> defaultText(item.getProductName(), "半成品") + " " + formatMaterialQuantity(item))
                    .distinct()
                    .collect(Collectors.joining("；"));
            addNode(nodes, downstreamOrderId, "DOWNSTREAM_PRODUCTION_ORDER", first.getOrderNo(), first.getOrderNo(),
                    null, materialSummary, null, first.getOrderStatus(), first.getPickedAt(),
                    Map.of("orderId", first.getProductionOrderId()));

            for (ProductionTraceMaterialRowVO material : entry.getValue()) {
                String identity = palletIdentity(material.getPalletCodeId(), material.getPalletCycleNo());
                String palletNodeId = palletNodesByIdentity.get(identity);
                if (palletNodeId == null) {
                    continue;
                }
                String sourceNodeId = warehouseNodesByPalletIdentity.getOrDefault(identity, palletNodeId);
                addEdge(edges, sourceNodeId, downstreamOrderId,
                        sourceNodeId.equals(palletNodeId) ? "被领用消耗" : "出库领用",
                        formatMaterialQuantity(material), material.getStatus());
                addTimeline(timeline, material.getPickedAt(), "半成品领用/消耗", material.getOrderNo(),
                        material.getProductName(), formatMaterialQuantity(material),
                        warehouseDisplay(material.getWarehouseName()), material.getPalletCode(),
                        materialStatusText(material.getStatus()));
            }

            if (!initialOrderIds.contains(first.getProductionOrderId())) {
                appendOrderOutputs(first.getProductionOrderId(), downstreamOrderId, null, nodes, edges, timeline,
                        renderedOutputIds, palletNodesByIdentity, warehouseNodesByPalletIdentity, producedCycles, true);
            }
        }

        timeline.sort(Comparator.comparing(ProductionBoilingBatchTraceNodeVO.TraceTimelineRecordVO::getOccurredAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        graph.setNodes(new ArrayList<>(nodes.values()));
        graph.setEdges(edges);
        graph.setTimeline(timeline);
        return graph;
    }

    private void appendOrderOutputs(Long orderId,
                                    String orderNodeId,
                                    ProductionBoilingBatchTraceNodeVO orderTreeNode,
                                    Map<String, ProductionBoilingBatchTraceNodeVO.TraceGraphNodeVO> nodes,
                                    List<ProductionBoilingBatchTraceNodeVO.TraceGraphEdgeVO> edges,
                                    List<ProductionBoilingBatchTraceNodeVO.TraceTimelineRecordVO> timeline,
                                    Set<Long> renderedOutputIds,
                                    Map<String, String> palletNodesByIdentity,
                                    Map<String, String> warehouseNodesByPalletIdentity,
                                    Map<Integer, Set<Integer>> producedCycles,
                                    boolean downstream) {
        List<ProductionTraceOutputCodeRowVO> rows = traceMapper.listOutputCodeRowsByOrder(orderId);
        for (ProductionTraceOutputCodeRowVO row : rows) {
            String outputNodeId = outputNodeId(row.getOutputId(), downstream);
            boolean finishedOutput = downstream || "FINISH".equals(row.getOrderType())
                    || "成品".equals(row.getOutputProductStatus());
            String outputType = finishedOutput ? "FINISHED_OUTPUT" : "ACTUAL_OUTPUT";
            addNode(nodes, outputNodeId, outputType, row.getOutputProductName(), row.getOrderNo(),
                    row.getOutputProductName(), formatOutputQuantity(row), null, row.getOutputStatus(),
                    row.getOutputCreatedAt(), Map.of("outputId", row.getOutputId()));
            addEdge(edges, orderNodeId, outputNodeId, "产出",
                    row.getOutputProductName() + " " + formatOutputQuantity(row), row.getOutputStatus());

            if (row.getOutputId() != null && renderedOutputIds.add(row.getOutputId())) {
                addTimeline(timeline, row.getOutputCreatedAt(), finishedOutput ? "成品产出" : "半成品产出",
                        row.getOrderNo(), row.getOutputProductName(), formatOutputQuantity(row), null,
                        defaultText(row.getLabelBatchNo(), row.getPalletCode()), outputStatusText(row.getOutputStatus()));
                if (orderTreeNode != null) {
                    orderTreeNode.getChildren().add(node(row.getOutputProductName(), "实际产出",
                            row.getOutputStatus(), formatOutputQuantity(row)));
                }
            }

            String codeParentNodeId = outputNodeId;
            if (row.getLabelBatchId() != null) {
                String labelBatchNodeId = labelBatchNodeId(row.getLabelBatchId());
                addNode(nodes, labelBatchNodeId, "PALLET_CODE_BATCH", row.getLabelBatchNo(), row.getLabelBatchNo(),
                        row.getOutputProductName(), formatLabelBatchQuantity(row), null, row.getLabelBatchStatus(),
                        row.getLabelBatchCreatedAt(), Map.of("labelBatchId", row.getLabelBatchId()));
                addEdge(edges, outputNodeId, labelBatchNodeId, "生成订单码批次",
                        formatLabelBatchQuantity(row), row.getLabelBatchStatus());
                codeParentNodeId = labelBatchNodeId;
            }

            if (row.getOutputCodeId() == null || row.getPalletCodeId() == null) {
                continue;
            }

            String identity = palletIdentity(row.getPalletCodeId(), row.getPalletCycleNo());
            String palletNodeId = palletNodeId(row.getPalletCodeId(), row.getPalletCycleNo());
            palletNodesByIdentity.put(identity, palletNodeId);
            producedCycles.computeIfAbsent(row.getPalletCodeId(), ignored -> new HashSet<>())
                    .add(cycleKey(row.getPalletCycleNo()));
            addNode(nodes, palletNodeId, "PALLET_CODE", row.getPalletCode(), row.getPalletCode(),
                    row.getOutputProductName(), formatCodeQuantity(row), null, row.getCodeStatus(),
                    firstTime(row.getCodeCreatedAt(), row.getInboundAt()),
                    Map.of("palletCodeId", row.getPalletCodeId(), "outputCodeId", row.getOutputCodeId()));
            addEdge(edges, codeParentNodeId, palletNodeId,
                    row.getLabelBatchId() == null ? "生成二维码" : "核销使用标签",
                    formatCodeQuantity(row), row.getCodeStatus());

            if (orderTreeNode != null) {
                orderTreeNode.getChildren().add(node(row.getPalletCode(), "托盘码",
                        row.getCodeStatus(), formatCodeQuantity(row)));
            }

            if (hasActualInbound(row)) {
                String warehouseNodeId = warehouseNodeId(row.getWarehouseId(), row.getWarehouseName(),
                        row.getPalletCodeId(), row.getPalletCycleNo(), finishedOutput);
                String warehouseType = finishedOutput ? "FINISHED_WAREHOUSE_LOCATION" : "WAREHOUSE_LOCATION";
                String warehouseName = warehouseDisplay(row.getWarehouseName());
                addNode(nodes, warehouseNodeId, warehouseType, warehouseName, null, null,
                        formatCodeQuantity(row), warehouseName, "INSTOCK", row.getInboundAt(),
                        row.getWarehouseId() == null ? Map.of() : Map.of("warehouseId", row.getWarehouseId()));
                addEdge(edges, palletNodeId, warehouseNodeId, "入库", formatCodeQuantity(row), "INSTOCK");
                warehouseNodesByPalletIdentity.put(identity, warehouseNodeId);
                addTimeline(timeline, row.getInboundAt(), finishedOutput ? "成品入库" : "半成品入库",
                        row.getPalletCode(), row.getOutputProductName(), formatCodeQuantity(row),
                        warehouseName, row.getOrderNo(), "已入库");
            }
        }
    }

    private boolean belongsToProducedCycle(ProductionTraceMaterialRowVO material,
                                           Map<Integer, Set<Integer>> producedCycles) {
        Set<Integer> cycles = producedCycles.get(material.getPalletCodeId());
        if (cycles == null || cycles.isEmpty()) {
            return false;
        }
        return cycles.contains(-1) || cycles.contains(cycleKey(material.getPalletCycleNo()));
    }

    private boolean hasActualInbound(ProductionTraceOutputCodeRowVO row) {
        return "INSTOCK".equals(row.getCodeStatus()) || row.getInboundAt() != null || row.getInventoryId() != null;
    }

    private int cycleKey(Integer cycleNo) {
        return cycleNo == null ? -1 : cycleNo;
    }

    private String palletIdentity(Integer palletCodeId, Integer cycleNo) {
        return palletCodeId + ":" + cycleKey(cycleNo);
    }

    private String formatLabelBatchQuantity(ProductionTraceOutputCodeRowVO row) {
        int reserved = safeInt(row.getLabelBatchReservedCount());
        int used = safeInt(row.getLabelBatchUsedCount());
        int recycled = safeInt(row.getLabelBatchRecycledCount());
        return "共" + reserved + "张 / 已用" + used + "张 / 回收" + recycled + "张";
    }

    private void addNode(Map<String, ProductionBoilingBatchTraceNodeVO.TraceGraphNodeVO> nodes,
                         String id,
                         String type,
                         String name,
                         String documentNo,
                         String productName,
                         String quantityText,
                         String warehouseName,
                         String status,
                         LocalDateTime occurredAt,
                         Map<String, Object> meta) {
        if (id == null || nodes.containsKey(id)) {
            return;
        }
        ProductionBoilingBatchTraceNodeVO.TraceGraphNodeVO node = new ProductionBoilingBatchTraceNodeVO.TraceGraphNodeVO();
        node.setId(id);
        node.setType(type);
        node.setName(defaultText(name, documentNo, productName, warehouseName));
        node.setDocumentNo(documentNo);
        node.setProductName(productName);
        node.setQuantityText(quantityText);
        node.setWarehouseName(warehouseName);
        node.setStatus(statusText(status));
        node.setOccurredAt(occurredAt == null ? null : occurredAt.toString());
        node.setMeta(meta == null ? Map.of() : meta);
        nodes.put(id, node);
    }

    private void addEdge(List<ProductionBoilingBatchTraceNodeVO.TraceGraphEdgeVO> edges,
                         String source,
                         String target,
                         String action,
                         String quantityText,
                         String status) {
        if (source == null || target == null) {
            return;
        }
        boolean duplicated = edges.stream().anyMatch(item -> source.equals(item.getSource())
                && target.equals(item.getTarget()) && action.equals(item.getAction()));
        if (duplicated) {
            return;
        }
        ProductionBoilingBatchTraceNodeVO.TraceGraphEdgeVO edge = new ProductionBoilingBatchTraceNodeVO.TraceGraphEdgeVO();
        edge.setId("edge-" + edges.size());
        edge.setSource(source);
        edge.setTarget(target);
        edge.setAction(action);
        edge.setQuantityText(quantityText);
        edge.setLabel(quantityText == null || quantityText.isBlank() ? action : action + " " + quantityText);
        edge.setStatus(statusText(status));
        edges.add(edge);
    }

    private void addTimeline(List<ProductionBoilingBatchTraceNodeVO.TraceTimelineRecordVO> timeline,
                             LocalDateTime occurredAt,
                             String actionType,
                             String documentNo,
                             String productName,
                             String quantityText,
                             String warehouseName,
                             String relatedObject,
                             String status) {
        ProductionBoilingBatchTraceNodeVO.TraceTimelineRecordVO record = new ProductionBoilingBatchTraceNodeVO.TraceTimelineRecordVO();
        record.setOccurredAt(occurredAt);
        record.setActionType(actionType);
        record.setDocumentNo(documentNo);
        record.setProductName(productName);
        record.setQuantityText(quantityText);
        record.setWarehouseName(warehouseName);
        record.setRelatedObject(relatedObject);
        record.setStatus(statusText(status));
        timeline.add(record);
    }

    private String orderNodeId(Long orderId, boolean downstream) {
        return (downstream ? "downstream-order-" : "order-") + orderId;
    }

    private String outputNodeId(Long outputId, boolean downstream) {
        return (downstream ? "finished-output-" : "output-") + outputId;
    }

    private String labelBatchNodeId(Long labelBatchId) {
        return "label-batch-" + labelBatchId;
    }

    private String palletNodeId(Integer palletCodeId, Integer cycleNo) {
        return "pallet-code-" + palletCodeId + "-cycle-" + cycleKey(cycleNo);
    }

    private String warehouseNodeId(Integer warehouseId,
                                   String warehouseName,
                                   Integer palletCodeId,
                                   Integer cycleNo,
                                   boolean downstream) {
        return (downstream ? "finished-warehouse-" : "warehouse-")
                + (warehouseId == null ? warehouseDisplay(warehouseName) : warehouseId)
                + "-pallet-" + palletCodeId + "-cycle-" + cycleKey(cycleNo);
    }

    private String formatOutputQuantity(ProductionTraceOutputCodeRowVO row) {
        int boards = safeInt(row.getBoardCount());
        int pieces = safeInt(row.getPieceCount());
        if (boards <= 0 && pieces <= 0 && row.getOutputTotalPieces() != null) {
            return row.getOutputTotalPieces() + "件";
        }
        return boardPieceText(boards, pieces);
    }

    private String formatCodeQuantity(ProductionTraceOutputCodeRowVO row) {
        if ("1".equals(row.getUnit())) {
            return safeInt(row.getPieces()) > 0 ? row.getPieces() + "件" : safeInt(row.getQuantity()) + "件";
        }
        int boards = Math.max(1, safeInt(row.getQuantity()));
        int pieces = safeInt(row.getPieces());
        return boardPieceText(boards, pieces);
    }

    private String formatMaterialQuantity(ProductionTraceMaterialRowVO row) {
        if (row.getTotalPieces() != null && row.getTotalPieces() > 0 && "1".equals(row.getUnit())) {
            return row.getTotalPieces() + "件";
        }
        if ("1".equals(row.getUnit())) {
            return safeInt(row.getPieces()) > 0 ? row.getPieces() + "件" : safeInt(row.getQuantity()) + "件";
        }
        return boardPieceText(Math.max(1, safeInt(row.getQuantity())), safeInt(row.getPieces()));
    }

    private String boardPieceText(int boards, int pieces) {
        StringBuilder builder = new StringBuilder();
        if (boards > 0) {
            builder.append(boards).append("板");
        }
        if (pieces > 0) {
            builder.append(pieces).append("件");
        }
        return builder.isEmpty() ? "-" : builder.toString();
    }

    private String usageTraceText(ProductionBoilingBatchUsageVO usage) {
        return format(usage.getBucketQuantity()) + "桶 / " + format(usage.getWeightKg()) + "kg";
    }

    private String warehouseDisplay(String warehouseName) {
        String value = blankToNull(warehouseName);
        if (value == null) {
            return null;
        }
        return value.matches("\\d+") ? value + "号库" : value;
    }

    private LocalDateTime firstTime(LocalDateTime first, LocalDateTime fallback) {
        return first == null ? fallback : first;
    }

    private String defaultText(String... values) {
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return "-";
    }

    private String statusText(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "AVAILABLE" -> "可用";
            case "USED_UP" -> "已用完";
            case "CANCELED" -> "已作废";
            case "RESERVED" -> "已预占";
            case "CONSUMED" -> "已消耗";
            case "RELEASED" -> "已释放";
            case "ISSUED" -> "已下发";
            case "MATERIALING" -> "领料中";
            case "MATERIALED" -> "已领料";
            case "OUTPUT_BINDING" -> "产出绑定中";
            case "PREPRINTED" -> "已预打印";
            case "WAIT_INBOUND" -> "待入库";
            case "PART_INBOUND" -> "部分入库";
            case "COMPLETED" -> "已完成";
            case "DRAFT" -> "草稿";
            case "BOUND" -> "已绑定";
            case "PENDING_INBOUND" -> "待入库";
            case "INSTOCK" -> "已入库";
            case "PRINTED" -> "已打印";
            case "CLOSED" -> "已关闭";
            case "USED" -> "已使用";
            case "RECYCLED" -> "已回收";
            case "ORDER_RESERVED" -> "订单预留";
            case "PICKED" -> "已领用";
            default -> status;
        };
    }

    private String outputStatusText(String status) {
        return statusText(status);
    }

    private String codeStatusText(String status) {
        return statusText(status);
    }

    private String materialStatusText(String status) {
        return statusText(status);
    }
    private void applySaveDTO(ProductionBoilingBatch batch, ProductionBoilingBatchSaveDTO dto) {
        batch.setBoilingDate(dto.getBoilingDate());
        batch.setTeamName(blankToNull(dto.getTeamName()));
        batch.setSugarType(blankToNull(dto.getSugarType()));
        batch.setProductId(dto.getProductId());
        Product product = dto.getProductId() == null ? null : productMapper.selectById(dto.getProductId());
        batch.setProductNameSnapshot(product == null ? null : product.getProductName());
        BigDecimal potCount = dto.getPotCount() == null ? BigDecimal.ONE.setScale(3) : positiveInteger(dto.getPotCount(), "锅数");
        batch.setPotCount(potCount);
        BigDecimal bucketCount = positiveInteger(ProductionBoilingBatchQuantity.positiveOrDefault(
                dto.getBucketCount(), ProductionBoilingBatchQuantity.DEFAULT_BUCKET_COUNT, "实际桶数"), "实际桶数");
        BigDecimal kgPerBucket = ProductionBoilingBatchQuantity.positiveOrDefault(
                dto.getKgPerBucket(), ProductionBoilingBatchQuantity.DEFAULT_KG_PER_BUCKET, "每桶重量");
        batch.setBucketCount(bucketCount);
        batch.setKgPerBucket(kgPerBucket);
        batch.setTotalWeightKg(ProductionBoilingBatchQuantity.totalWeight(potCount, bucketCount, kgPerBucket));
        batch.setSourceText(blankToNull(dto.getSourceText()));
        batch.setRemark(blankToNull(dto.getRemark()));
    }

    private void ensureEnough(ProductionBoilingBatch batch, ProductionBoilingBatchQuantity.UsageQuantity quantity) {
        BigDecimal remainingWeight = remainingWeight(batch);
        if (remainingWeight.compareTo(quantity.weightKg()) >= 0) {
            return;
        }
        List<ProductionBoilingBatchUsageVO> usages = usageMapper.listByBatch(batch.getId()).stream()
                .filter(item -> USAGE_RESERVED.equals(item.getStatus()))
                .toList();
        String occupied = usages.isEmpty()
                ? "无订单占用"
                : usages.stream()
                .map(item -> item.getOrderNo() + "占用" + format(item.getBucketQuantity()) + "桶")
                .collect(Collectors.joining("，"));
        throw new BusinessException("当前剩余 " + format(remainingBucket(batch)) + "桶 / " + format(remainingWeight)
                + "kg，其中 " + occupied);
    }

    private ProductionBoilingBatchVO toVO(ProductionBoilingBatch batch) {
        ProductionBoilingBatchVO vo = new ProductionBoilingBatchVO();
        vo.setId(batch.getId());
        vo.setBatchNo(batch.getBatchNo());
        vo.setBoilingDate(batch.getBoilingDate());
        vo.setTeamName(batch.getTeamName());
        vo.setSugarType(batch.getSugarType());
        vo.setProductId(batch.getProductId());
        vo.setProductName(batch.getProductNameSnapshot());
        vo.setPotCount(batch.getPotCount());
        vo.setBucketCount(batch.getBucketCount());
        vo.setKgPerBucket(batch.getKgPerBucket());
        vo.setTotalWeightKg(batch.getTotalWeightKg());
        vo.setStatus(batch.getStatus());
        vo.setSourceText(batch.getSourceText());
        vo.setRemark(batch.getRemark());
        vo.setCreatedByName(batch.getCreatedByName());
        vo.setCreatedAt(batch.getCreatedAt());
        vo.setUpdatedAt(batch.getUpdatedAt());
        vo.setReservedBucketCount(sumBucket(batch.getId(), USAGE_RESERVED));
        vo.setReservedWeightKg(sumWeight(batch.getId(), USAGE_RESERVED));
        vo.setConsumedBucketCount(sumBucket(batch.getId(), USAGE_CONSUMED));
        vo.setConsumedWeightKg(sumWeight(batch.getId(), USAGE_CONSUMED));
        fillRemaining(vo);
        return vo;
    }

    private void fillRemaining(ProductionBoilingBatchVO vo) {
        vo.setReservedBucketCount(nvl(vo.getReservedBucketCount()));
        vo.setReservedWeightKg(nvl(vo.getReservedWeightKg()));
        vo.setConsumedBucketCount(nvl(vo.getConsumedBucketCount()));
        vo.setConsumedWeightKg(nvl(vo.getConsumedWeightKg()));
        vo.setRemainingBucketCount(nvl(vo.getBucketCount()).subtract(vo.getReservedBucketCount()).subtract(vo.getConsumedBucketCount()));
        vo.setRemainingWeightKg(nvl(vo.getTotalWeightKg()).subtract(vo.getReservedWeightKg()).subtract(vo.getConsumedWeightKg()));
    }

    private void refreshBatchStatus(Long batchId) {
        ProductionBoilingBatch batch = requireBatchForUpdate(batchId);
        if (STATUS_CANCELED.equals(batch.getStatus())) {
            return;
        }
        batch.setStatus(remainingWeight(batch).compareTo(BigDecimal.ZERO) <= 0 ? STATUS_USED_UP : STATUS_AVAILABLE);
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
    }

    private BigDecimal remainingBucket(ProductionBoilingBatch batch) {
        return nvl(batch.getBucketCount()).subtract(sumBucket(batch.getId(), USAGE_RESERVED)).subtract(sumBucket(batch.getId(), USAGE_CONSUMED));
    }

    private BigDecimal remainingWeight(ProductionBoilingBatch batch) {
        return nvl(batch.getTotalWeightKg()).subtract(sumWeight(batch.getId(), USAGE_RESERVED)).subtract(sumWeight(batch.getId(), USAGE_CONSUMED));
    }

    private BigDecimal sumBucket(Long batchId, String status) {
        return nvl(usageMapper.sumBucketByStatus(batchId, status));
    }

    private BigDecimal sumWeight(Long batchId, String status) {
        return nvl(usageMapper.sumWeightByStatus(batchId, status));
    }

    private ProductionBoilingBatch requireBatch(Long id) {
        ProductionBoilingBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new BusinessException("煮糖批次不存在");
        }
        return batch;
    }

    private ProductionBoilingBatch requireBatchForUpdate(Long id) {
        ProductionBoilingBatch batch = batchMapper.selectOne(new QueryWrapper<ProductionBoilingBatch>()
                .eq("id", id)
                .last("limit 1 for update"));
        if (batch == null) {
            throw new BusinessException("煮糖批次不存在");
        }
        return batch;
    }
    private String resolveBatchNo(String input, java.time.LocalDate date, Long excludeId) {
        String batchNo = blankToNull(input);
        if (batchNo == null) {
            batchNo = generateBatchNo(date);
        }
        if (batchNo.length() > 40) {
            throw new BusinessException("批次号不能超过40个字符");
        }
        QueryWrapper<ProductionBoilingBatch> wrapper = new QueryWrapper<ProductionBoilingBatch>().eq("batch_no", batchNo);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        if (batchMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("煮糖批次号已存在：" + batchNo);
        }
        return batchNo;
    }

    private BigDecimal positiveIntegerOrNull(BigDecimal value, String fieldName) {
        if (value == null) {
            return null;
        }
        return positiveInteger(value, fieldName);
    }

    private BigDecimal positiveInteger(BigDecimal value, String fieldName) {
        BigDecimal normalized = ProductionBoilingBatchQuantity.normalize(value);
        if (normalized == null || normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(fieldName + "必须为正整数");
        }
        if (normalized.stripTrailingZeros().scale() > 0) {
            throw new BusinessException(fieldName + "必须为正整数");
        }
        return normalized;
    }

    private String generateBatchNo(java.time.LocalDate date) {
        String prefix = date.format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        String latest = batchMapper.selectLatestBatchNoForUpdate(prefix);
        int next = 1;
        if (latest != null && latest.length() >= prefix.length() + 2) {
            next = Integer.parseInt(latest.substring(prefix.length())) + 1;
        }
        return prefix + String.format("%02d", next);
    }

    private ProductionBoilingBatchTraceNodeVO node(String label, String type, String status, String summary) {
        ProductionBoilingBatchTraceNodeVO node = new ProductionBoilingBatchTraceNodeVO();
        node.setLabel(label);
        node.setType(type);
        node.setStatus(status);
        node.setSummary(summary);
        return node;
    }

    private String usageText(ProductionBoilingBatchUsageVO item) {
        return ("KG".equals(item.getUsageUnit()) ? format(item.getUsageQuantity()) + "kg" : format(item.getUsageQuantity()) + "桶")
                + "（" + usageStatusText(item.getStatus()) + "）";
    }

    private String usageStatusText(String status) {
        if (status == null) {
            return "未知状态";
        }
        return switch (status) {
            case USAGE_RESERVED -> "已预占";
            case USAGE_CONSUMED -> "已消耗";
            case USAGE_RELEASED -> "已释放";
            default -> "未知状态";
        };
    }

    private String format(BigDecimal value) {
        return nvl(value).stripTrailingZeros().toPlainString();
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? ProductionBoilingBatchQuantity.ZERO : value;
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}


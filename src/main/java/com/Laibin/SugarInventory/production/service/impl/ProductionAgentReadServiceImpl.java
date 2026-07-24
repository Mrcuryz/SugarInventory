package com.Laibin.SugarInventory.production.service.impl;

import com.Laibin.SugarInventory.agent.security.AgentEntityRefCodec;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.production.domain.dto.ProductionBoilingBatchQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionBoilingBatchListQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionBoilingBatchTraceQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionEntityResolveQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionOrderProgressQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionMaterialPickTraceQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionLabelCompletionQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionInProcessMaterialQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionInProcessMaterialsAgentQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionMaterialCandidatesAgentQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionMaterialCandidateQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionOrderQueryDTO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchListVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchTraceNodeVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchTraceVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchUsageVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionEntityCandidateVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionEntityResolutionVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionLabelBatchVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderBaseVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderDetailVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderPageVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderProgressVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionMaterialPickTraceVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionLabelCompletionVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionInProcessMaterialsVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionMaterialCandidatesVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionTraceOutputCodeRowVO;
import com.Laibin.SugarInventory.production.mapper.ProductionBoilingBatchTraceMapper;
import com.Laibin.SugarInventory.production.domain.vo.ProductionMaterialCandidateVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionMaterialVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOutputVO;
import com.Laibin.SugarInventory.production.service.ProductionAgentReadService;
import com.Laibin.SugarInventory.production.service.ProductionBoilingBatchService;
import com.Laibin.SugarInventory.production.service.ProductionOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductionAgentReadServiceImpl implements ProductionAgentReadService {
    static final String ORDER_TYPE = "PRODUCTION_ORDER";
    static final String BOILING_TYPE = "BOILING_BATCH";
    static final String ORDER_SCOPE = "production:order:view";
    static final String BOILING_SCOPE = "production:boiling:view";

    private final ProductionOrderService orderService;
    private final ProductionBoilingBatchService boilingBatchService;
    private final AgentEntityRefCodec refCodec;
    private final ProductionBoilingBatchTraceMapper traceMapper;

    @Override
    public ProductionEntityResolutionVO resolveEntities(ProductionEntityResolveQueryDTO query, int userId) {
        if (query == null || query.getQuery() == null || query.getQuery().isBlank()) {
            throw new BusinessException(400, "生产实体查询词不能为空");
        }
        String keyword = query.getQuery().trim();
        if (keyword.length() > 100) {
            throw new BusinessException(400, "生产实体查询词长度不能超过 100");
        }
        String entityType = normalizeType(query.getEntityType());
        int limit = query.getLimit() == null ? 5 : Math.max(1, Math.min(query.getLimit(), 10));
        List<ProductionEntityCandidateVO> candidates = ORDER_TYPE.equals(entityType)
                ? resolveOrders(keyword, limit, userId)
                : resolveBoilingBatches(keyword, limit, userId);
        String status = candidates.isEmpty() ? "NO_MATCH" : candidates.size() == 1 ? "EXACT" : "AMBIGUOUS";
        return ProductionEntityResolutionVO.builder()
                .resolutionStatus(status)
                .needsUserSelection(candidates.size() > 1)
                .entityType(entityType)
                .query(keyword)
                .candidates(candidates)
                .limitations(List.of("候选来自当前生产数据；entityRef 有效期较短且仅限当前用户和权限范围。"))
                .build();
    }

    @Override
    public ProductionOrderProgressVO queryOrderProgress(ProductionOrderProgressQueryDTO query, int userId) {
        if (query == null || query.getOrderRef() == null || query.getOrderRef().isBlank()) {
            throw new BusinessException(400, "orderRef 不能为空");
        }
        AgentEntityRefCodec.DecodedRef decoded = refCodec.decodeAndValidate(
                query.getOrderRef().trim(), ORDER_TYPE, userId, ORDER_SCOPE);
        ProductionOrderDetailVO detail = orderService.getOrderDetail(decoded.entityId());
        ProductionOrderBaseVO base = detail.getBaseInfo();
        List<ProductionOutputVO> outputs = safe(detail.getOutputs());
        List<ProductionLabelBatchVO> labels = safe(detail.getLabelBatches());
        List<ProductionBoilingBatchUsageVO> boilingSources = safe(detail.getBoilingSources());
        List<ProductionTraceOutputCodeRowVO> outputCodeRows = safe(
                traceMapper.listOutputCodeRowsByOrder(decoded.entityId()));
        return ProductionOrderProgressVO.builder()
                .dataScope("CURRENT_PRODUCTION_ORDER_PROGRESS")
                .orderRef(query.getOrderRef().trim())
                .orderNo(base.getOrderNo())
                .orderType(base.getOrderType())
                .status(base.getStatus())
                .productionDate(base.getProductionDate())
                .teamName(base.getTeamName())
                .plannedMaterialText(base.getPlannedMaterialText())
                .plannedOutputText(base.getPlannedOutputText())
                .materialRecordCount(safe(detail.getMaterials()).size())
                .outputRecordCount(outputs.size())
                .requiredQrCount(outputs.stream().map(ProductionOutputVO::getRequiredQrCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum())
                .boundQrCount(outputs.stream().map(ProductionOutputVO::getBoundQrCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum())
                .inboundQrCount(outputs.stream().map(ProductionOutputVO::getInboundQrCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum())
                .labelBatchCount(labels.size())
                .reservedLabelCount(sumLabels(labels, ProductionLabelBatchVO::getReservedCount))
                .usedLabelCount(sumLabels(labels, ProductionLabelBatchVO::getUsedCount))
                .recycledLabelCount(sumLabels(labels, ProductionLabelBatchVO::getRecycledCount))
                .boilingSources(boilingSources.stream().map(this::toSafeBoilingSource).toList())
                .outputs(outputs.stream().map(output -> toSafeOutputProgress(output, outputCodeRows)).toList())
                .updatedAt(base.getUpdatedAt())
                .completedAt(base.getCompletedAt())
                .limitations(List.of(
                        "本结果仅表示当前订单记录中的计划、领料、产出、标签和入库进度。",
                        "不计算产出率、损耗率或材料消耗差异，也不代表质量放行结论。"))
                .build();
    }

    private ProductionOrderProgressVO.BoilingSource toSafeBoilingSource(
            ProductionBoilingBatchUsageVO usage) {
        return ProductionOrderProgressVO.BoilingSource.builder()
                .batchNo(usage.getBatchNo())
                .usageUnit(usage.getUsageUnit())
                .usageQuantity(usage.getUsageQuantity())
                .bucketQuantity(usage.getBucketQuantity())
                .weightKg(usage.getWeightKg())
                .status(usage.getStatus())
                .build();
    }

    private ProductionOrderProgressVO.OutputProgress toSafeOutputProgress(
            ProductionOutputVO output,
            List<ProductionTraceOutputCodeRowVO> outputCodeRows) {
        Map<String, List<ProductionTraceOutputCodeRowVO>> inboundByWarehouse = outputCodeRows.stream()
                .filter(row -> Objects.equals(row.getOutputId(), output.getId()))
                .filter(this::isInboundOutputCode)
                .filter(row -> row.getWarehouseName() != null && !row.getWarehouseName().isBlank())
                .collect(java.util.stream.Collectors.groupingBy(
                        ProductionTraceOutputCodeRowVO::getWarehouseName,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        List<ProductionOrderProgressVO.InboundDestination> destinations = inboundByWarehouse.entrySet().stream()
                .map(entry -> ProductionOrderProgressVO.InboundDestination.builder()
                        .warehouseName(entry.getKey())
                        .inboundCodeCount(entry.getValue().size())
                        .palletCodes(entry.getValue().stream()
                                .map(ProductionTraceOutputCodeRowVO::getPalletCode)
                                .filter(Objects::nonNull)
                                .filter(value -> !value.isBlank())
                                .distinct()
                                .limit(20)
                                .toList())
                        .build())
                .toList();
        return ProductionOrderProgressVO.OutputProgress.builder()
                .productName(output.getProductName())
                .productStatus(output.getProductStatus())
                .boardCount(output.getBoardCount())
                .pieceCount(output.getPieceCount())
                .totalPieces(output.getTotalPieces())
                .totalWeight(output.getTotalWeight())
                .requiredQrCount(output.getRequiredQrCount())
                .boundQrCount(output.getBoundQrCount())
                .inboundQrCount(output.getInboundQrCount())
                .status(output.getStatus())
                .inboundDestinations(destinations)
                .build();
    }

    private boolean isInboundOutputCode(ProductionTraceOutputCodeRowVO row) {
        return row.getInventoryId() != null
                || row.getInboundAt() != null
                || "INSTOCK".equalsIgnoreCase(row.getCodeStatus());
    }

    @Override
    public ProductionBoilingBatchTraceVO queryBoilingBatchTrace(ProductionBoilingBatchTraceQueryDTO query, int userId) {
        if (query == null || query.getBatchRef() == null || query.getBatchRef().isBlank()) {
            throw new BusinessException(400, "batchRef 不能为空");
        }
        String batchRef = query.getBatchRef().trim();
        AgentEntityRefCodec.DecodedRef decoded = refCodec.decodeAndValidate(
                batchRef, BOILING_TYPE, userId, BOILING_SCOPE);
        ProductionBoilingBatchVO detail = boilingBatchService.getDetail(decoded.entityId());
        ProductionBoilingBatchTraceNodeVO trace = boilingBatchService.getTrace(decoded.entityId());
        List<ProductionBoilingBatchTraceNodeVO.TraceGraphNodeVO> rawNodes = safe(trace.getNodes());
        Map<String, String> safeNodeRefs = new LinkedHashMap<>();
        for (int index = 0; index < rawNodes.size(); index++) {
            String rawId = rawNodes.get(index).getId();
            if (rawId != null && !rawId.isBlank()) {
                safeNodeRefs.put(rawId, "trace_node_" + (index + 1));
            }
        }
        List<ProductionBoilingBatchTraceVO.Node> nodes = rawNodes.stream().map(node ->
                ProductionBoilingBatchTraceVO.Node.builder()
                        .nodeRef(safeNodeRefs.get(node.getId()))
                        .type(node.getType())
                        .name(node.getName())
                        .documentNo(node.getDocumentNo())
                        .productName(node.getProductName())
                        .quantityText(node.getQuantityText())
                        .warehouseName(node.getWarehouseName())
                        .status(node.getStatus())
                        .occurredAt(node.getOccurredAt())
                        .build()).toList();
        List<ProductionBoilingBatchTraceVO.Edge> edges = safe(trace.getEdges()).stream()
                .filter(edge -> safeNodeRefs.containsKey(edge.getSource()) && safeNodeRefs.containsKey(edge.getTarget()))
                .map(edge -> ProductionBoilingBatchTraceVO.Edge.builder()
                        .sourceNodeRef(safeNodeRefs.get(edge.getSource()))
                        .targetNodeRef(safeNodeRefs.get(edge.getTarget()))
                        .action(edge.getAction())
                        .label(edge.getLabel())
                        .quantityText(edge.getQuantityText())
                        .status(edge.getStatus())
                        .build()).toList();
        List<ProductionBoilingBatchTraceVO.Usage> usages = safe(detail.getUsages()).stream()
                .map(this::toSafeUsage).toList();
        List<ProductionBoilingBatchTraceVO.TimelineRecord> timeline = safe(trace.getTimeline()).stream()
                .map(item -> ProductionBoilingBatchTraceVO.TimelineRecord.builder()
                        .occurredAt(item.getOccurredAt())
                        .actionType(item.getActionType())
                        .documentNo(item.getDocumentNo())
                        .productName(item.getProductName())
                        .quantityText(item.getQuantityText())
                        .warehouseName(item.getWarehouseName())
                        .relatedObject(item.getRelatedObject())
                        .status(item.getStatus())
                        .build()).toList();
        return ProductionBoilingBatchTraceVO.builder()
                .dataScope("REGISTERED_BOILING_BATCH_TRACE")
                .batchRef(batchRef)
                .batchNo(detail.getBatchNo())
                .boilingDate(detail.getBoilingDate())
                .sugarType(detail.getSugarType())
                .productName(detail.getProductName())
                .status(detail.getStatus())
                .totalWeightKg(detail.getTotalWeightKg())
                .reservedWeightKg(detail.getReservedWeightKg())
                .consumedWeightKg(detail.getConsumedWeightKg())
                .remainingWeightKg(detail.getRemainingWeightKg())
                .usageCount(usages.size())
                .nodeCount(nodes.size())
                .edgeCount(edges.size())
                .usages(usages)
                .nodes(nodes)
                .edges(edges)
                .timeline(timeline)
                .limitations(List.of(
                        "仅展示系统已登记的煮糖批次详情、使用记录、追溯节点和关系边。"))
                .build();
    }

    @Override
    public ProductionBoilingBatchListVO queryBoilingBatches(ProductionBoilingBatchListQueryDTO query, int userId) {
        ProductionBoilingBatchListQueryDTO source = query == null ? new ProductionBoilingBatchListQueryDTO() : query;
        if (source.getStartDate() != null && source.getEndDate() != null
                && source.getStartDate().isAfter(source.getEndDate())) {
            throw new BusinessException(400, "startDate 不能晚于 endDate");
        }
        String status = source.getStatus() == null ? null : source.getStatus().trim().toUpperCase(Locale.ROOT);
        if (status != null && !status.isBlank()
                && !Set.of("AVAILABLE", "USED_UP", "CANCELED").contains(status)) {
            throw new BusinessException(400, "status 仅支持 AVAILABLE、USED_UP 或 CANCELED");
        }
        int limit = source.getLimit() == null ? 10 : Math.max(1, Math.min(source.getLimit(), 20));
        ProductionBoilingBatchQueryDTO pageQuery = new ProductionBoilingBatchQueryDTO();
        pageQuery.setProductQuery(source.getProductQuery() == null ? null : source.getProductQuery().trim());
        pageQuery.setStartDate(source.getStartDate());
        pageQuery.setEndDate(source.getEndDate());
        pageQuery.setStatus(status);
        pageQuery.setPage(1);
        pageQuery.setSize(limit);
        PageResult<ProductionBoilingBatchVO> page = boilingBatchService.pageBatches(pageQuery);
        List<ProductionEntityCandidateVO> candidates = safe(page.getRecords()).stream()
                .map(batch -> ProductionEntityCandidateVO.builder()
                        .entityRef(refCodec.encode(BOILING_TYPE, batch.getId(), userId, BOILING_SCOPE))
                        .entityType(BOILING_TYPE)
                        .displayCode(batch.getBatchNo())
                        .status(batch.getStatus())
                        .businessDate(batch.getBoilingDate())
                        .summary(joinSummary(batch.getProductName(), batch.getSugarType(), batch.getTeamName(),
                                batch.getTotalWeightKg() == null ? null : batch.getTotalWeightKg().stripTrailingZeros().toPlainString() + " kg"))
                        .build())
                .toList();
        String productLabel = pageQuery.getProductQuery() == null || pageQuery.getProductQuery().isBlank()
                ? "全部产品" : pageQuery.getProductQuery();
        String dateLabel = source.getStartDate() == null && source.getEndDate() == null
                ? "全部日期"
                : (source.getStartDate() == null ? "截至 " + source.getEndDate()
                : source.getEndDate() == null ? source.getStartDate() + " 起"
                : source.getStartDate().equals(source.getEndDate()) ? source.getStartDate().toString()
                : source.getStartDate() + " 至 " + source.getEndDate());
        return ProductionBoilingBatchListVO.builder()
                .dataScope("BOILING_BATCH_LIST")
                .scopeLabel(productLabel)
                .dateRangeLabel(dateLabel)
                .total(page.getTotal() == null ? candidates.size() : page.getTotal())
                .candidates(candidates)
                .limitations(List.of(
                        "仅返回当前筛选范围内已登记的煮糖批次，不推断缺失记录。",
                        "候选项中的 batchRef 仅用于后续只读详情查询。"))
                .build();
    }

    private ProductionBoilingBatchTraceVO.Usage toSafeUsage(ProductionBoilingBatchUsageVO usage) {
        return ProductionBoilingBatchTraceVO.Usage.builder()
                .orderNo(usage.getOrderNo())
                .orderType(usage.getOrderType())
                .orderStatus(usage.getOrderStatus())
                .usageUnit(usage.getUsageUnit())
                .usageQuantity(usage.getUsageQuantity())
                .bucketQuantity(usage.getBucketQuantity())
                .weightKg(usage.getWeightKg())
                .status(usage.getStatus())
                .createdAt(usage.getCreatedAt())
                .build();
    }

    @Override
    public ProductionMaterialPickTraceVO queryMaterialPickTrace(ProductionMaterialPickTraceQueryDTO query, int userId) {
        if (query == null || query.getOrderRef() == null || query.getOrderRef().isBlank()) {
            throw new BusinessException(400, "orderRef 不能为空");
        }
        String orderRef = query.getOrderRef().trim();
        AgentEntityRefCodec.DecodedRef decoded = refCodec.decodeAndValidate(
                orderRef, ORDER_TYPE, userId, ORDER_SCOPE);
        ProductionOrderDetailVO detail = orderService.getOrderDetail(decoded.entityId());
        ProductionOrderBaseVO base = detail.getBaseInfo();
        List<ProductionMaterialPickTraceVO.MaterialRecord> records = safe(detail.getMaterials()).stream()
                .map(this::toMaterialRecord).toList();
        return ProductionMaterialPickTraceVO.builder()
                .dataScope("REGISTERED_MATERIAL_PICK_TRACE")
                .orderRef(orderRef)
                .orderNo(base.getOrderNo())
                .orderStatus(base.getStatus())
                .materialRecordCount(records.size())
                .records(records)
                .limitations(List.of(
                        "仅展示生产订单中已登记的实际领料记录及托盘来源。",
                        "不计算计划差异、损耗、实际消耗率，也不推断未登记的退料或替代用料。"))
                .build();
    }

    private ProductionMaterialPickTraceVO.MaterialRecord toMaterialRecord(ProductionMaterialVO material) {
        String position = joinPosition(material.getSide(), material.getRowNumber(), material.getLayer());
        return ProductionMaterialPickTraceVO.MaterialRecord.builder()
                .palletCode(material.getPalletCode())
                .productName(material.getProductName())
                .productStatus(material.getProductStatus())
                .productionDate(material.getProductionDate())
                .warehouseName(material.getWarehouseName())
                .positionText(position)
                .quantity(material.getQuantity())
                .unit(material.getUnit())
                .pieces(material.getPieces())
                .totalPieces(material.getTotalPieces())
                .totalWeight(material.getTotalWeight())
                .status(material.getStatus())
                .pickedByName(material.getPickedByName())
                .pickedAt(material.getPickedAt())
                .remark(material.getRemark())
                .build();
    }

    private String joinPosition(String side, Integer row, Integer layer) {
        List<String> parts = new java.util.ArrayList<>();
        if (side != null && !side.isBlank()) {
            parts.add(side + "侧");
        }
        if (row != null) {
            parts.add(row + "排");
        }
        if (layer != null) {
            parts.add(layer + "层");
        }
        return parts.isEmpty() ? null : String.join("", parts);
    }

    @Override
    public ProductionLabelCompletionVO queryProductionLabelCompletion(ProductionLabelCompletionQueryDTO query,
                                                                       int userId) {
        if (query == null || query.getOrderRef() == null || query.getOrderRef().isBlank()) {
            throw new BusinessException(400, "orderRef 不能为空");
        }
        String orderRef = query.getOrderRef().trim();
        AgentEntityRefCodec.DecodedRef decoded = refCodec.decodeAndValidate(
                orderRef, ORDER_TYPE, userId, ORDER_SCOPE);
        ProductionOrderDetailVO detail = orderService.getOrderDetail(decoded.entityId());
        ProductionOrderBaseVO base = detail.getBaseInfo();
        List<ProductionLabelBatchVO> batches = safe(detail.getLabelBatches());
        List<ProductionOutputVO> outputs = safe(detail.getOutputs());
        int reserved = sumLabels(batches, ProductionLabelBatchVO::getReservedCount);
        int used = sumLabels(batches, ProductionLabelBatchVO::getUsedCount);
        int recycled = sumLabels(batches, ProductionLabelBatchVO::getRecycledCount);
        int requiredQr = sumOutputs(outputs, ProductionOutputVO::getRequiredQrCount);
        int boundQr = sumOutputs(outputs, ProductionOutputVO::getBoundQrCount);
        int inboundQr = sumOutputs(outputs, ProductionOutputVO::getInboundQrCount);
        return ProductionLabelCompletionVO.builder()
                .dataScope("CURRENT_PRODUCTION_LABEL_COMPLETION")
                .orderRef(orderRef)
                .orderNo(base.getOrderNo())
                .orderStatus(base.getStatus())
                .labelBatchCount(batches.size())
                .reservedLabelCount(reserved)
                .usedLabelCount(used)
                .recycledLabelCount(recycled)
                .requiredQrCount(requiredQr)
                .boundQrCount(boundQr)
                .inboundQrCount(inboundQr)
                .notBoundQrCount(Math.max(0, requiredQr - boundQr))
                .notInboundQrCount(Math.max(0, requiredQr - inboundQr))
                .batches(batches.stream().map(this::toSafeLabelBatch).toList())
                .limitations(List.of(
                        "标签预留、使用、回收与二维码需求、绑定、入库是不同阶段，分别统计。",
                        "printedAt 仅表示标签批次记录了打印时间，不代表二维码已绑定或已入库。"))
                .build();
    }

    @Override
    public ProductionInProcessMaterialsVO queryInProcessMaterials(ProductionInProcessMaterialsAgentQueryDTO query) {
        ProductionInProcessMaterialsAgentQueryDTO source = query == null
                ? new ProductionInProcessMaterialsAgentQueryDTO() : query;
        int page = source.getPage() == null ? 1 : source.getPage();
        int size = source.getSize() == null ? 20 : source.getSize();
        if (page < 1 || size < 1 || size > 50) {
            throw new BusinessException(400, "page 必须大于等于 1，size 必须在 1 到 50 之间");
        }
        if (source.getProductionDateStart() != null && source.getProductionDateEnd() != null
                && source.getProductionDateStart().isAfter(source.getProductionDateEnd())) {
            throw new BusinessException(400, "生产日期起不能晚于生产日期止");
        }
        ProductionInProcessMaterialQueryDTO internal = new ProductionInProcessMaterialQueryDTO();
        internal.setProductName(trimToNull(source.getProductName(), 100, "productName"));
        internal.setProductType(trimToNull(source.getProductType(), 50, "productType"));
        internal.setProductionDateStart(source.getProductionDateStart());
        internal.setProductionDateEnd(source.getProductionDateEnd());
        internal.setPage(page);
        internal.setSize(size);
        PageResult<ProductionMaterialVO> result = orderService.pageInProcessMaterials(internal);
        return ProductionInProcessMaterialsVO.builder()
                .dataScope("CURRENT_REGISTERED_IN_PROCESS_MATERIALS")
                .total(result.getTotal() == null ? 0 : result.getTotal())
                .page(page)
                .size(size)
                .records(safe(result.getRecords()).stream().map(this::toSafeInProcessMaterial).toList())
                .limitations(List.of(
                        "仅展示未取消且所属生产订单未完成、未取消的已登记半成品领料记录。",
                        "在制记录不代表仍可再次领用、质量已放行、FIFO/FEFO 推荐或实时库存结余。"))
                .build();
    }

    private ProductionInProcessMaterialsVO.Row toSafeInProcessMaterial(ProductionMaterialVO material) {
        return ProductionInProcessMaterialsVO.Row.builder()
                .orderNo(material.getOrderNo()).orderStatus(material.getOrderStatus())
                .palletCode(material.getPalletCode()).productName(material.getProductName())
                .productStatus(material.getProductStatus()).productionDate(material.getProductionDate())
                .warehouseName(material.getWarehouseName())
                .positionText(joinPosition(material.getSide(), material.getRowNumber(), material.getLayer()))
                .quantity(material.getQuantity()).unit(material.getUnit()).pieces(material.getPieces())
                .totalPieces(material.getTotalPieces()).totalWeight(material.getTotalWeight())
                .materialStatus(material.getStatus()).pickedByName(material.getPickedByName())
                .pickedAt(material.getPickedAt()).build();
    }

    @Override
    public ProductionMaterialCandidatesVO queryMaterialCandidates(ProductionMaterialCandidatesAgentQueryDTO query, int userId) {
        if (query == null || query.getOrderRef() == null || query.getOrderRef().isBlank()) {
            throw new BusinessException(400, "orderRef 不能为空");
        }
        int page = query.getPage() == null ? 1 : query.getPage();
        int size = query.getSize() == null ? 20 : query.getSize();
        if (page < 1 || size < 1 || size > 50) {
            throw new BusinessException(400, "page 必须大于等于 1，size 必须在 1 到 50 之间");
        }
        String orderRef = query.getOrderRef().trim();
        AgentEntityRefCodec.DecodedRef decoded = refCodec.decodeAndValidate(orderRef, ORDER_TYPE, userId, ORDER_SCOPE);
        ProductionMaterialCandidateQueryDTO internal = new ProductionMaterialCandidateQueryDTO();
        internal.setPage(page);
        internal.setSize(size);
        PageResult<ProductionMaterialCandidateVO> result = orderService.pageMaterialCandidates(decoded.entityId(), internal);
        return ProductionMaterialCandidatesVO.builder()
                .dataScope("CURRENT_MATERIAL_CANDIDATE_INVENTORY")
                .orderRef(orderRef).total(result.getTotal() == null ? 0 : result.getTotal())
                .page(page).size(size)
                .records(safe(result.getRecords()).stream().map(item -> ProductionMaterialCandidatesVO.Row.builder()
                        .palletCode(item.getPalletCode()).productName(item.getProductName())
                        .productStatus(item.getProductStatus()).productionDate(item.getProductionDate())
                        .quantityText(item.getQuantityText()).quantity(item.getQuantity()).unit(item.getUnit())
                        .pieces(item.getPieces()).warehouseName(item.getWarehouseName())
                        .positionText(joinPosition(item.getSide(), item.getRowNumber(), item.getLayer()))
                        .weight(item.getWeight()).build()).toList())
                .limitations(List.of(
                        "候选仅表示当前半成品库存查询口径下可展示的记录，不代表已领用或已为该订单预留。",
                        "返回顺序不得解释为 Agent 的 FIFO/FEFO 推荐、质量放行或最终领料决策。"))
                .build();
    }

    private String trimToNull(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) throw new BusinessException(400, field + " 长度超限");
        return trimmed;
    }

    private ProductionLabelCompletionVO.LabelBatch toSafeLabelBatch(ProductionLabelBatchVO batch) {
        return ProductionLabelCompletionVO.LabelBatch.builder()
                .batchNo(batch.getBatchNo())
                .productName(batch.getProductName())
                .reservedCount(batch.getReservedCount())
                .usedCount(batch.getUsedCount())
                .recycledCount(batch.getRecycledCount())
                .status(batch.getStatus())
                .printedAt(batch.getPrintedAt())
                .closedAt(batch.getClosedAt())
                .createdAt(batch.getCreatedAt())
                .build();
    }

    private int sumOutputs(List<ProductionOutputVO> outputs,
                           java.util.function.Function<ProductionOutputVO, Integer> getter) {
        return outputs.stream().map(getter).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
    }

    private List<ProductionEntityCandidateVO> resolveOrders(String keyword, int limit, int userId) {
        ProductionOrderQueryDTO query = new ProductionOrderQueryDTO();
        query.setOrderNo(keyword);
        query.setPage(1);
        query.setSize(limit);
        PageResult<ProductionOrderPageVO> page = orderService.pageOrders(query);
        return safe(page.getRecords()).stream().map(order -> ProductionEntityCandidateVO.builder()
                .entityRef(refCodec.encode(ORDER_TYPE, order.getId(), userId, ORDER_SCOPE))
                .entityType(ORDER_TYPE)
                .displayCode(order.getOrderNo())
                .status(order.getStatus())
                .businessDate(order.getProductionDate())
                .summary(joinSummary(order.getOrderType(), order.getTeamName(), order.getPlannedOutputText()))
                .build()).toList();
    }

    private List<ProductionEntityCandidateVO> resolveBoilingBatches(String keyword, int limit, int userId) {
        ProductionBoilingBatchQueryDTO query = new ProductionBoilingBatchQueryDTO();
        query.setBatchNo(keyword);
        query.setPage(1);
        query.setSize(limit);
        PageResult<ProductionBoilingBatchVO> page = boilingBatchService.pageBatches(query);
        return safe(page.getRecords()).stream().map(batch -> ProductionEntityCandidateVO.builder()
                .entityRef(refCodec.encode(BOILING_TYPE, batch.getId(), userId, BOILING_SCOPE))
                .entityType(BOILING_TYPE)
                .displayCode(batch.getBatchNo())
                .status(batch.getStatus())
                .businessDate(batch.getBoilingDate())
                .summary(joinSummary(batch.getSugarType(), batch.getProductName(), batch.getTeamName()))
                .build()).toList();
    }

    private String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!ORDER_TYPE.equals(normalized) && !BOILING_TYPE.equals(normalized)) {
            throw new BusinessException(400, "entityType 仅支持 PRODUCTION_ORDER 或 BOILING_BATCH");
        }
        return normalized;
    }

    private String joinSummary(String... values) {
        return java.util.Arrays.stream(values).filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + "；" + right).orElse(null);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private int sumLabels(List<ProductionLabelBatchVO> labels,
                          java.util.function.Function<ProductionLabelBatchVO, Integer> getter) {
        return labels.stream().map(getter).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
    }
}

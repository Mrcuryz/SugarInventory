package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.PalletTaskAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PalletTaskQueryDTO;
import com.Laibin.SugarInventory.domain.vo.PalletTaskPageVO;
import com.Laibin.SugarInventory.domain.vo.PalletTasksAgentVO;
import com.Laibin.SugarInventory.domain.dto.StockDocumentAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.InStockQueryDTO;
import com.Laibin.SugarInventory.domain.dto.OutRecordQueryDTO;
import com.Laibin.SugarInventory.domain.dto.SemiProductRecordDTO;
import com.Laibin.SugarInventory.domain.po.PalletCode;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.InStockVO;
import com.Laibin.SugarInventory.domain.vo.OutStockRecordVO;
import com.Laibin.SugarInventory.domain.vo.PalletInventoryVO;
import com.Laibin.SugarInventory.domain.vo.RecordDetailVO;
import com.Laibin.SugarInventory.domain.vo.StockDocumentsAgentVO;
import com.Laibin.SugarInventory.service.LogisticsAgentReadService;
import com.Laibin.SugarInventory.service.PalletCodeService;
import com.Laibin.SugarInventory.service.InStockService;
import com.Laibin.SugarInventory.service.OutStockService;
import com.Laibin.SugarInventory.service.SemiProductRecordService;
import com.Laibin.SugarInventory.service.AutoInboundParseService;
import com.Laibin.SugarInventory.agent.security.AutoInboundBatchRefCodec;
import com.Laibin.SugarInventory.domain.dto.AutoInboundBatchesAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AutoInboundBatchDetailAgentQueryDTO;
import com.Laibin.SugarInventory.domain.redis.AutoInboundTask;
import com.Laibin.SugarInventory.domain.vo.AutoInboundBatchOptionVO;
import com.Laibin.SugarInventory.domain.vo.AutoInboundBatchDetailAgentVO;
import com.Laibin.SugarInventory.domain.vo.AutoInboundBatchesAgentVO;
import com.Laibin.SugarInventory.domain.dto.TaskTransitionPreviewDTO;
import com.Laibin.SugarInventory.domain.vo.TaskTransitionPreviewVO;
import com.Laibin.SugarInventory.agent.security.TaskTransitionPreviewRefCodec;
import com.Laibin.SugarInventory.domain.vo.AutoInboundParseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LogisticsAgentReadServiceImpl implements LogisticsAgentReadService {
    private static final Set<String> TASK_TYPES = Set.of("IN", "SEMI_IN", "FINISH_IN", "OUT", "TRANSFER");
    private static final Set<String> BIZ_SCENES = Set.of("DIRECT_OUT", "PREPARE_CONSUMED", "FINISH_OUT");
    private static final Set<String> STATUSES = Set.of("PENDING", "CONFIRMED", "CANCELED");
    private static final Set<String> PRODUCT_STATUSES = Set.of("半成品", "成品");

    private final PalletCodeService palletCodeService;
    private final InStockService inStockService;
    private final OutStockService outStockService;
    private final SemiProductRecordService semiProductRecordService;
    private final AutoInboundParseService autoInboundParseService;
    private final AutoInboundBatchRefCodec autoInboundBatchRefCodec;
    private final TaskTransitionPreviewRefCodec taskTransitionPreviewRefCodec;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int TASK_PREVIEW_VERSION = 1;
    private static final int TASK_PREVIEW_TTL_MINUTES = 5;
    private static final String FINISH_INBOUND_TRANSITION = "CONFIRM_FINISH_INBOUND";
    private static final String FINISH_OUTBOUND_TRANSITION = "CONFIRM_FINISH_OUTBOUND";

    @Override
    public PalletTasksAgentVO queryPalletTasks(PalletTaskAgentQueryDTO query) {
        PalletTaskAgentQueryDTO source = query == null ? new PalletTaskAgentQueryDTO() : query;
        int page = source.getPage() == null ? 1 : source.getPage();
        int size = source.getSize() == null ? 20 : source.getSize();
        if (page < 1 || size < 1 || size > 50) throw new BusinessException(400, "分页参数超出允许范围");
        if (source.getProductionDateStart() != null && source.getProductionDateEnd() != null
                && source.getProductionDateStart().isAfter(source.getProductionDateEnd())) {
            throw new BusinessException(400, "生产日期起不能晚于生产日期止");
        }
        PalletTaskQueryDTO internal = new PalletTaskQueryDTO();
        internal.setCode(text(source.getCode(), 100, "code"));
        internal.setTaskType(enumValue(source.getTaskType(), TASK_TYPES, "taskType"));
        internal.setBizScene(enumValue(source.getBizScene(), BIZ_SCENES, "bizScene"));
        internal.setStatus(enumValue(source.getStatus(), STATUSES, "status"));
        internal.setProductName(text(source.getProductName(), 100, "productName"));
        internal.setProductType(text(source.getProductType(), 50, "productType"));
        internal.setProductStatus(enumValue(source.getProductStatus(), PRODUCT_STATUSES, "productStatus"));
        internal.setTargetWarehouseName(text(source.getTargetWarehouseName(), 100, "targetWarehouseName"));
        internal.setProductionDateStart(source.getProductionDateStart());
        internal.setProductionDateEnd(source.getProductionDateEnd());
        internal.setPageNum((long) page);
        internal.setPageSize((long) size);
        PageResult<PalletTaskPageVO> result = palletCodeService.pagePalletTasks(internal);
        return PalletTasksAgentVO.builder()
                .dataScope("CURRENT_PALLET_TASKS")
                .total(result.getTotal() == null ? 0 : result.getTotal()).page(page).size(size)
                .records((result.getRecords() == null ? List.<PalletTaskPageVO>of() : result.getRecords())
                        .stream().map(this::safeRow).toList())
                .limitations(List.of("仅展示当前托盘轮次中的任务记录。", "结果不执行任务确认、取消、入库、出库或调拨。"))
                .build();
    }

    private PalletTasksAgentVO.Row safeRow(PalletTaskPageVO row) {
        return PalletTasksAgentVO.Row.builder()
                .taskType(row.getTaskType()).bizScene(row.getBizScene()).taskStatus(row.getTaskStatus())
                .code(row.getCode()).operationBatchNo(row.getOperationBatchNo())
                .targetWarehouseName(row.getTargetWarehouseName()).targetSide(row.getTargetSide())
                .productName(row.getProductName()).productType(row.getProductType()).productStatus(row.getProductStatus())
                .totalWeight(row.getTotalWeight()).productionDate(row.getProductionDate())
                .screenMeshName(row.getScreenMeshName()).semiItemCount(row.getSemiItemCount())
                .productionOrderNo(row.getProductionOrderNo()).productionOrderType(row.getProductionOrderType())
                .productionOrderStatus(row.getProductionOrderStatus()).productionLabelBatchNo(row.getProductionLabelBatchNo())
                .createdBy(row.getCreatedBy()).createdAt(row.getCreatedAt())
                .confirmedBy(row.getConfirmedBy()).confirmedAt(row.getConfirmedAt()).build();
    }

    private String text(String value, int max, String field) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > max) throw new BusinessException(400, field + " 长度超限");
        return result;
    }

    private String enumValue(String value, Set<String> allowed, String field) {
        String result = text(value, 50, field);
        if (result != null && !allowed.contains(result)) throw new BusinessException(400, field + " 不受支持");
        return result;
    }

    @Override
    public StockDocumentsAgentVO queryStockDocuments(StockDocumentAgentQueryDTO query, User user) {
        if (query == null) throw new BusinessException(400, "查询参数不能为空");
        String type = enumValue(query.getDocumentType(), Set.of("INBOUND", "OUTBOUND", "SEMI_PRODUCT"), "documentType");
        if (type == null) throw new BusinessException(400, "documentType 不能为空");
        int page = query.getPage() == null ? 1 : query.getPage();
        int size = query.getSize() == null ? 20 : query.getSize();
        if (page < 1 || size < 1 || size > 50) throw new BusinessException(400, "分页参数超出允许范围");
        if (query.getStartDate() != null && query.getEndDate() != null && query.getStartDate().isAfter(query.getEndDate())) {
            throw new BusinessException(400, "开始日期不能晚于结束日期");
        }
        String product = text(query.getProductName(), 100, "productName");
        String warehouse = text(query.getWarehouseName(), 100, "warehouseName");
        String operator = text(query.getOperatorName(), 100, "operatorName");
        PageResult<?> raw;
        List<StockDocumentsAgentVO.Row> rows;
        if ("INBOUND".equals(type)) {
            InStockQueryDTO dto = new InStockQueryDTO();
            dto.setProductName(product); dto.setWarehouseName(warehouse); dto.setOperatorName(operator);
            dto.setStartDate(query.getStartDate()); dto.setEndDate(query.getEndDate()); dto.setPage(page); dto.setSize(size);
            PageResult<InStockVO> result = inStockService.queryInStockRecords(dto, user); raw = result;
            rows = safe(result.getRecords()).stream().map(item -> StockDocumentsAgentVO.Row.builder()
                    .documentType(type).productName(item.getProductName()).warehouseName(item.getWarehouseName())
                    .quantity(item.getQuantity()).unit(item.getUnit()).totalWeight(item.getTotalWeight())
                    .businessDate(item.getEntryDate()).operatorName(item.getOperator()).screenMeshName(item.getMeshName())
                    .createdAt(item.getCreatedAt()).build()).toList();
        } else if ("OUTBOUND".equals(type)) {
            OutRecordQueryDTO dto = new OutRecordQueryDTO();
            dto.setProductName(product); dto.setWarehouseName(warehouse); dto.setOperatorName(operator);
            dto.setStartDate(query.getStartDate() == null ? null : java.sql.Date.valueOf(query.getStartDate()));
            dto.setEndDate(query.getEndDate() == null ? null : java.sql.Date.valueOf(query.getEndDate()));
            dto.setPage(page); dto.setSize(size);
            PageResult<OutStockRecordVO> result = outStockService.searchOutRecords(dto, user); raw = result;
            rows = safe(result.getRecords()).stream().map(item -> StockDocumentsAgentVO.Row.builder()
                    .documentType(type).productName(item.getProductName()).warehouseName(item.getWarehouseName())
                    .quantity(item.getQuantity()).pieces(item.getPieces()).unit(item.getUnit()).totalWeight(item.getTotalWeight())
                    .businessDate(item.getOutDate()).sourceEntryDate(item.getInDate()).operatorName(item.getOperator())
                    .createdAt(item.getCreatedAt()).build()).toList();
        } else {
            SemiProductRecordDTO dto = new SemiProductRecordDTO();
            dto.setProductName(product); dto.setWarehouseName(warehouse); dto.setOperatorName(operator);
            dto.setStartDate(query.getStartDate()); dto.setEndDate(query.getEndDate()); dto.setPage(page); dto.setSize(size);
            PageResult<RecordDetailVO> result = semiProductRecordService.getSemiProductRecords(dto, user); raw = result;
            rows = safe(result.getRecords()).stream().map(item -> StockDocumentsAgentVO.Row.builder()
                    .documentType(type).productName(item.getProductName()).warehouseName(item.getWarehouseName())
                    .quantity(item.getQuantity()).unit(item.getUnit()).totalWeight(item.getTotalWeight())
                    .businessDate(item.getOperationDate()).operatorName(item.getOperator()).screenMeshName(item.getMeshName())
                    .createdAt(item.getCreatedAt()).build()).toList();
        }
        return StockDocumentsAgentVO.builder().dataScope("RECORDED_STOCK_DOCUMENTS").documentType(type)
                .total(raw.getTotal() == null ? 0 : raw.getTotal()).page(page).size(size).records(rows)
                .limitations(List.of("每次查询只覆盖一种明确单据类型，不合并不同来源后伪造统一流水顺序。",
                        "结果仅表示系统已登记单据，不重建缺失的历史库存事件，也不执行任何库存操作。"))
                .build();
    }

    private <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }

    @Override
    public AutoInboundBatchesAgentVO queryAutoInboundBatches(AutoInboundBatchesAgentQueryDTO query, User user) {
        requireUser(user);
        AutoInboundBatchesAgentQueryDTO source = query == null ? new AutoInboundBatchesAgentQueryDTO() : query;
        String status = text(source.getStatus(), 50, "status");
        int limit = source.getLimit() == null ? 20 : source.getLimit();
        if (limit < 1 || limit > 20) throw new BusinessException(400, "limit 必须在 1 到 20 之间");
        List<AutoInboundBatchesAgentVO.Row> rows = safe(autoInboundParseService.listBatches(user)).stream()
                .filter(item -> status == null || status.equalsIgnoreCase(item.getStatus()))
                .limit(limit)
                .map(item -> safeBatchRow(item, user.getId()))
                .toList();
        return AutoInboundBatchesAgentVO.builder()
                .dataScope("CURRENT_USER_RECENT_AUTO_INBOUND_BATCHES")
                .count(rows.size()).records(rows)
                .limitations(List.of("仅覆盖当前用户在 Redis 中尚未过期的最近智能报数批次，不代表长期历史台账。",
                        "结果仅供查询，不执行批次确认、自动入库或库存变更。"))
                .build();
    }

    @Override
    public AutoInboundBatchDetailAgentVO getAutoInboundBatchDetail(AutoInboundBatchDetailAgentQueryDTO query, User user) {
        requireUser(user);
        if (query == null || query.getBatchRef() == null || query.getBatchRef().isBlank()) {
            throw new BusinessException(400, "batchRef 不能为空");
        }
        String batchRef = query.getBatchRef().trim();
        if (batchRef.length() > 100 || !batchRef.startsWith("aibr_")) {
            throw new BusinessException(400, "智能报数批次引用无效或已过期");
        }
        String batchId = safe(autoInboundParseService.listBatches(user)).stream()
                .map(AutoInboundBatchOptionVO::getBatchId)
                .filter(id -> id != null && autoInboundBatchRefCodec.matches(batchRef, id, user.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "智能报数批次不存在、已过期或不属于当前用户"));
        AutoInboundParseResponse response = autoInboundParseService.getBatch(batchId);
        List<AutoInboundBatchDetailAgentVO.Task> tasks = safe(response == null ? null : response.getTasks()).stream()
                .map(this::safeAutoInboundTask).toList();
        return AutoInboundBatchDetailAgentVO.builder()
                .dataScope("CURRENT_USER_AUTO_INBOUND_BATCH_DETAIL")
                .batchRef(batchRef).taskCount(tasks.size()).tasks(tasks)
                .globalRemarks(safe(response == null ? null : response.getGlobalRemarks()))
                .limitations(List.of("详情不包含原始报数文本、内部批次 ID、任务 ID、产品 ID 或库位 ID。",
                        "解析结果和风险提示不等同于入库确认、质量放行或库存事实。"))
                .build();
    }

    @Override
    public TaskTransitionPreviewVO previewTaskTransition(TaskTransitionPreviewDTO request, User user) {
        requireUser(user);
        if (request == null || !Integer.valueOf(TASK_PREVIEW_VERSION).equals(request.getPreviewVersion())) {
            throw new BusinessException(400, "当前仅支持第 1 版任务预览协议");
        }
        if (FINISH_OUTBOUND_TRANSITION.equals(request.getTransition())) {
            return previewFinishOutboundTasks(request, user);
        }
        if (!FINISH_INBOUND_TRANSITION.equals(request.getTransition())) {
            throw new BusinessException(400, "当前仅支持成品入库或成品出库任务预览");
        }
        List<String> requestedCodes = normalizePreviewCodes(request.getPalletCodes());

        PalletTaskQueryDTO query = new PalletTaskQueryDTO();
        query.setCodes(String.join(",", requestedCodes));
        query.setTaskType("FINISH_IN");
        query.setStatus("PENDING");
        query.setPageNum(1L);
        query.setPageSize((long) requestedCodes.size());
        PageResult<PalletTaskPageVO> current = palletCodeService.pagePalletTasks(query);

        Map<String, PalletTaskPageVO> rowsByCode = new LinkedHashMap<>();
        for (PalletTaskPageVO row : safe(current.getRecords())) {
            String code = row.getCode() == null ? null : row.getCode().trim().toUpperCase(Locale.ROOT);
            if (code != null && requestedCodes.contains(code)) rowsByCode.putIfAbsent(code, row);
        }
        List<String> blockingIssues = requestedCodes.stream()
                .filter(code -> !rowsByCode.containsKey(code))
                .map(code -> "托盘 " + code + " 的成品入库任务已不存在、状态已变化或不在当前轮次。")
                .toList();
        boolean ready = blockingIssues.isEmpty() && rowsByCode.size() == requestedCodes.size();
        List<PalletTaskPageVO> orderedRows = requestedCodes.stream()
                .map(rowsByCode::get).filter(java.util.Objects::nonNull).toList();
        String stateDigest = digestTaskState(orderedRows, request.getTransition());
        LocalDateTime previewedAt = LocalDateTime.now(BUSINESS_ZONE);
        LocalDateTime expiresAt = previewedAt.plusMinutes(TASK_PREVIEW_TTL_MINUTES);
        String previewRef = ready
                ? taskTransitionPreviewRefCodec.encode(user.getId(), stateDigest, expiresAt)
                : null;
        List<String> warnings = new ArrayList<>();
        if (orderedRows.stream().anyMatch(row -> row.getTargetWarehouseName() == null || row.getTargetWarehouseName().isBlank())) {
            warnings.add("部分任务尚未预设入库库位，需要在业务弹窗中选择。");
        }
        warnings.add("业务弹窗打开和最终提交时都会重新检查任务状态；任务变化后需要重新预览。");

        return TaskTransitionPreviewVO.builder()
                .dataScope("CURRENT_FINISH_INBOUND_TASK_TRANSITION_PREVIEW")
                .previewVersion(TASK_PREVIEW_VERSION)
                .previewStatus(ready ? "READY" : "CONFLICT")
                .previewRef(previewRef)
                .stateDigest(stateDigest)
                .previewedAt(previewedAt)
                .expiresAt(expiresAt)
                .transition(FINISH_INBOUND_TRANSITION)
                .transitionLabel("确认成品入库")
                .canOpenBusinessDialog(ready)
                .requestedTaskCount(requestedCodes.size())
                .eligibleTaskCount(orderedRows.size())
                .tasks(orderedRows.stream().map(row -> TaskTransitionPreviewVO.Task.builder()
                        .palletCode(row.getCode())
                        .currentTaskStatus(row.getTaskStatus())
                        .productName(row.getProductName())
                        .productType(row.getProductType())
                        .productionDate(row.getProductionDate())
                        .totalWeight(row.getTotalWeight())
                        .presetWarehouseName(row.getTargetWarehouseName())
                        .presetSide(row.getTargetSide())
                        .quantityLockedByProductionOutput(row.getProductionOutputCodeId() != null)
                        .build()).toList())
                .requiredUserInputs(List.of("入库库位", "入库日期", "存放侧", "单位与数量", "可选备注"))
                .blockingIssues(blockingIssues)
                .warnings(warnings)
                .limitations(List.of(
                        "本预览只读取当前成品入库待处理任务，不修改任务、库存、二维码或业务单据。",
                        "本次预览不能直接执行任何业务写入。",
                        "最终提交仍由当前登录用户在既有业务弹窗中完成并接受原接口权限、校验、事务和审计。"))
                .build();
    }

    private TaskTransitionPreviewVO previewFinishOutboundTasks(TaskTransitionPreviewDTO request, User user) {
        List<String> requestedCodes = normalizePreviewCodes(request.getPalletCodes());

        PalletTaskQueryDTO query = new PalletTaskQueryDTO();
        query.setCodes(String.join(",", requestedCodes));
        query.setTaskType("OUT");
        query.setBizScene("FINISH_OUT");
        query.setStatus("PENDING");
        query.setPageNum(1L);
        query.setPageSize((long) requestedCodes.size());
        PageResult<PalletTaskPageVO> current = palletCodeService.pagePalletTasks(query);

        Map<String, PalletTaskPageVO> rowsByCode = new LinkedHashMap<>();
        for (PalletTaskPageVO row : safe(current.getRecords())) {
            String code = row.getCode() == null ? null : row.getCode().trim().toUpperCase(Locale.ROOT);
            if (code != null && requestedCodes.contains(code)) rowsByCode.putIfAbsent(code, row);
        }

        List<String> blockingIssues = new ArrayList<>();
        List<TaskTransitionPreviewVO.Task> eligibleTasks = new ArrayList<>();
        List<PalletTaskPageVO> eligibleRows = new ArrayList<>();
        for (String code : requestedCodes) {
            PalletTaskPageVO row = rowsByCode.get(code);
            if (row == null) {
                blockingIssues.add("托盘 " + code + " 的成品出库任务已不存在、状态已变化或不在当前轮次。");
                continue;
            }
            try {
                PalletCode pallet = palletCodeService.parseAndFind(code);
                if (!"INSTOCK".equalsIgnoreCase(pallet.getStatus())) {
                    throw new BusinessException("托盘当前不在可出库状态");
                }
                if (!"成品".equals(pallet.getProductStatus())) {
                    throw new BusinessException("当前不是成品托盘");
                }
                if (pallet.getProductId() == null || pallet.getProductionDate() == null) {
                    throw new BusinessException("托盘当前绑定信息不完整");
                }
                PalletInventoryVO inventory = palletCodeService.getInventoryByCode(code);
                eligibleRows.add(row);
                eligibleTasks.add(TaskTransitionPreviewVO.Task.builder()
                        .palletCode(row.getCode())
                        .currentTaskStatus(row.getTaskStatus())
                        .productName(row.getProductName())
                        .productType(row.getProductType())
                        .productionDate(row.getProductionDate())
                        .totalWeight(row.getTotalWeight())
                        .quantityLockedByProductionOutput(false)
                        .currentWarehouseName(inventory.getWarehouseName())
                        .currentSide(inventory.getSide())
                        .currentRowNumber(inventory.getRowNumber())
                        .currentLayer(inventory.getLayer())
                        .currentInventoryQuantity(inventory.getQuantity())
                        .currentInventoryUnit(Boolean.TRUE.equals(inventory.getUnit()) ? "件" : "板")
                        .build());
            } catch (BusinessException e) {
                blockingIssues.add("托盘 " + code + " 当前不满足成品出库条件：" + e.getMessage() + "。");
            }
        }

        boolean ready = blockingIssues.isEmpty() && eligibleTasks.size() == requestedCodes.size();
        String stateDigest = digestTaskState(eligibleRows, request.getTransition(), eligibleTasks);
        LocalDateTime previewedAt = LocalDateTime.now(BUSINESS_ZONE);
        LocalDateTime expiresAt = previewedAt.plusMinutes(TASK_PREVIEW_TTL_MINUTES);
        String previewRef = ready
                ? taskTransitionPreviewRefCodec.encode(user.getId(), stateDigest, expiresAt)
                : null;

        return TaskTransitionPreviewVO.builder()
                .dataScope("CURRENT_FINISH_OUTBOUND_TASK_TRANSITION_PREVIEW")
                .previewVersion(TASK_PREVIEW_VERSION)
                .previewStatus(ready ? "READY" : "CONFLICT")
                .previewRef(previewRef)
                .stateDigest(stateDigest)
                .previewedAt(previewedAt)
                .expiresAt(expiresAt)
                .transition(FINISH_OUTBOUND_TRANSITION)
                .transitionLabel("确认成品出库")
                .canOpenBusinessDialog(ready)
                .requestedTaskCount(requestedCodes.size())
                .eligibleTaskCount(eligibleTasks.size())
                .tasks(List.copyOf(eligibleTasks))
                .requiredUserInputs(List.of())
                .blockingIssues(List.copyOf(blockingIssues))
                .warnings(List.of(
                        "业务弹窗打开和最终提交时都会重新检查任务、托盘和库存状态；状态变化后需要重新预览。",
                        "最终确认成品出库会移除对应库存并释放托盘；本次预览不会执行这些操作。"))
                .limitations(List.of(
                        "本预览只读取当前成品出库待处理任务、托盘和库存，不修改任务、库存、二维码或业务单据。",
                        "本次预览不能直接执行任何业务写入。",
                        "最终提交仍由当前登录用户在既有业务弹窗中完成并接受原接口权限、校验、事务和审计。"))
                .build();
    }

    private List<String> normalizePreviewCodes(List<String> codes) {
        if (codes == null || codes.isEmpty() || codes.size() > 20) {
            throw new BusinessException(400, "托盘码数量必须在 1 到 20 之间");
        }
        LinkedHashMap<String, Boolean> unique = new LinkedHashMap<>();
        for (String value : codes) {
            String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            if (code.isEmpty() || code.length() > 100 || !code.matches("[A-Z0-9-]+")) {
                throw new BusinessException(400, "托盘码格式不正确");
            }
            unique.put(code, Boolean.TRUE);
        }
        return List.copyOf(unique.keySet());
    }

    private String digestTaskState(List<PalletTaskPageVO> rows, String transition) {
        return digestTaskState(rows, transition, List.of());
    }

    private String digestTaskState(
            List<PalletTaskPageVO> rows,
            String transition,
            List<TaskTransitionPreviewVO.Task> previewTasks
    ) {
        try {
            StringBuilder canonical = new StringBuilder("v1|").append(transition);
            rows.stream().sorted(Comparator.comparing(PalletTaskPageVO::getCode)).forEach(row -> canonical
                    .append('|').append(row.getTaskId())
                    .append('|').append(row.getCode())
                    .append('|').append(row.getTaskType())
                    .append('|').append(row.getTaskStatus())
                    .append('|').append(row.getCreatedAt()));
            previewTasks.stream().sorted(Comparator.comparing(TaskTransitionPreviewVO.Task::getPalletCode))
                    .forEach(task -> canonical
                            .append("|inventory|").append(task.getPalletCode())
                            .append('|').append(task.getCurrentWarehouseName())
                            .append('|').append(task.getCurrentSide())
                            .append('|').append(task.getCurrentRowNumber())
                            .append('|').append(task.getCurrentLayer())
                            .append('|').append(task.getCurrentInventoryQuantity())
                            .append('|').append(task.getCurrentInventoryUnit()));
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create task transition state digest", e);
        }
    }

    private AutoInboundBatchesAgentVO.Row safeBatchRow(AutoInboundBatchOptionVO item, int userId) {
        return AutoInboundBatchesAgentVO.Row.builder()
                .batchRef(autoInboundBatchRefCodec.encode(item.getBatchId(), userId))
                .parseTime(item.getParseTime()).displayName(item.getDisplayName()).taskCount(item.getTaskCount())
                .status(item.getStatus()).parseType(item.getParseType()).build();
    }

    private AutoInboundBatchDetailAgentVO.Task safeAutoInboundTask(AutoInboundTask task) {
        boolean semi = task.getType() != null && "SEMI_PRODUCT".equals(task.getType().name());
        return AutoInboundBatchDetailAgentVO.Task.builder()
                .type(task.getType()).riskLevel(task.getRiskLevel()).riskReason(task.getRiskReason())
                .entryDate(task.getEntryDate()).side(task.getSide()).hasAssay(task.getHasAssay())
                .productName(semi ? task.getSemiProductName() : task.getProductName())
                .warehouseName(semi ? task.getSemiWarehouseName() : task.getWarehouseName())
                .boardQuantity(semi ? task.getSemiBoardQuantity() : task.getFinishedBoardQuantity())
                .pieceQuantity(semi ? task.getSemiPieceQuantity() : task.getFinishedPieceQuantity())
                .requiredQrCount(task.getRequiredQrCount()).availableQrCount(task.getAvailableQrCount())
                .missingFields(safe(task.getMissingFields())).warnings(safe(task.getWarnings()))
                .status(task.getStatus()).canAutoStockIn(task.isCanAutoStockIn()).build();
    }

    private void requireUser(User user) {
        if (user == null || user.getId() == null || user.getId() <= 0) {
            throw new BusinessException(401, "当前用户身份无效");
        }
    }
}

package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayRecordsQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PalletAnomaliesQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PalletFlowRecordsQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PalletLifecycleQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PrintedNotInboundCodesQueryDTO;
import com.Laibin.SugarInventory.domain.dto.QrBatchInboundCompletionQueryDTO;
import com.Laibin.SugarInventory.domain.vo.PalletAnomaliesVO;
import com.Laibin.SugarInventory.domain.vo.PalletAnomalyGroupVO;
import com.Laibin.SugarInventory.domain.vo.PalletFlowRecordVO;
import com.Laibin.SugarInventory.domain.vo.PalletFlowRecordsVO;
import com.Laibin.SugarInventory.domain.vo.PalletLifecycleEventVO;
import com.Laibin.SugarInventory.domain.vo.PalletLifecycleVO;
import com.Laibin.SugarInventory.domain.vo.PalletPrintInfoVO;
import com.Laibin.SugarInventory.domain.vo.PrintedNotInboundCodesVO;
import com.Laibin.SugarInventory.domain.vo.PrintedNotInboundGroupVO;
import com.Laibin.SugarInventory.domain.vo.QrBatchInboundCompletionVO;
import com.Laibin.SugarInventory.mapper.PalletLifecycleQueryMapper;
import com.Laibin.SugarInventory.mapper.model.PalletAnomalyGroupRow;
import com.Laibin.SugarInventory.mapper.model.PalletLifecycleEventRow;
import com.Laibin.SugarInventory.mapper.model.PalletLifecycleSummaryRow;
import com.Laibin.SugarInventory.mapper.model.PalletPrintInfoRow;
import com.Laibin.SugarInventory.mapper.model.PrintedNotInboundGroupRow;
import com.Laibin.SugarInventory.mapper.model.QrBatchInboundCompletionRow;
import com.Laibin.SugarInventory.service.PalletLifecycleAnalysisService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class PalletLifecycleAnalysisServiceImpl implements PalletLifecycleAnalysisService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<String> FLOW_EVENT_TYPES = Set.of(
            "INBOUND", "OUTBOUND", "TRANSFER", "BIND", "ASSAY", "CANCEL", "LABEL");
    private static final Set<String> ANOMALY_TYPES = Set.of(
            "VOID_CODE_SCANNED", "STATUS_INVENTORY_MISMATCH", "DUPLICATE_INBOUND",
            "OUTBOUND_WITHOUT_INBOUND", "PRODUCT_BINDING_MISMATCH");

    private final PalletLifecycleQueryMapper mapper;

    public PalletLifecycleAnalysisServiceImpl(PalletLifecycleQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PalletLifecycleVO queryQrCodeLifecycle(PalletLifecycleQueryDTO query) {
        requireCode(query == null ? null : query.getCode());
        int flowLimit = limit(query == null ? null : query.getFlowLimit());
        PalletLifecycleSummaryRow row = mapper.selectLifecycleSummary(query.getCode().trim());
        if (row == null) {
            throw new BusinessException(404, "二维码或托盘码不存在");
        }

        boolean includeInventory = query.getIncludeInventory() == null || query.getIncludeInventory();
        boolean includeAssay = query.getIncludeAssay() == null || query.getIncludeAssay();
        boolean includeFlows = query.getIncludeFlows() == null || query.getIncludeFlows();
        boolean includePrintInfo = query.getIncludePrintInfo() != null && query.getIncludePrintInfo();

        PalletLifecycleVO result = new PalletLifecycleVO();
        result.setCodeLabel(row.getCode());
        result.setCurrentStatusLabel(statusLabel(row.getStatus()));
        result.setProductLabel(blankToDefault(row.getProductName(), "未绑定产品"));
        result.setProductionDate(row.getProductionDate());
        if (includeInventory && row.getQuantity() != null) {
            result.setWarehouseLabel(blankToDefault(row.getWarehouseName(), "库位未找到"));
            result.setQuantityText(stockText(row.getQuantity(), row.getPieces()));
        } else if (includeInventory) {
            result.setWarehouseLabel("尚未入库");
            result.setQuantityText("尚未形成当前库存");
        }
        if (includeAssay) {
            result.setAssaySummary(assaySummary(row));
        }

        List<PalletLifecycleEventVO> timeline = includeFlows
                ? mapper.selectLifecycleEvents(row.getPalletCodeId(), flowLimit).stream().map(this::toLifecycleEvent).toList()
                : List.of();
        result.setTimeline(timeline);

        if (includePrintInfo) {
            PalletPrintInfoRow print = mapper.selectPrintInfo(row.getPalletCodeId());
            result.setPrintInfo(print == null ? null : toPrintInfo(print));
        }

        List<String> risks = new ArrayList<>();
        if ("INVALID".equalsIgnoreCase(row.getStatus())) {
            risks.add("二维码当前为作废状态");
        }
        if (("INSTOCK".equalsIgnoreCase(row.getStatus())) != (row.getQuantity() != null)) {
            risks.add("托盘状态与当前库存关联不一致");
        }
        if (includeAssay && row.getQuantity() != null && row.getAssayId() == null) {
            risks.add("当前库存未关联有效化验");
        }
        if (includeFlows && timeline.isEmpty() && row.getStatus() != null && !"FREE".equalsIgnoreCase(row.getStatus())) {
            risks.add("当前没有可用托盘流转记录");
        }
        if (includePrintInfo && result.getPrintInfo() != null
                && result.getPrintInfo().getPrintedAt() != null
                && result.getPrintInfo().getUsedAt() == null
                && result.getPrintInfo().getRecycledAt() == null) {
            risks.add("标签已打印但尚未核销");
        }
        result.setRiskLabels(distinct(risks));
        result.setNotes(List.of("生命周期事件按业务时间正序展示；未展示内部主键和原始扩展字段。"));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public PalletFlowRecordsVO queryPalletFlowRecords(PalletFlowRecordsQueryDTO query) {
        if (query == null) {
            query = new PalletFlowRecordsQueryDTO();
        }
        validateProductScope(query.getProductScope());
        validateWarehouse(query.getWarehouseId());
        resolveDateRange(query.getDateRange(), query);
        query.setResolvedEventTypes(resolveFlowEventTypes(query.getEventTypes()));
        int page = positive(query.getPage(), 1);
        int size = limit(query.getSize() == null ? DEFAULT_PAGE_SIZE : query.getSize());
        long total = nullToZero(mapper.countFlowRecords(query));
        List<PalletFlowRecordVO> records = mapper.selectFlowRecords(query, (long) (page - 1) * size, size)
                .stream().map(this::toFlowRecord).toList();

        PalletFlowRecordsVO result = new PalletFlowRecordsVO();
        result.setScopeLabel(flowScopeLabel(query));
        result.setDateRangeLabel(dateRangeLabel(query.getResolvedFrom(), query.getResolvedTo()));
        result.setTotal(total);
        result.setSummaryText("" + dateRangeLabel(query.getResolvedFrom(), query.getResolvedTo())
                + "共有 " + total + " 条托盘流转记录。");
        result.setRecords(records);
        result.setNotes(List.of("流转记录按业务时间倒序返回，分页大小最大为100。"));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public PrintedNotInboundCodesVO queryPrintedNotInboundCodes(PrintedNotInboundCodesQueryDTO query) {
        if (query == null) {
            query = new PrintedNotInboundCodesQueryDTO();
        }
        validateProductScope(query.getProductScope());
        resolveDateRange(query.getDateRange(), query);
        query.setGroupBy(normalizeGroupBy(query.getGroupBy(), Set.of("batch", "order", "product"), "batch"));
        query.setLimit(limit(query.getLimit()));
        List<PrintedNotInboundGroupVO> groups = mapper.selectPrintedNotInboundGroups(query).stream()
                .map(this::toPrintedGroup).toList();
        long printed = groups.stream().mapToLong(PrintedNotInboundGroupVO::getPrintedCount).sum();
        long inbound = groups.stream().mapToLong(PrintedNotInboundGroupVO::getInboundCount).sum();
        long notInbound = Math.max(0, printed - inbound);

        PrintedNotInboundCodesVO result = new PrintedNotInboundCodesVO();
        result.setScopeLabel(printedScopeLabel(query));
        result.setDateRangeLabel(dateRangeLabel(query.getResolvedFrom(), query.getResolvedTo()));
        result.setGroupBy(query.getGroupBy());
        result.setPrintedCount(printed);
        result.setInboundCount(inbound);
        result.setNotInboundCount(notInbound);
        result.setCompletionRateText(rateText(inbound, printed));
        result.setSummaryText(String.format(Locale.ROOT, "%s共有 %d 个已打印标签，其中 %d 个已入库，%d 个未完成入库，完成率 %s。",
                result.getDateRangeLabel(), printed, inbound, notInbound, result.getCompletionRateText()));
        result.setGroups(groups);
        result.setNotes(List.of("第一版将生产订单标签批次的 printed_at 作为已打印口径，将关联产出码已入库作为完成入库口径。"));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public PalletAnomaliesVO queryPalletAnomalies(PalletAnomaliesQueryDTO query) {
        if (query == null) {
            query = new PalletAnomaliesQueryDTO();
        }
        validateProductScope(query.getProductScope());
        validateWarehouse(query.getWarehouseId());
        resolveDateRange(query.getDateRange(), query);
        Set<String> requestedTypes = query.getAnomalyTypes() == null || query.getAnomalyTypes().isEmpty()
                ? Set.of("STATUS_INVENTORY_MISMATCH", "DUPLICATE_INBOUND", "OUTBOUND_WITHOUT_INBOUND", "PRODUCT_BINDING_MISMATCH")
                : new LinkedHashSet<>(query.getAnomalyTypes());
        if (!ANOMALY_TYPES.containsAll(requestedTypes)) {
            throw new BusinessException(400, "存在不支持的托盘异常类型");
        }
        query.setLimit(limit(query.getLimit()));

        List<PalletAnomalyGroupVO> groups = mapper.selectAnomalyGroups(query).stream()
                .filter(row -> requestedTypes.contains(row.getAnomalyType()))
                .map(this::toAnomalyGroup)
                .toList();
        List<String> notes = new ArrayList<>();
        if (requestedTypes.contains("VOID_CODE_SCANNED")) {
            notes.add("当前系统没有独立的二维码扫描日志来源，暂不能判断作废码是否被扫描。");
        }
        if (groups.isEmpty() && notes.isEmpty()) {
            notes.add("当前筛选范围内未发现已定义的托盘生命周期异常。");
        }

        PalletAnomaliesVO result = new PalletAnomaliesVO();
        result.setScopeLabel(anomalyScopeLabel(query));
        result.setDateRangeLabel(dateRangeLabel(query.getResolvedFrom(), query.getResolvedTo()));
        result.setTotal(groups.stream().mapToLong(PalletAnomalyGroupVO::getCount).sum());
        result.setSummaryText("" + result.getDateRangeLabel() + "发现 " + groups.size() + " 类托盘异常，共 "
                + result.getTotal() + " 项。");
        result.setGroups(groups);
        result.setNotes(notes);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public QrBatchInboundCompletionVO queryQrBatchInboundCompletion(QrBatchInboundCompletionQueryDTO query) {
        if (query == null) {
            query = new QrBatchInboundCompletionQueryDTO();
        }
        if (isBlank(query.getBatchNo()) && isBlank(query.getOrderNo()) && query.getProductId() == null
                && query.getDateRange() == null) {
            throw new BusinessException(400, "batchNo、orderNo、productId 或 dateRange 至少提供一项");
        }
        resolveDateRange(query.getDateRange(), query);
        query.setLimit(limit(query.getLimit()));
        List<QrBatchInboundCompletionRow> rows = mapper.selectBatchInboundCompletion(query);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "未找到符合条件的已打印二维码批次");
        }
        QrBatchInboundCompletionRow row = rows.getFirst();
        long printed = nullToZero(row.getPrintedCount());
        long inbound = nullToZero(row.getInboundCount());
        long notInbound = Math.max(0, printed - inbound);
        QrBatchInboundCompletionVO result = new QrBatchInboundCompletionVO();
        result.setBatchLabel(row.getBatchNo());
        result.setOrderLabel(row.getOrderNo());
        result.setPrintedCount(printed);
        result.setInboundCount(inbound);
        result.setNotInboundCount(notInbound);
        result.setCompletionRateText(rateText(inbound, printed));
        result.setUnfinishedExamples(query.getIncludeUnfinishedExamples() == null || query.getIncludeUnfinishedExamples()
                ? csv(row.getUnfinishedExamples()) : List.of());
        result.setSummaryText(String.format(Locale.ROOT, "批次 %s 的二维码入库完成率为 %s。",
                row.getBatchNo(), result.getCompletionRateText()));
        result.setNotes(List.of("已打印以标签批次 printed_at 非空为准，已入库以产出码关联库存或入库时间为准。"));
        return result;
    }

    private PalletLifecycleEventVO toLifecycleEvent(PalletLifecycleEventRow row) {
        PalletLifecycleEventVO vo = new PalletLifecycleEventVO();
        vo.setTime(row.getOperationTime());
        vo.setEventType(row.getOperationType());
        vo.setEventLabel(blankToDefault(row.getOperationName(), eventLabel(row.getOperationType())));
        vo.setCodeLabel(row.getCode());
        vo.setProductLabel(row.getProductName());
        vo.setFromWarehouseLabel(row.getFromWarehouseName());
        vo.setToWarehouseLabel(row.getToWarehouseName());
        vo.setOperatorLabel(row.getOperatorName());
        vo.setCycleNo(row.getCycleNo());
        return vo;
    }

    private PalletPrintInfoVO toPrintInfo(PalletPrintInfoRow row) {
        PalletPrintInfoVO vo = new PalletPrintInfoVO();
        vo.setBatchLabel(row.getBatchNo());
        vo.setOrderLabel(row.getOrderNo());
        vo.setPrintStatusLabel(printStatusLabel(row.getBatchStatus()));
        vo.setLabelStatusLabel(labelStatusLabel(row.getLabelStatus()));
        vo.setPrintedAt(row.getPrintedAt());
        vo.setUsedAt(row.getUsedAt());
        vo.setRecycledAt(row.getRecycledAt());
        return vo;
    }

    private PalletFlowRecordVO toFlowRecord(PalletLifecycleEventRow row) {
        PalletFlowRecordVO vo = new PalletFlowRecordVO();
        vo.setTime(row.getOperationTime());
        vo.setEventType(row.getOperationType());
        vo.setEventLabel(blankToDefault(row.getOperationName(), eventLabel(row.getOperationType())));
        vo.setCodeLabel(row.getCode());
        vo.setProductLabel(row.getProductName());
        vo.setFromWarehouseLabel(row.getFromWarehouseName());
        vo.setToWarehouseLabel(row.getToWarehouseName());
        vo.setOperatorLabel(row.getOperatorName());
        vo.setCycleNo(row.getCycleNo());
        return vo;
    }

    private PrintedNotInboundGroupVO toPrintedGroup(PrintedNotInboundGroupRow row) {
        PrintedNotInboundGroupVO vo = new PrintedNotInboundGroupVO();
        long printed = nullToZero(row.getPrintedCount());
        long inbound = nullToZero(row.getInboundCount());
        vo.setGroupLabel(blankToDefault(row.getGroupLabel(), "未命名分组"));
        vo.setPrintedCount(printed);
        vo.setInboundCount(inbound);
        vo.setNotInboundCount(Math.max(0, printed - inbound));
        vo.setCompletionRateText(rateText(inbound, printed));
        vo.setExamples(csv(row.getNotInboundExamples()));
        vo.setRiskLabels(vo.getNotInboundCount() > 0 ? List.of("存在已打印未完成入库标签") : List.of());
        return vo;
    }

    private PalletAnomalyGroupVO toAnomalyGroup(PalletAnomalyGroupRow row) {
        PalletAnomalyGroupVO vo = new PalletAnomalyGroupVO();
        vo.setAnomalyType(row.getAnomalyType());
        vo.setGroupLabel(anomalyLabel(row.getAnomalyType()));
        vo.setCount(nullToZero(row.getAnomalyCount()));
        vo.setExamples(csv(row.getExamples()));
        vo.setRiskLabels(List.of("需要人工核对"));
        return vo;
    }

    private List<String> resolveFlowEventTypes(List<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return List.of();
        }
        if (!FLOW_EVENT_TYPES.containsAll(eventTypes)) {
            throw new BusinessException(400, "存在不支持的托盘流转事件类型");
        }
        Set<String> resolved = new LinkedHashSet<>();
        for (String type : eventTypes) {
            switch (type) {
                case "INBOUND" -> resolved.addAll(List.of("SEMI_INSTOCK", "FINISH_INSTOCK"));
                case "OUTBOUND" -> resolved.addAll(List.of("OUT", "PREPARE_CONSUMED", "ORDER_MATERIAL_PICK"));
                case "TRANSFER" -> resolved.add("TRANSFER");
                case "BIND" -> resolved.addAll(List.of("SEMI_BIND", "FINISH_BIND", "ORDER_OUTPUT_BIND"));
                case "ASSAY" -> resolved.add("ASSAY");
                case "CANCEL" -> resolved.add("CANCELED");
                case "LABEL" -> resolved.addAll(List.of("ORDER_LABEL_RESERVE", "ORDER_LABEL_USED", "ORDER_LABEL_RECYCLE"));
                default -> throw new BusinessException(400, "存在不支持的托盘流转事件类型");
            }
        }
        return new ArrayList<>(resolved);
    }

    private void validateProductScope(AssayRecordsQueryDTO.ProductScope scope) {
        if (scope == null) {
            return;
        }
        if (scope.getType() == null || !Set.of("SINGLE_PRODUCT", "EXACT_PRODUCT_NAME_GROUP", "PRODUCT_TYPE_GROUP", "ALL").contains(scope.getType())) {
            throw new BusinessException(400, "不支持的产品范围类型");
        }
        if ("SINGLE_PRODUCT".equals(scope.getType()) && (scope.getProductId() == null || scope.getProductId() < 1)) {
            throw new BusinessException(400, "单产品范围必须提供有效 productId");
        }
        if ("EXACT_PRODUCT_NAME_GROUP".equals(scope.getType()) && isBlank(scope.getProductName())) {
            throw new BusinessException(400, "产品名称范围不能为空");
        }
        if ("PRODUCT_TYPE_GROUP".equals(scope.getType()) && isBlank(scope.getProductType())) {
            throw new BusinessException(400, "产品大类范围不能为空");
        }
    }

    private void validateWarehouse(Integer warehouseId) {
        if (warehouseId != null && warehouseId < 1) {
            throw new BusinessException(400, "warehouseId 必须为正数");
        }
    }

    private void resolveDateRange(AssayRecordsQueryDTO.DateRange range, Object target) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(29);
        LocalDate to = today;
        if (range != null) {
            if (range.getType() == null) {
                throw new BusinessException(400, "dateRange.type 不能为空");
            }
            switch (range.getType()) {
                case "EXACT" -> {
                    if (range.getDate() == null) throw new BusinessException(400, "dateRange.date 不能为空");
                    from = range.getDate();
                    to = range.getDate();
                }
                case "LAST_DAYS" -> {
                    if (range.getDays() == null || range.getDays() < 1 || range.getDays() > 366) {
                        throw new BusinessException(400, "dateRange.days 必须在1到366之间");
                    }
                    from = today.minusDays(range.getDays() - 1L);
                }
                case "RANGE" -> {
                    if (range.getFrom() == null || range.getTo() == null || range.getFrom().isAfter(range.getTo())) {
                        throw new BusinessException(400, "dateRange.from 和 dateRange.to 无效");
                    }
                    from = range.getFrom();
                    to = range.getTo();
                }
                default -> throw new BusinessException(400, "不支持的日期范围类型");
            }
        }
        if (target instanceof PalletFlowRecordsQueryDTO q) {
            q.setResolvedFrom(from);
            q.setResolvedTo(to);
        } else if (target instanceof PrintedNotInboundCodesQueryDTO q) {
            q.setResolvedFrom(from);
            q.setResolvedTo(to);
        } else if (target instanceof PalletAnomaliesQueryDTO q) {
            q.setResolvedFrom(from);
            q.setResolvedTo(to);
        } else if (target instanceof QrBatchInboundCompletionQueryDTO q) {
            q.setResolvedFrom(from);
            q.setResolvedTo(to);
        }
    }

    private String normalizeGroupBy(String value, Set<String> allowed, String fallback) {
        String normalized = isBlank(value) ? fallback : value;
        if (!allowed.contains(normalized)) {
            throw new BusinessException(400, "不支持的分组字段");
        }
        return normalized;
    }

    private int limit(Integer value) {
        int normalized = value == null ? DEFAULT_LIMIT : value;
        if (normalized < 1 || normalized > MAX_LIMIT) {
            throw new BusinessException(400, "limit/size 必须在1到100之间");
        }
        return normalized;
    }

    private int positive(Integer value, int fallback) {
        return value == null ? fallback : value < 1 ? fallback : value;
    }

    private String flowScopeLabel(PalletFlowRecordsQueryDTO query) {
        if (!isBlank(query.getCode())) return "托盘码 " + query.getCode().trim();
        if (query.getProductScope() == null || "ALL".equals(query.getProductScope().getType())) return "全部托盘";
        return "受控产品范围内托盘";
    }

    private String printedScopeLabel(PrintedNotInboundCodesQueryDTO query) {
        if (!isBlank(query.getBatchNo())) return "标签批次 " + query.getBatchNo().trim();
        if (!isBlank(query.getOrderNo())) return "生产订单 " + query.getOrderNo().trim();
        return "受控标签范围";
    }

    private String anomalyScopeLabel(PalletAnomaliesQueryDTO query) {
        return query.getProductScope() == null || "ALL".equals(query.getProductScope().getType())
                ? "全部托盘" : "受控产品范围内托盘";
    }

    private String dateRangeLabel(LocalDate from, LocalDate to) {
        if (from == null || to == null) return "指定范围";
        return from.equals(to) ? from.toString() : from + "至" + to;
    }

    private String assaySummary(PalletLifecycleSummaryRow row) {
        if (row.getAssayId() == null) return "无有效化验";
        String qualified = row.getAssayQualified();
        if (qualified == null || qualified.isBlank()) return "已有化验记录，结论待确认";
        if (Set.of("PASS", "QUALIFIED", "合格", "1", "Y").contains(qualified.toUpperCase(Locale.ROOT))) return "化验合格";
        if (Set.of("FAIL", "UNQUALIFIED", "不合格", "0", "N").contains(qualified.toUpperCase(Locale.ROOT))) return "化验不合格";
        return "已有化验记录，结论为 " + qualified;
    }

    private String statusLabel(String status) {
        return switch (status == null ? "" : status.toUpperCase(Locale.ROOT)) {
            case "FREE" -> "空闲";
            case "PENDING" -> "待入库";
            case "INSTOCK" -> "在库";
            case "INVALID" -> "作废";
            case "ORDER_RESERVED" -> "订单预留";
            default -> blankToDefault(status, "未知状态");
        };
    }

    private String printStatusLabel(String status) {
        return switch (status == null ? "" : status.toUpperCase(Locale.ROOT)) {
            case "RESERVED" -> "已预留";
            case "PRINTED" -> "已打印";
            case "CLOSED" -> "已关闭";
            case "CANCELED" -> "已取消";
            default -> blankToDefault(status, "未知打印状态");
        };
    }

    private String labelStatusLabel(String status) {
        return switch (status == null ? "" : status.toUpperCase(Locale.ROOT)) {
            case "RESERVED" -> "待核销";
            case "USED" -> "已核销";
            case "RECYCLED" -> "已回收";
            case "CANCELED" -> "已取消";
            default -> blankToDefault(status, "未知标签状态");
        };
    }

    private String eventLabel(String type) {
        return switch (type == null ? "" : type) {
            case "SEMI_BIND" -> "半成品绑定";
            case "FINISH_BIND", "ORDER_OUTPUT_BIND" -> "成品绑定";
            case "SEMI_INSTOCK", "FINISH_INSTOCK" -> "入库";
            case "TRANSFER" -> "调拨";
            case "OUT", "PREPARE_CONSUMED", "ORDER_MATERIAL_PICK" -> "出库或领用";
            case "ASSAY" -> "关联化验";
            case "CANCELED" -> "取消任务";
            case "ORDER_LABEL_RESERVE" -> "预留订单标签";
            case "ORDER_LABEL_USED" -> "核销订单标签";
            case "ORDER_LABEL_RECYCLE" -> "回收订单标签";
            default -> blankToDefault(type, "流转事件");
        };
    }

    private String anomalyLabel(String type) {
        return switch (type) {
            case "STATUS_INVENTORY_MISMATCH" -> "托盘状态与库存不一致";
            case "DUPLICATE_INBOUND" -> "重复入库";
            case "OUTBOUND_WITHOUT_INBOUND" -> "无入库记录出库";
            case "PRODUCT_BINDING_MISMATCH" -> "二维码绑定产品与库存产品不一致";
            case "VOID_CODE_SCANNED" -> "作废码被扫描（当前无日志来源）";
            default -> type;
        };
    }

    private String stockText(Integer quantity, Integer pieces) {
        return (quantity == null ? 0 : quantity) + "板" + (pieces == null ? 0 : pieces) + "件";
    }

    private String rateText(long numerator, long denominator) {
        if (denominator <= 0) return "暂无数据";
        return String.format(Locale.ROOT, "%.1f%%", numerator * 100.0D / denominator);
    }

    private List<String> csv(String value) {
        if (isBlank(value)) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private List<String> distinct(List<String> values) {
        return values.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private String blankToDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void requireCode(String code) {
        if (isBlank(code) || code.trim().length() > 100) {
            throw new BusinessException(400, "code 不能为空且长度不能超过100");
        }
    }

}

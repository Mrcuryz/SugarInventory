package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AutoInboundConfirmRequest;
import com.Laibin.SugarInventory.domain.dto.BindPalletTaskDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmPalletInItemDTO;
import com.Laibin.SugarInventory.domain.enumObject.AutoInboundType;
import com.Laibin.SugarInventory.domain.po.PalletCode;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.ProductionConsumptionRecord;
import com.Laibin.SugarInventory.domain.po.ProductionReportRecord;
import com.Laibin.SugarInventory.domain.po.SemiPreparePoolBalance;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.domain.redis.AutoInboundTask;
import com.Laibin.SugarInventory.domain.redis.AutoInboundTaskItem;
import com.Laibin.SugarInventory.domain.redis.ProductionConsumptionEntry;
import com.Laibin.SugarInventory.domain.redis.ProductionConsumptionItem;
import com.Laibin.SugarInventory.domain.vo.AutoInboundParseResponse;
import com.Laibin.SugarInventory.domain.vo.InVO;
import com.Laibin.SugarInventory.mapper.PalletCodeMapper;
import com.Laibin.SugarInventory.mapper.ProductionConsumptionRecordMapper;
import com.Laibin.SugarInventory.mapper.ProductionReportRecordMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.SemiPreparePoolBalanceMapper;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.service.AutoInboundConfirmService;
import com.Laibin.SugarInventory.service.AutoInboundParseService;
import com.Laibin.SugarInventory.service.PalletCodeService;
import com.Laibin.SugarInventory.service.model.AutoInboundQuantityNormalizer;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AutoInboundConfirmServiceImpl implements AutoInboundConfirmService {

    private final AutoInboundParseService autoInboundParseService;
    private final PalletCodeService palletCodeService;
    private final PalletCodeMapper palletCodeMapper;
    private final ProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;
    private final ProductionReportRecordMapper productionReportRecordMapper;
    private final ProductionConsumptionRecordMapper productionConsumptionRecordMapper;
    private final SemiPreparePoolBalanceMapper semiPreparePoolBalanceMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String REDIS_PREFIX = "auto_inbound:batch:";
    private static final int PALLET_TASK_REMARK_MAX_LENGTH = 200;
    private static final int REPORT_REMARK_MAX_LENGTH = 480;

    @Transactional
    @Override
    public AutoInboundParseResponse confirm(String batchId, AutoInboundConfirmRequest req, User user) {
        AutoInboundParseResponse batch = autoInboundParseService.getBatch(batchId);
        List<AutoInboundTask> tasks = batch.getTasks() == null ? List.of() : batch.getTasks();

        Map<String, AutoInboundTask> taskMap = tasks.stream()
                .collect(Collectors.toMap(AutoInboundTask::getTaskId, t -> t, (a, b) -> a, LinkedHashMap::new));
        Map<String, AutoInboundTask> updatedMap = (req.getUpdatedTasks() == null ? List.<AutoInboundTask>of() : req.getUpdatedTasks())
                .stream()
                .collect(Collectors.toMap(AutoInboundTask::getTaskId, t -> t, (a, b) -> b));

        List<String> confirmedTaskIds = req.getConfirmedTaskIds() == null ? List.of() : req.getConfirmedTaskIds();
        if (confirmedTaskIds.isEmpty()) {
            throw new BusinessException("请选择需要入库的报数任务");
        }

        for (String taskId : confirmedTaskIds) {
            AutoInboundTask task = updatedMap.getOrDefault(taskId, taskMap.get(taskId));
            if (task == null) {
                throw new BusinessException("报数任务不存在：" + taskId);
            }
            if (task.getType() == AutoInboundType.FINISHED_PRODUCT) {
                throw new BusinessException("成品报数必须先关联生产订单，再通过生产订单登记产出和分配二维码");
            }
            executeTask(task, user);
            applyProductionConsumption(task, batchId, user);
            taskMap.put(taskId, task);
        }
        saveProductionReportRecord(batchId, new ArrayList<>(taskMap.values()), confirmedTaskIds, user);

        AutoInboundParseResponse response = new AutoInboundParseResponse();
        response.setBatchId(batchId);
        response.setGlobalRemarks(batch.getGlobalRemarks());
        response.setTasks(new ArrayList<>(taskMap.values()));
        saveCommittedBatch(batchId, response);
        return response;
    }

    private void executeTask(AutoInboundTask task, User operator) {
        Product product = requireProduct(task);
        Warehouse warehouse = requireWarehouse(task);
        List<AutoInboundTaskItem> taskItems;
        try {
            taskItems = AutoInboundQuantityNormalizer.normalize(
                    getBoardQuantity(task), getPieceQuantity(task), product.getPiecesPerPallet(), "报数入库");
        } catch (RuntimeException e) {
            throw new BusinessException(e.getMessage());
        }
        task.setTaskItems(taskItems);
        task.setRequiredQrCount(taskItems.size());

        List<PalletCode> palletCodes = allocateFixedProductQr(product.getId(), taskItems.size());
        task.setAvailableQrCount(palletCodes.size());
        LocalDate entryDate = task.getEntryDate() == null ? LocalDate.now() : task.getEntryDate();

        for (int i = 0; i < taskItems.size(); i++) {
            AutoInboundTaskItem item = taskItems.get(i);
            PalletCode palletCode = palletCodes.get(i);
            try {
                createAndConfirm(task, item, palletCode, product, warehouse, entryDate, operator);
                item.setCode(palletCode.getCode());
                item.setWarehouseName(warehouse.getWarehouseName());
                item.setSide(task.getSide());
                item.setStatus("SUCCESS");
                item.setMessage("入库成功");
            } catch (RuntimeException e) {
                item.setCode(palletCode.getCode());
                item.setWarehouseName(warehouse.getWarehouseName());
                item.setSide(task.getSide());
                item.setStatus("FAILED");
                item.setMessage(e.getMessage());
                throw e;
            }
        }

        task.setStatus("COMMITTED");
        task.setCanAutoStockIn(false);
    }

    private InVO createAndConfirm(AutoInboundTask task, AutoInboundTaskItem item, PalletCode palletCode,
                                  Product product, Warehouse warehouse, LocalDate entryDate, User operator) {
        String productStatus = task.getType() == AutoInboundType.SEMI_PRODUCT ? "半成品" : "成品";
        String remark = buildPalletTaskRemark(task, item);

        BindPalletTaskDTO bindDTO = new BindPalletTaskDTO();
        bindDTO.setCode(palletCode.getCode());
        bindDTO.setProductId(product.getId());
        bindDTO.setProductStatus(productStatus);
        bindDTO.setProductionDate(entryDate);
        bindDTO.setQuantity(item.getQuantity());
        bindDTO.setUnit(item.getUnit());
        bindDTO.setRemark(remark);

        ConfirmPalletInItemDTO confirmDTO = new ConfirmPalletInItemDTO();
        confirmDTO.setWarehouseName(warehouse.getWarehouseName());
        confirmDTO.setEntryDate(entryDate);
        confirmDTO.setSide(normalizeSide(task.getSide()));
        confirmDTO.setQuantity(item.getQuantity());
        confirmDTO.setUnit(item.getUnit());
        confirmDTO.setRemark(remark);
        return palletCodeService.createFixedProductInboundAndConfirm(bindDTO, confirmDTO, operator.getId());
    }

    private List<PalletCode> allocateFixedProductQr(Integer productId, int requiredCount) {
        List<PalletCode> codes = palletCodeMapper.selectList(new QueryWrapper<PalletCode>()
                .eq("fixed_product_id", productId)
                .eq("fixed_mode_enabled", true)
                .eq("status", "FREE")
                .orderByAsc("updated_at")
                .orderByAsc("id")
                .last("limit " + requiredCount + " for update"));
        if (codes.size() < requiredCount) {
            Product product = productMapper.selectById(productId);
            String productName = product == null ? String.valueOf(productId) : product.getProductName();
            throw new BusinessException(productName + " 需要 " + requiredCount
                    + " 个空闲固定产品二维码，当前仅有 " + codes.size() + " 个");
        }
        return codes;
    }

    private Product requireProduct(AutoInboundTask task) {
        Integer productId = task.getType() == AutoInboundType.SEMI_PRODUCT ? task.getSemiProductId() : task.getProductId();
        if (productId == null) {
            throw new BusinessException("请先补全产品");
        }
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("产品不存在");
        }
        return product;
    }

    private Warehouse requireWarehouse(AutoInboundTask task) {
        Integer warehouseId = task.getType() == AutoInboundType.SEMI_PRODUCT ? task.getSemiWarehouseId() : task.getWarehouseId();
        String warehouseName = task.getType() == AutoInboundType.SEMI_PRODUCT ? task.getSemiWarehouseName() : task.getWarehouseName();
        Warehouse warehouse = warehouseId == null ? null : warehouseMapper.selectById(warehouseId);
        if (warehouse == null && warehouseName != null && !warehouseName.isBlank()) {
            warehouse = warehouseMapper.selectByWarehouseName(warehouseName);
        }
        if (warehouse == null) {
            throw new BusinessException("请先补全库位");
        }
        return warehouse;
    }

    private Integer getBoardQuantity(AutoInboundTask task) {
        return task.getType() == AutoInboundType.SEMI_PRODUCT ? task.getSemiBoardQuantity() : task.getFinishedBoardQuantity();
    }

    private Integer getPieceQuantity(AutoInboundTask task) {
        return task.getType() == AutoInboundType.SEMI_PRODUCT ? task.getSemiPieceQuantity() : task.getFinishedPieceQuantity();
    }

    private String normalizeSide(String side) {
        return Objects.equals(side, "右") ? "右" : "左";
    }

    private String appendRemark(String base, String extra) {
        if (base == null || base.isBlank()) {
            return extra;
        }
        if (extra == null || extra.isBlank()) {
            return base;
        }
        return base + "；" + extra;
    }

    private String buildPalletTaskRemark(AutoInboundTask task, AutoInboundTaskItem item) {
        String remark = appendRemark("智能报数入库", item == null ? null : item.getDisplayQuantity());
        remark = appendRemark(remark, task == null ? null : task.getRemark());
        return truncate(remark, PALLET_TASK_REMARK_MAX_LENGTH);
    }

    private void saveProductionReportRecord(String batchId, List<AutoInboundTask> tasks,
                                            List<String> confirmedTaskIds, User user) {
        List<AutoInboundTask> confirmedTasks = tasks.stream()
                .filter(task -> confirmedTaskIds.contains(task.getTaskId()))
                .toList();
        boolean hasConsumption = confirmedTasks.stream()
                .anyMatch(task -> task.getProductionConsumptionItems() != null
                        && !task.getProductionConsumptionItems().isEmpty());
        boolean hasSource = confirmedTasks.stream().anyMatch(task -> task.getSourceText() != null && !task.getSourceText().isBlank());
        if (!hasConsumption && !hasSource) {
            return;
        }

        ProductionReportRecord record = new ProductionReportRecord();
        record.setSourceText(resolveSourceText(confirmedTasks));
        record.setReportType("AUTO_INBOUND");
        record.setReportDate(resolveReportDate(confirmedTasks));
        record.setInboundJson(writeJson(confirmedTasks));
        record.setConsumptionText(buildConsumptionText(confirmedTasks));
        record.setConsumptionJson(writeJson(confirmedTasks.stream()
                .flatMap(task -> task.getProductionConsumptionItems() == null
                        ? List.<ProductionConsumptionItem>of().stream()
                        : task.getProductionConsumptionItems().stream())
                .toList()));
        record.setUnmatchedNames(writeJson(confirmedTasks.stream()
                .flatMap(task -> task.getUnmatchedNames() == null ? List.<String>of().stream() : task.getUnmatchedNames().stream())
                .distinct()
                .toList()));
        record.setInboundTaskIds(writeJson(confirmedTaskIds));
        record.setPalletCodes(writeJson(confirmedTasks.stream()
                .flatMap(task -> task.getTaskItems() == null ? List.<AutoInboundTaskItem>of().stream() : task.getTaskItems().stream())
                .map(AutoInboundTaskItem::getCode)
                .filter(Objects::nonNull)
                .filter(code -> !code.isBlank())
                .toList()));
        record.setStatus("COMMITTED");
        record.setCreatedBy(user == null ? null : user.getId());
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        record.setRemark(buildReportRemark(batchId, confirmedTasks));
        productionReportRecordMapper.insert(record);
    }

    private void applyProductionConsumption(AutoInboundTask task, String batchId, User user) {
        if (task.getType() != AutoInboundType.FINISHED_PRODUCT) {
            return;
        }
        List<ProductionConsumptionItem> items = task.getProductionConsumptionItems();
        if (items == null || items.isEmpty()) {
            return;
        }
        List<String> results = items.stream()
                .map(item -> (item.getMaterialNameRaw() == null || item.getMaterialNameRaw().isBlank()
                        ? "半成品领用提示"
                        : item.getMaterialNameRaw()) + "：已留档，请在生产订单中确认实际领用二维码")
                .toList();
        task.setProductionConsumptionResults(results);
        if (!results.isEmpty()) {
            task.setConsumptionRemark(appendRemark(task.getConsumptionRemark(), String.join("；", results)));
        }
    }

    private void consumeProductionItem(AutoInboundTask task, ProductionConsumptionItem item,
                                       String batchId, User user, List<String> results) {
        if (item == null) {
            return;
        }
        String materialName = firstNonBlank(item.getProductName(), item.getMaterialNameRaw(), "未识别半成品");
        if (item.getProductId() == null || Boolean.FALSE.equals(item.getMatchedProduct())) {
            results.add(materialName + " 未匹配产品，生产消耗仅留档");
            return;
        }
        Product product = productMapper.selectById(item.getProductId());
        if (product == null) {
            results.add(materialName + " 产品不存在，生产消耗仅留档");
            return;
        }
        Integer piecesPerPallet = product.getPiecesPerPallet();
        if (piecesPerPallet == null || piecesPerPallet <= 0) {
            results.add(product.getProductName() + " 未配置每板件数，生产消耗仅留档");
            return;
        }
        List<ProductionConsumptionEntry> entries = item.getItems() == null ? List.of() : item.getItems();
        if (entries.isEmpty()) {
            results.add(product.getProductName() + " 未识别消耗数量，生产消耗仅留档");
            return;
        }
        for (ProductionConsumptionEntry entry : entries) {
            consumeProductionEntry(task, product, entry, batchId, user, results);
        }
    }

    private void consumeProductionEntry(AutoInboundTask task, Product product, ProductionConsumptionEntry entry,
                                        String batchId, User user, List<String> results) {
        if (entry == null || entry.getProductionDate() == null) {
            results.add(product.getProductName() + " 未识别生产日期，生产消耗仅留档");
            return;
        }
        int consumePieces = calculateConsumePieces(product, entry);
        if (consumePieces <= 0) {
            results.add(product.getProductName() + " " + entry.getProductionDate() + " 未识别有效消耗数量，生产消耗仅留档");
            return;
        }
        List<SemiPreparePoolBalance> balances = semiPreparePoolBalanceMapper.selectActiveByProductDateForUpdate(
                product.getId(), entry.getProductionDate());
        int available = balances.stream().mapToInt(balance -> safeInt(balance.getRemainingPieces())).sum();
        if (available < consumePieces) {
            results.add(product.getProductName() + " " + entry.getProductionDate()
                    + " 备料池余额不足，需要" + consumePieces + "件，当前" + available + "件，生产消耗仅留档");
            return;
        }
        int rest = consumePieces;
        LocalDateTime now = LocalDateTime.now();
        for (SemiPreparePoolBalance balance : balances) {
            if (rest <= 0) {
                break;
            }
            int currentRemaining = safeInt(balance.getRemainingPieces());
            if (currentRemaining <= 0) {
                continue;
            }
            int deducted = Math.min(currentRemaining, rest);
            int newRemaining = currentRemaining - deducted;
            balance.setConsumedPieces(safeInt(balance.getConsumedPieces()) + deducted);
            balance.setRemainingPieces(newRemaining);
            balance.setRemainingWeight(resolveRemainingWeight(balance, newRemaining));
            balance.setStatus(newRemaining == 0 ? "CONSUMED" : "ACTIVE");
            balance.setLastConsumedAt(now);
            balance.setUpdatedAt(now);
            semiPreparePoolBalanceMapper.updateById(balance);
            insertConsumptionRecord(task, product, balance, deducted, batchId, user, entry, now);
            rest -= deducted;
        }
        results.add(product.getProductName() + " " + entry.getProductionDate()
                + " 已扣减备料池" + consumePieces + "件");
    }

    private int calculateConsumePieces(Product product, ProductionConsumptionEntry entry) {
        int boards = entry.getBoardCount() == null ? 0 : entry.getBoardCount();
        int pieces = entry.getPieceCount() == null ? 0 : entry.getPieceCount();
        return boards * product.getPiecesPerPallet() + pieces;
    }

    private java.math.BigDecimal resolveRemainingWeight(SemiPreparePoolBalance balance, int remainingPieces) {
        java.math.BigDecimal weightPerPiece = balance.getWeightPerPiece();
        if (weightPerPiece == null) {
            weightPerPiece = java.math.BigDecimal.ZERO;
        }
        return weightPerPiece.multiply(java.math.BigDecimal.valueOf(remainingPieces));
    }

    private void insertConsumptionRecord(AutoInboundTask task, Product product, SemiPreparePoolBalance balance,
                                         int deducted, String batchId, User user,
                                         ProductionConsumptionEntry entry, LocalDateTime now) {
        ProductionConsumptionRecord record = new ProductionConsumptionRecord();
        record.setProductId(product.getId());
        record.setProductNameSnapshot(product.getProductName());
        record.setProductionDate(entry.getProductionDate());
        record.setScreenMeshId(balance.getScreenMeshId());
        record.setAssayId(balance.getAssayId());
        record.setConsumePieces(deducted);
        record.setBalanceId(balance.getId());
        record.setSourceBatchId(batchId);
        record.setSourceTaskId(task.getTaskId());
        record.setSourceText(task.getSourceText());
        record.setCreatedBy(user == null ? null : user.getId());
        record.setCreatedAt(now);
        record.setRemark("智能报数成品入库扣减备料池余额");
        productionConsumptionRecordMapper.insert(record);
    }

    private String buildReportRemark(String batchId, List<AutoInboundTask> confirmedTasks) {
        List<String> results = confirmedTasks.stream()
                .flatMap(task -> task.getProductionConsumptionResults() == null
                        ? List.<String>of().stream()
                        : task.getProductionConsumptionResults().stream())
                .distinct()
                .toList();
        if (results.isEmpty()) {
            return truncate("智能报数批次：" + batchId, REPORT_REMARK_MAX_LENGTH);
        }
        return truncate("智能报数批次：" + batchId + "；生产消耗处理结果：" + String.join("；", results),
                REPORT_REMARK_MAX_LENGTH);
    }

    private String resolveSourceText(List<AutoInboundTask> tasks) {
        return tasks.stream()
                .map(AutoInboundTask::getSourceText)
                .filter(Objects::nonNull)
                .filter(text -> !text.isBlank())
                .findFirst()
                .orElse("");
    }

    private LocalDate resolveReportDate(List<AutoInboundTask> tasks) {
        return tasks.stream()
                .map(AutoInboundTask::getEntryDate)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(LocalDate.now());
    }

    private String buildConsumptionText(List<AutoInboundTask> tasks) {
        return tasks.stream()
                .map(AutoInboundTask::getConsumptionRemark)
                .filter(Objects::nonNull)
                .filter(text -> !text.isBlank())
                .distinct()
                .collect(Collectors.joining("；"));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("保存生产报数留档失败");
        }
    }

    private void saveCommittedBatch(String batchId, AutoInboundParseResponse response) {
        try {
            stringRedisTemplate.opsForValue()
                    .set(REDIS_PREFIX + batchId, objectMapper.writeValueAsString(response.getTasks()), 24, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            throw new BusinessException("保存报数入库结果失败");
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}

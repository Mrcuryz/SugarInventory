package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AutoInboundConfirmRequest;
import com.Laibin.SugarInventory.domain.dto.AutoInboundTaskUpdateDTO;
import com.Laibin.SugarInventory.domain.dto.BindPalletTaskDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmPalletInItemDTO;
import com.Laibin.SugarInventory.domain.enumObject.AutoInboundType;
import com.Laibin.SugarInventory.domain.po.AutoInboundExecution;
import com.Laibin.SugarInventory.domain.po.PalletCode;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.ProductionReportRecord;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.domain.redis.AutoInboundTask;
import com.Laibin.SugarInventory.domain.redis.AutoInboundTaskItem;
import com.Laibin.SugarInventory.domain.redis.ProductionConsumptionItem;
import com.Laibin.SugarInventory.domain.vo.AutoInboundParseResponse;
import com.Laibin.SugarInventory.domain.vo.InVO;
import com.Laibin.SugarInventory.mapper.AutoInboundExecutionMapper;
import com.Laibin.SugarInventory.mapper.PalletCodeMapper;
import com.Laibin.SugarInventory.mapper.ProductionReportRecordMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.service.AutoInboundConfirmService;
import com.Laibin.SugarInventory.service.AutoInboundParseService;
import com.Laibin.SugarInventory.service.PalletCodeService;
import com.Laibin.SugarInventory.service.model.AutoInboundQuantityNormalizer;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AutoInboundConfirmServiceImpl implements AutoInboundConfirmService {

    private static final Logger log = LoggerFactory.getLogger(AutoInboundConfirmServiceImpl.class);

    private final AutoInboundParseService autoInboundParseService;
    private final PalletCodeService palletCodeService;
    private final PalletCodeMapper palletCodeMapper;
    private final ProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;
    private final ProductionReportRecordMapper productionReportRecordMapper;
    private final AutoInboundExecutionMapper autoInboundExecutionMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CONFIRM_LOCK_PREFIX = "auto_inbound:confirm-lock:";
    private static final int MAX_CONFIRM_TASKS = 20;
    private static final int MAX_CONFIRM_QR_COUNT = 100;
    private static final long CONFIRM_LOCK_MINUTES = 10;
    private static final int PALLET_TASK_REMARK_MAX_LENGTH = 200;
    private static final int REPORT_REMARK_MAX_LENGTH = 480;
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    @Transactional
    @Override
    public AutoInboundParseResponse confirm(String batchId, AutoInboundConfirmRequest req, User user) {
        requireUser(user);
        List<String> confirmedTaskIds = validateConfirmedTaskIds(req);
        String lockKey = CONFIRM_LOCK_PREFIX + batchId;
        String lockToken = UUID.randomUUID().toString();
        acquireConfirmLock(lockKey, lockToken);

        boolean synchronizationRegistered = false;
        try {
            AutoInboundParseResponse batch = autoInboundParseService.getBatch(batchId, user);
            List<AutoInboundTask> tasks = batch.getTasks() == null ? new ArrayList<>() : new ArrayList<>(batch.getTasks());
            Map<String, AutoInboundTask> taskMap = tasks.stream()
                    .collect(Collectors.toMap(AutoInboundTask::getTaskId, t -> t, (a, b) -> a, LinkedHashMap::new));
            Map<String, AutoInboundTaskUpdateDTO> updatedMap = validateAndIndexUpdates(req, taskMap, confirmedTaskIds);

            boolean hasExecutableTask = false;
            for (String taskId : confirmedTaskIds) {
                AutoInboundTask storedTask = taskMap.get(taskId);
                if (storedTask == null) {
                    throw new BusinessException(400, "报数任务不存在：" + taskId);
                }
                if ("COMMITTED".equalsIgnoreCase(storedTask.getStatus())) {
                    continue;
                }
                if (storedTask.getType() == AutoInboundType.FINISHED_PRODUCT) {
                    throw new BusinessException(400, "成品报数必须先关联生产订单，再通过生产订单登记产出和分配二维码");
                }
                mergeAllowedUpdate(storedTask, updatedMap.get(taskId));
                hasExecutableTask = true;
            }

            if (!hasExecutableTask) {
                return buildResponse(batchId, batch.getGlobalRemarks(), taskMap);
            }

            Map<String, PreparedAutoInboundTask> preparedTasks = prepareSelectedTasks(taskMap, confirmedTaskIds);
            List<AutoInboundTask> rollbackSnapshot = deepCopyTasks(new ArrayList<>(taskMap.values()));
            confirmedTaskIds.stream()
                    .map(taskMap::get)
                    .filter(Objects::nonNull)
                    .filter(task -> !"COMMITTED".equalsIgnoreCase(task.getStatus()))
                    .forEach(task -> task.setStatus("COMMITTING"));
            autoInboundParseService.saveBatch(batchId, new ArrayList<>(taskMap.values()), user);

            AtomicReference<List<AutoInboundTask>> committedTasks = new AtomicReference<>();
            AtomicReference<String> failureReason = new AtomicReference<>("事务提交失败，业务写入已回滚，可重试");
            registerBatchSynchronization(batchId, user, confirmedTaskIds, rollbackSnapshot,
                    committedTasks, failureReason, lockKey, lockToken);
            synchronizationRegistered = true;

            try {
                List<String> newlyExecutedTaskIds = new ArrayList<>();
                for (String taskId : confirmedTaskIds) {
                    AutoInboundTask task = taskMap.get(taskId);
                    if (task == null || "COMMITTED".equalsIgnoreCase(task.getStatus())) {
                        continue;
                    }
                    PreparedAutoInboundTask prepared = preparedTasks.get(taskId);
                    String requestHash = buildRequestHash(prepared);
                    AutoInboundTask recovered = recoverCommittedExecution(batchId, taskId, requestHash, user);
                    if (recovered != null) {
                        taskMap.put(taskId, recovered);
                        continue;
                    }

                    AutoInboundExecution execution = reserveExecution(batchId, taskId, requestHash, user);
                    executeTask(task, prepared, user, "AUTO_INBOUND:" + batchId);
                    execution.setStatus("COMMITTED");
                    execution.setResultJson(writeTaskJson(task));
                    execution.setUpdatedAt(LocalDateTime.now());
                    autoInboundExecutionMapper.updateById(execution);
                    newlyExecutedTaskIds.add(taskId);
                }
                List<AutoInboundTask> finalTasks = new ArrayList<>(taskMap.values());
                if (!newlyExecutedTaskIds.isEmpty()) {
                    saveProductionReportRecord(batchId, finalTasks, newlyExecutedTaskIds, user);
                }
                committedTasks.set(deepCopyTasks(finalTasks));
                return buildResponse(batchId, batch.getGlobalRemarks(), taskMap);
            } catch (RuntimeException e) {
                failureReason.set(truncate(firstNonBlank(e.getMessage(), "入库执行失败，业务写入已回滚"), 240));
                throw e;
            }
        } finally {
            if (!synchronizationRegistered) {
                releaseConfirmLock(lockKey, lockToken);
            }
        }
    }

    private List<String> validateConfirmedTaskIds(AutoInboundConfirmRequest request) {
        if (request == null || request.getConfirmedTaskIds() == null || request.getConfirmedTaskIds().isEmpty()) {
            throw new BusinessException(400, "请选择需要入库的报数任务");
        }
        if (request.getConfirmedTaskIds().size() > MAX_CONFIRM_TASKS) {
            throw new BusinessException(400, "单次最多确认 " + MAX_CONFIRM_TASKS + " 条报数任务");
        }
        List<String> taskIds = request.getConfirmedTaskIds().stream()
                .map(id -> id == null ? "" : id.trim())
                .toList();
        if (taskIds.stream().anyMatch(String::isBlank)) {
            throw new BusinessException(400, "报数任务标识不能为空");
        }
        Set<String> unique = new HashSet<>(taskIds);
        if (unique.size() != taskIds.size()) {
            throw new BusinessException(400, "同一报数任务不能重复提交");
        }
        return taskIds;
    }

    private Map<String, AutoInboundTaskUpdateDTO> validateAndIndexUpdates(
            AutoInboundConfirmRequest request,
            Map<String, AutoInboundTask> taskMap,
            List<String> confirmedTaskIds) {
        List<AutoInboundTaskUpdateDTO> updates = request.getUpdatedTasks() == null
                ? List.of()
                : request.getUpdatedTasks();
        Map<String, AutoInboundTaskUpdateDTO> result = new LinkedHashMap<>();
        Set<String> confirmed = new HashSet<>(confirmedTaskIds);
        for (AutoInboundTaskUpdateDTO update : updates) {
            String taskId = update == null || update.getTaskId() == null ? "" : update.getTaskId().trim();
            if (taskId.isBlank() || !confirmed.contains(taskId) || !taskMap.containsKey(taskId)) {
                throw new BusinessException(400, "客户端提交了不属于本次确认的报数任务");
            }
            if (result.putIfAbsent(taskId, update) != null) {
                throw new BusinessException(400, "同一报数任务不能提交多份修订");
            }
        }
        return result;
    }

    private void mergeAllowedUpdate(AutoInboundTask task, AutoInboundTaskUpdateDTO update) {
        if (update == null) {
            return;
        }
        if (update.getEntryDate() != null) {
            task.setEntryDate(update.getEntryDate());
        }
        if (update.getSide() != null) {
            task.setSide(update.getSide().trim());
        }
        if (update.getRemark() != null) {
            task.setRemark(truncate(update.getRemark().trim(), PALLET_TASK_REMARK_MAX_LENGTH));
        }
        if (task.getType() == AutoInboundType.SEMI_PRODUCT) {
            if (update.getSemiProductId() != null) {
                task.setSemiProductId(update.getSemiProductId());
            }
            if (update.getSemiWarehouseName() != null) {
                String warehouseName = update.getSemiWarehouseName().trim();
                if (!Objects.equals(warehouseName, task.getSemiWarehouseName())) {
                    task.setSemiWarehouseId(null);
                }
                task.setSemiWarehouseName(warehouseName);
            }
            if (update.getSemiWarehouseId() != null) {
                task.setSemiWarehouseId(update.getSemiWarehouseId());
            }
            if (update.getSemiBoardQuantity() != null) {
                task.setSemiBoardQuantity(update.getSemiBoardQuantity());
            }
            if (update.getSemiPieceQuantity() != null) {
                task.setSemiPieceQuantity(update.getSemiPieceQuantity());
            }
        } else {
            if (update.getProductId() != null) {
                task.setProductId(update.getProductId());
            }
            if (update.getWarehouseName() != null) {
                String warehouseName = update.getWarehouseName().trim();
                if (!Objects.equals(warehouseName, task.getWarehouseName())) {
                    task.setWarehouseId(null);
                }
                task.setWarehouseName(warehouseName);
            }
            if (update.getWarehouseId() != null) {
                task.setWarehouseId(update.getWarehouseId());
            }
            if (update.getFinishedBoardQuantity() != null) {
                task.setFinishedBoardQuantity(update.getFinishedBoardQuantity());
            }
            if (update.getFinishedPieceQuantity() != null) {
                task.setFinishedPieceQuantity(update.getFinishedPieceQuantity());
            }
        }
    }

    private Map<String, PreparedAutoInboundTask> prepareSelectedTasks(
            Map<String, AutoInboundTask> taskMap,
            List<String> confirmedTaskIds) {
        Map<String, PreparedAutoInboundTask> prepared = new LinkedHashMap<>();
        int totalQrCount = 0;
        for (String taskId : confirmedTaskIds) {
            AutoInboundTask task = taskMap.get(taskId);
            if (task == null || "COMMITTED".equalsIgnoreCase(task.getStatus())) {
                continue;
            }
            Product product = requireProduct(task);
            validateProductType(task, product);
            Warehouse warehouse = requireWarehouse(task);
            validateAndCanonicalizeTask(task, product, warehouse);
            List<AutoInboundTaskItem> items;
            try {
                items = AutoInboundQuantityNormalizer.normalize(
                        getBoardQuantity(task), getPieceQuantity(task), product.getPiecesPerPallet(), "报数入库");
            } catch (RuntimeException e) {
                throw new BusinessException(400, e.getMessage());
            }
            totalQrCount += items.size();
            if (totalQrCount > MAX_CONFIRM_QR_COUNT) {
                throw new BusinessException(400, "单次确认最多占用 " + MAX_CONFIRM_QR_COUNT + " 个二维码板位");
            }
            task.setTaskItems(items);
            task.setRequiredQrCount(items.size());
            prepared.put(taskId, new PreparedAutoInboundTask(task, product, warehouse, items));
        }
        return prepared;
    }

    private void validateProductType(AutoInboundTask task, Product product) {
        String expected = task.getType() == AutoInboundType.SEMI_PRODUCT ? "半成品" : "成品";
        if (!expected.equals(product.getStatus())) {
            throw new BusinessException(400, "选择的产品与报数任务类型不一致");
        }
    }

    private void validateAndCanonicalizeTask(AutoInboundTask task, Product product, Warehouse warehouse) {
        if (!Objects.equals(task.getSide(), "左") && !Objects.equals(task.getSide(), "右")) {
            throw new BusinessException(400, "侧别只能为左或右");
        }
        if (task.getEntryDate() == null) {
            throw new BusinessException(400, "请先补全入库日期");
        }
        if (task.getType() == AutoInboundType.SEMI_PRODUCT) {
            task.setSemiProductId(product.getId());
            task.setSemiProductName(product.getProductName());
            task.setSemiWarehouseId(warehouse.getId());
            task.setSemiWarehouseName(warehouse.getWarehouseName());
        } else {
            task.setProductId(product.getId());
            task.setProductName(product.getProductName());
            task.setWarehouseId(warehouse.getId());
            task.setWarehouseName(warehouse.getWarehouseName());
        }
    }

    private String buildRequestHash(PreparedAutoInboundTask prepared) {
        AutoInboundTask task = prepared.task();
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("taskId", task.getTaskId());
        canonical.put("type", task.getType() == null ? null : task.getType().name());
        canonical.put("productId", prepared.product().getId());
        canonical.put("warehouseId", prepared.warehouse().getId());
        canonical.put("entryDate", task.getEntryDate());
        canonical.put("side", task.getSide());
        canonical.put("boardQuantity", getBoardQuantity(task));
        canonical.put("pieceQuantity", getPieceQuantity(task));
        canonical.put("remark", task.getRemark());
        canonical.put("taskItems", prepared.taskItems().stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("seq", item.getSeq());
            row.put("quantity", item.getQuantity());
            row.put("unit", item.getUnit());
            return row;
        }).toList());
        try {
            byte[] payload = objectMapper.writeValueAsBytes(canonical);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new BusinessException(500, "生成自动入库幂等指纹失败");
        }
    }

    private AutoInboundTask recoverCommittedExecution(String batchId, String taskId, String requestHash, User user) {
        AutoInboundExecution execution = autoInboundExecutionMapper.selectOne(
                new QueryWrapper<AutoInboundExecution>()
                        .eq("batch_id", batchId)
                        .eq("source_task_id", taskId)
                        .last("limit 1 for update"));
        if (execution == null) {
            return null;
        }
        if (!Objects.equals(execution.getCreatedBy(), user.getId())) {
            throw new BusinessException(409, "批次执行所有者冲突，已拒绝重试");
        }
        if (!Objects.equals(execution.getRequestHash(), requestHash)) {
            throw new BusinessException(409, "该报数任务已按不同内容执行，不能修改后重放");
        }
        if (!"COMMITTED".equalsIgnoreCase(execution.getStatus())
                || execution.getResultJson() == null
                || execution.getResultJson().isBlank()) {
            throw new BusinessException(409, "该报数任务正在处理，请稍后重试");
        }
        try {
            AutoInboundTask recovered = objectMapper.readValue(execution.getResultJson(), AutoInboundTask.class);
            if (!Objects.equals(recovered.getTaskId(), taskId) || !"COMMITTED".equalsIgnoreCase(recovered.getStatus())) {
                throw new BusinessException(500, "自动入库恢复结果不完整");
            }
            return recovered;
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "读取自动入库恢复结果失败");
        }
    }

    private AutoInboundExecution reserveExecution(String batchId, String taskId, String requestHash, User user) {
        AutoInboundExecution execution = new AutoInboundExecution();
        execution.setBatchId(batchId);
        execution.setSourceTaskId(taskId);
        execution.setRequestHash(requestHash);
        execution.setStatus("PROCESSING");
        execution.setCreatedBy(user.getId());
        execution.setCreatedAt(LocalDateTime.now());
        execution.setUpdatedAt(LocalDateTime.now());
        try {
            autoInboundExecutionMapper.insert(execution);
            return execution;
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "该报数任务已被另一请求处理，请刷新批次状态");
        }
    }

    private String writeTaskJson(AutoInboundTask task) {
        try {
            return objectMapper.writeValueAsString(task);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "保存自动入库幂等结果失败");
        }
    }

    private List<AutoInboundTask> deepCopyTasks(List<AutoInboundTask> tasks) {
        try {
            return objectMapper.readValue(
                    objectMapper.writeValueAsBytes(tasks),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, AutoInboundTask.class));
        } catch (java.io.IOException e) {
            throw new BusinessException(500, "复制自动入库批次状态失败");
        }
    }

    private AutoInboundParseResponse buildResponse(String batchId, List<String> globalRemarks,
                                                    Map<String, AutoInboundTask> taskMap) {
        AutoInboundParseResponse response = new AutoInboundParseResponse();
        response.setBatchId(batchId);
        response.setGlobalRemarks(globalRemarks);
        response.setTasks(new ArrayList<>(taskMap.values()));
        return response;
    }

    private void acquireConfirmLock(String lockKey, String lockToken) {
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockToken, CONFIRM_LOCK_MINUTES, TimeUnit.MINUTES);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(409, "该自动入库批次正在确认，请勿重复提交");
        }
    }

    private void releaseConfirmLock(String lockKey, String lockToken) {
        try {
            stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), lockToken);
        } catch (RuntimeException e) {
            log.warn("Failed to release auto inbound confirm lock; key={}; errorType={}",
                    lockKey, e.getClass().getSimpleName());
        }
    }

    private void registerBatchSynchronization(
            String batchId,
            User user,
            List<String> confirmedTaskIds,
            List<AutoInboundTask> rollbackSnapshot,
            AtomicReference<List<AutoInboundTask>> committedTasks,
            AtomicReference<String> failureReason,
            String lockKey,
            String lockToken) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("自动入库确认必须在 Spring 事务中执行");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                List<AutoInboundTask> tasks = committedTasks.get();
                if (tasks == null) {
                    return;
                }
                try {
                    autoInboundParseService.saveBatch(batchId, tasks, user);
                } catch (RuntimeException e) {
                    log.error("Database committed but auto inbound Redis result refresh failed; batchId={}; errorType={}",
                            batchId, e.getClass().getSimpleName());
                }
            }

            @Override
            public void afterCompletion(int status) {
                try {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        markFailedForRetry(rollbackSnapshot, confirmedTaskIds, failureReason.get());
                        autoInboundParseService.saveBatch(batchId, rollbackSnapshot, user);
                    }
                } catch (RuntimeException e) {
                    log.error("Failed to restore auto inbound Redis state after rollback; batchId={}; errorType={}",
                            batchId, e.getClass().getSimpleName());
                } finally {
                    releaseConfirmLock(lockKey, lockToken);
                }
            }
        });
    }

    private void markFailedForRetry(List<AutoInboundTask> tasks, List<String> confirmedTaskIds, String reason) {
        Set<String> selected = new HashSet<>(confirmedTaskIds);
        for (AutoInboundTask task : tasks) {
            if (task == null || !selected.contains(task.getTaskId())
                    || "COMMITTED".equalsIgnoreCase(task.getStatus())) {
                continue;
            }
            task.setStatus("FAILED");
            List<String> warnings = task.getWarnings() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(task.getWarnings());
            warnings.add("上次确认失败：" + firstNonBlank(reason, "业务写入已回滚，可重试"));
            task.setWarnings(warnings.stream().distinct().toList());
        }
    }

    private void requireUser(User user) {
        if (user == null || user.getId() == null) {
            throw new BusinessException(401, "用户认证信息无效");
        }
    }

    private record PreparedAutoInboundTask(
            AutoInboundTask task,
            Product product,
            Warehouse warehouse,
            List<AutoInboundTaskItem> taskItems) {
    }

    private void executeTask(AutoInboundTask task, PreparedAutoInboundTask prepared, User operator,
                             String operationBatchNo) {
        Product product = prepared.product();
        Warehouse warehouse = prepared.warehouse();
        List<AutoInboundTaskItem> taskItems = prepared.taskItems();
        task.setTaskItems(taskItems);
        task.setRequiredQrCount(taskItems.size());

        List<PalletCode> palletCodes = allocateFixedProductQr(product.getId(), taskItems.size());
        task.setAvailableQrCount(palletCodes.size());
        LocalDate entryDate = task.getEntryDate() == null ? LocalDate.now() : task.getEntryDate();

        for (int i = 0; i < taskItems.size(); i++) {
            AutoInboundTaskItem item = taskItems.get(i);
            PalletCode palletCode = palletCodes.get(i);
            try {
                createAndConfirm(task, item, palletCode, product, warehouse, entryDate, operator, operationBatchNo);
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
                                  Product product, Warehouse warehouse, LocalDate entryDate, User operator,
                                  String operationBatchNo) {
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
        return palletCodeService.createFixedProductInboundAndConfirm(
                bindDTO, confirmDTO, operator.getId(), operationBatchNo);
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

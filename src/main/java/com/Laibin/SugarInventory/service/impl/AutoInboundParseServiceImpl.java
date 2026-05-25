package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AutoInboundParseRequest;
import com.Laibin.SugarInventory.domain.enumObject.AutoInboundRiskLevel;
import com.Laibin.SugarInventory.domain.enumObject.AutoInboundType;
import com.Laibin.SugarInventory.domain.po.*;
import com.Laibin.SugarInventory.domain.redis.AutoInboundTask;
import com.Laibin.SugarInventory.domain.redis.AutoInboundTaskItem;
import com.Laibin.SugarInventory.domain.redis.ProductionConsumptionEntry;
import com.Laibin.SugarInventory.domain.redis.ProductionConsumptionItem;
import com.Laibin.SugarInventory.domain.vo.AutoInboundBatchOptionVO;
import com.Laibin.SugarInventory.domain.vo.AutoInboundParseResponse;
import com.Laibin.SugarInventory.mapper.AssayMapper;
import com.Laibin.SugarInventory.mapper.PalletCodeMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.service.AutoInboundParseService;
import com.Laibin.SugarInventory.service.LlmParseService;
import com.Laibin.SugarInventory.service.model.AutoInboundQuantityNormalizer;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AutoInboundParseServiceImpl implements AutoInboundParseService {

    private final LlmParseService llmParseService;
    private final AssayMapper assayMapper;
    private final ProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;
    private final PalletCodeMapper palletCodeMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final Logger log = LoggerFactory.getLogger(AutoInboundParseServiceImpl.class);
    private static final String REDIS_PREFIX = "auto_inbound:batch:";
    private static final String HISTORY_PREFIX = "auto_inbound:history:";
    private static final int HISTORY_LIMIT = 20;
    private static final DateTimeFormatter HISTORY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public AutoInboundParseResponse parse(AutoInboundParseRequest request, User user) {
        request.setOperator(user);
        String batchId = UUID.randomUUID().toString();

        // 1. 调 LLM 解析原始文本
        LlmParseResult llmResult = llmParseService.parseReport(request);
        List<AutoInboundTask> tasks = new ArrayList<>();

        // 2. 根据 LLM 结果，直接构造 AutoInboundTask
        if (llmResult != null && llmResult.getItems() != null) {
            for (ParsedInboundItem item : llmResult.getItems()) {
                log.info("Parsed item -> index={}, type={}, productId={}, productName={}, whId={}, whName={}",
                        item.getIndex(), item.getType(),
                        item.getProductId(), item.getProductName(),
                        item.getWarehouseId(), item.getWarehouseName());

                String type = item.getType();
                if ("SEMI_PRODUCT_IN".equalsIgnoreCase(type)) {
                    tasks.add(buildSemiTask(batchId, request, item));
                } else if ("FINISHED_PRODUCT_IN".equalsIgnoreCase(type)) {
                    tasks.add(buildFinishedTask(batchId, request, item));
                } else {
                    // OTHER 暂不生成任务
                }
            }
        }

        // 3. 存入 Redis，TTL 24h
        try {
            String key = REDIS_PREFIX + batchId;
            String json = objectMapper.writeValueAsString(tasks);
            stringRedisTemplate.opsForValue()
                    .set(key, json, 24, TimeUnit.HOURS);
            stringRedisTemplate.opsForZSet().add(historyKey(user), batchId, System.currentTimeMillis());
            stringRedisTemplate.expire(historyKey(user), 24, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化自动入库任务失败", e);
        }

        // 4. 返回给前端
        AutoInboundParseResponse resp = new AutoInboundParseResponse();
        resp.setBatchId(batchId);
        resp.setTasks(tasks);
        if (llmResult != null) {
            resp.setGlobalRemarks(llmResult.getGlobalRemarks());
        }
        return resp;
    }

    @Override
    public AutoInboundParseResponse getBatch(String batchId) {
        String key = REDIS_PREFIX + batchId;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            throw new BusinessException("自动入库批次不存在或已过期");
        }
        try {
            List<AutoInboundTask> tasks = objectMapper.readValue(
                    json, new TypeReference<List<AutoInboundTask>>() {});
            AutoInboundParseResponse resp = new AutoInboundParseResponse();
            resp.setBatchId(batchId);
            resp.setTasks(tasks);
            return resp;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("反序列化自动入库任务失败", e);
        }
    }

    /**
     * 半成品任务：完全按 LLM 输出的 product_id / warehouse_id / quantity 构建
     */
    private AutoInboundTask buildSemiTask(String batchId, AutoInboundParseRequest req, ParsedInboundItem item) {
        AutoInboundTask task = new AutoInboundTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setBatchId(batchId);
        task.setType(AutoInboundType.SEMI_PRODUCT);
        LocalDate entryDate = resolveEntryDate(item, req.getEntryDate());
        task.setEntryDate(entryDate);
        task.setSide(resolveSide(item));
        task.setRawBlock(item.getRawBlock());
        task.setSourceText(req.getRawText());

        List<String> reasons = new ArrayList<>();
        List<String> remarks = new ArrayList<>();
        List<String> missingFields = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (notBlank(item.getRemark())) {
            remarks.add(item.getRemark());
        }

        // === 产品：直接用 LLM 的 product_id / product_name ===
        Integer productId = item.getProductId();
        if (productId != null) {
            Product product = productMapper.selectById(productId);
            if (product != null) {
                task.setSemiProductId(product.getId());
                task.setSemiProductName(product.getProductName());
            } else {
                missingFields.add("产品");
                reasons.add("未匹配到产品：" + safe(item.getProductNameRaw()));
            }
        } else {
            missingFields.add("产品");
            reasons.add("未匹配到产品：" + safe(item.getProductNameRaw()));
        }

        // === 仓库：直接用 LLM 的 warehouse_id / warehouse_name ===
        Integer whId = item.getWarehouseId();
        if (whId != null) {
            Warehouse warehouse = warehouseMapper.selectById(whId);
            if (warehouse != null) {
                task.setSemiWarehouseId(warehouse.getId());
                task.setSemiWarehouseName(warehouse.getWarehouseName());
            } else {
                missingFields.add("库位");
                reasons.add("未匹配到库位：" + safe(item.getLocation()));
            }
        } else {
            missingFields.add("库位");
            String loc = safe(item.getLocation());
            if (!loc.isEmpty()) {
                reasons.add("未匹配到库位：" + loc);
            } else {
                reasons.add("缺少库位信息（未识别出仓库提示）");
            }
        }

        // === 数量：quantity.pallets / quantity.pieces ===
        ParsedInboundItem.Quantity qty = item.getQuantity();
        int pallets = (qty != null && qty.getPallets() != null) ? qty.getPallets() : 0;
        int pieces = (qty != null && qty.getPieces() != null) ? qty.getPieces() : 0;
        task.setSemiBoardQuantity(pallets);
        task.setSemiPieceQuantity(pieces);

        if (pallets <= 0 && pieces <= 0) {
            missingFields.add("数量");
            reasons.add("半成品板数与件数均为空或为0");
        }

        // === 化验：按 productId + entryDate 查当日化验 ===
        boolean hasAssay = false;
        if (task.getSemiProductId() != null) {
            Assay assay = assayMapper.selectByProductIdAndDate(task.getSemiProductId(), entryDate);
            hasAssay = (assay != null);
            if (!hasAssay) {
                warnings.add("半成品当日化验记录缺失");
            }
        } else {
            reasons.add("未能匹配到半成品产品，无法检查化验记录");
        }
        task.setHasAssay(hasAssay);

        // === 风险等级 ===
        refreshTaskValidation(task, missingFields, warnings, reasons);
        task.setRemark(String.join("；", remarks));
        return task;
    }

    /**
     * 成品任务：同样完全按 LLM 输出生成
     */
    private AutoInboundTask buildFinishedTask(String batchId, AutoInboundParseRequest req, ParsedInboundItem item) {
        AutoInboundTask task = new AutoInboundTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setBatchId(batchId);
        task.setType(AutoInboundType.FINISHED_PRODUCT);
        LocalDate entryDate = resolveEntryDate(item, req.getEntryDate());
        task.setEntryDate(entryDate);
        task.setSide(resolveSide(item));
        task.setRawBlock(item.getRawBlock());
        task.setSourceText(req.getRawText());

        List<String> reasons = new ArrayList<>();
        List<String> remarks = new ArrayList<>();
        List<String> missingFields = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (notBlank(item.getRemark())) {
            remarks.add(item.getRemark());
        }

        // === 成品产品：直接用 LLM ===
        Integer productId = item.getProductId();
        if (productId != null) {
            Product product = productMapper.selectById(productId);
            if (product != null) {
                task.setProductId(product.getId());
                task.setProductName(product.getProductName());
            } else {
                missingFields.add("产品");
                reasons.add("未匹配到成品产品：" + safe(item.getProductNameRaw()));
            }
        } else {
            missingFields.add("产品");
            reasons.add("未匹配到成品产品：" + safe(item.getProductNameRaw()));
        }

        // === 仓库：直接用 LLM ===
        Integer whId = item.getWarehouseId();
        if (whId != null) {
            Warehouse warehouse = warehouseMapper.selectById(whId);
            if (warehouse != null) {
                task.setWarehouseId(warehouse.getId());
                task.setWarehouseName(warehouse.getWarehouseName());
            } else {
                missingFields.add("库位");
                reasons.add("未匹配到库位：" + safe(item.getLocation()));
            }
        } else {
            missingFields.add("库位");
            String loc = safe(item.getLocation());
            if (!loc.isEmpty()) {
                reasons.add("未匹配到库位：" + loc);
            } else {
                reasons.add("缺少库位信息（未识别出仓库提示）");
            }
        }

        // === 数量 ===
        ParsedInboundItem.Quantity qty = item.getQuantity();
        int pallets = (qty != null && qty.getPallets() != null) ? qty.getPallets() : 0;
        int pieces = (qty != null && qty.getPieces() != null) ? qty.getPieces() : 0;
        task.setFinishedBoardQuantity(pallets);
        task.setFinishedPieceQuantity(pieces);
        if (pallets <= 0 && pieces <= 0) {
            missingFields.add("数量");
            reasons.add("成品板数与件数均为空或为0");
        }

        // === 成品化验 ===
        boolean hasAssay = false;
        if (task.getProductId() != null) {
            Assay assay = assayMapper.selectByProductIdAndDate(task.getProductId(), entryDate);
            hasAssay = (assay != null);
            if (!hasAssay) {
                warnings.add("成品当日化验记录缺失");
            }
        } else {
            reasons.add("未能匹配到成品产品，无法检查化验记录");
        }
        task.setHasAssay(hasAssay);

        List<ProductionConsumptionItem> consumptionItems = buildProductionConsumptionItems(item.getSources(), warnings);
        task.setProductionConsumptionItems(consumptionItems);
        task.setUnmatchedNames(resolveUnmatchedNames(consumptionItems));
        task.setConsumptionRemark(buildConsumptionRemark(consumptionItems, task.getUnmatchedNames()));
        if (!consumptionItems.isEmpty()) {
            warnings.add("生产消耗信息将在成品入库成功后优先扣减备料池余额；未匹配或余额不足的部分仅留档");
        }

        refreshTaskValidation(task, missingFields, warnings, reasons);
        task.setRemark(String.join("；", remarks));
        return task;
    }

    private List<ProductionConsumptionItem> buildProductionConsumptionItems(List<ParsedSemiSource> sources,
                                                                            List<String> warnings) {
        if (sources == null || sources.isEmpty()) {
            return Collections.emptyList();
        }

        List<ProductionConsumptionItem> result = new ArrayList<>();
        for (ParsedSemiSource src : sources) {
            String productName = firstNonBlank(src.getProductName(), src.getProductNameRaw());
            LocalDate productionDate = parseSourceDate(src.getProductionDate(), productName, warnings);
            Integer boardCount = src.getBoardCount();
            Integer pieceCount = src.getPieceCount();
            boolean hasBoard = boardCount != null && boardCount > 0;
            boolean hasPiece = pieceCount != null && pieceCount > 0;

            if (!hasBoard && !hasPiece) {
                warnings.add("生产消耗 " + safe(productName) + " 的板数和件数均未识别，仅按原文留档");
            }

            ProductionConsumptionEntry entry = new ProductionConsumptionEntry();
            entry.setProductionDate(productionDate);
            entry.setBoardCount(hasBoard ? boardCount : 0);
            entry.setPieceCount(hasPiece ? pieceCount : 0);
            entry.setQuantityText(formatQuantityText(entry.getBoardCount(), entry.getPieceCount()));

            ProductionConsumptionItem item = new ProductionConsumptionItem();
            item.setMaterialNameRaw(firstNonBlank(src.getProductNameRaw(), productName));
            item.setProductId(src.getSemiProductId());
            item.setProductName(productName);
            item.setSourceType(src.getSourceType());
            item.setWarehouseHint(src.getWarehouseHint());
            item.setBatchNo(src.getBatchNo());
            item.setRemark(src.getRemark());
            item.setMatchedProduct(src.getSemiProductId() != null);
            item.setItems(List.of(entry));
            result.add(item);
        }

        return result;
    }

    private LocalDate parseSourceDate(String rawDate, String productName, List<String> warnings) {
        if (!notBlank(rawDate)) {
            warnings.add("生产消耗生产日期缺失：" + safe(productName));
            return null;
        }
        try {
            return LocalDate.parse(rawDate);
        } catch (Exception e) {
            warnings.add("生产消耗生产日期格式无法解析：" + rawDate);
            return null;
        }
    }

    private List<String> resolveUnmatchedNames(List<ProductionConsumptionItem> consumptionItems) {
        if (consumptionItems == null || consumptionItems.isEmpty()) {
            return Collections.emptyList();
        }
        return consumptionItems.stream()
                .filter(item -> !Boolean.TRUE.equals(item.getMatchedProduct()))
                .map(item -> firstNonBlank(item.getMaterialNameRaw(), item.getProductName()))
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private String buildConsumptionRemark(List<ProductionConsumptionItem> consumptionItems, List<String> unmatchedNames) {
        if ((consumptionItems == null || consumptionItems.isEmpty())
                && (unmatchedNames == null || unmatchedNames.isEmpty())) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        lines.add("生产消耗备注（优先扣减备料池余额；未匹配或余额不足的部分仅留档）");
        if (consumptionItems != null) {
            for (ProductionConsumptionItem item : consumptionItems) {
                String material = firstNonBlank(item.getMaterialNameRaw(), item.getProductName());
                if (item.getItems() == null || item.getItems().isEmpty()) {
                    lines.add(material + "：数量未识别");
                    continue;
                }
                for (ProductionConsumptionEntry entry : item.getItems()) {
                    String dateText = entry.getProductionDate() == null ? "日期未识别" : entry.getProductionDate().toString();
                    lines.add(material + "：" + dateText + " " + entry.getQuantityText());
                }
            }
        }
        if (unmatchedNames != null && !unmatchedNames.isEmpty()) {
            lines.add("未匹配产品：" + String.join("、", unmatchedNames));
        }
        return String.join("；", lines);
    }

    private String formatQuantityText(Integer boards, Integer pieces) {
        int boardCount = boards == null ? 0 : boards;
        int pieceCount = pieces == null ? 0 : pieces;
        if (boardCount <= 0 && pieceCount <= 0) {
            return "数量未识别";
        }
        List<String> parts = new ArrayList<>();
        if (boardCount > 0) {
            parts.add(boardCount + "板");
        }
        if (pieceCount > 0) {
            parts.add(pieceCount + "件");
        }
        return String.join("", parts);
    }

    private void refreshTaskValidation(AutoInboundTask task, List<String> missingFields,
                                       List<String> warnings, List<String> reasons) {
        if (!notBlank(task.getSide())) {
            missingFields.add("侧别");
            reasons.add("缺少侧别信息");
        }
        Product product = resolveTaskProduct(task);
        Warehouse warehouse = resolveTaskWarehouse(task);
        if (product != null) {
            try {
                List<AutoInboundTaskItem> taskItems = AutoInboundQuantityNormalizer.normalize(
                        getBoardQuantity(task), getPieceQuantity(task), product.getPiecesPerPallet(), "报数入库");
                task.setTaskItems(taskItems);
                task.setRequiredQrCount(taskItems.size());
                task.setAvailableQrCount(countAvailableFixedQr(product.getId()));
                if (task.getAvailableQrCount() < task.getRequiredQrCount()) {
                    warnings.add(product.getProductName() + " 需要 " + task.getRequiredQrCount()
                            + " 个固定产品二维码，当前可用 " + task.getAvailableQrCount() + " 个");
                }
                if (warehouse != null && warehouse.getMaxCapacity() != null && warehouse.getCurCapacity() != null
                        && warehouse.getCurCapacity() + task.getRequiredQrCount() > warehouse.getMaxCapacity()) {
                    warnings.add(warehouse.getWarehouseName() + " 剩余板位不足，本次需要 "
                            + task.getRequiredQrCount() + " 个板位");
                }
            } catch (RuntimeException e) {
                missingFields.add("数量");
                reasons.add(e.getMessage());
                task.setTaskItems(Collections.emptyList());
                task.setRequiredQrCount(0);
                task.setAvailableQrCount(0);
            }
        } else {
            task.setTaskItems(Collections.emptyList());
            task.setRequiredQrCount(0);
            task.setAvailableQrCount(0);
        }

        task.setMissingFields(distinct(missingFields));
        task.setWarnings(distinct(warnings));
        task.setRiskReason(String.join("；", distinct(joinReasons(reasons, warnings))));
        task.setStatus("DRAFT");
        if (!task.getMissingFields().isEmpty()) {
            task.setRiskLevel(AutoInboundRiskLevel.RED);
            task.setCanAutoStockIn(false);
        } else if (!task.getWarnings().isEmpty() || !reasons.isEmpty()) {
            task.setRiskLevel(AutoInboundRiskLevel.YELLOW);
            task.setCanAutoStockIn(true);
        } else {
            task.setRiskLevel(AutoInboundRiskLevel.GREEN);
            task.setCanAutoStockIn(true);
        }
    }

    @Override
    public List<AutoInboundBatchOptionVO> listBatches(User user) {
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(historyKey(user), 0, HISTORY_LIMIT - 1);
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        List<AutoInboundBatchOptionVO> result = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String batchId = tuple.getValue();
            if (batchId == null) {
                continue;
            }
            String json = stringRedisTemplate.opsForValue().get(REDIS_PREFIX + batchId);
            if (json == null) {
                stringRedisTemplate.opsForZSet().remove(historyKey(user), batchId);
                continue;
            }
            List<AutoInboundTask> tasks = readTasks(json);
            long timestamp = tuple.getScore() == null ? 0L : tuple.getScore().longValue();
            String parseTime = formatHistoryTime(timestamp);

            AutoInboundBatchOptionVO option = new AutoInboundBatchOptionVO();
            option.setBatchId(batchId);
            option.setParseTime(parseTime);
            option.setDisplayName(parseTime);
            option.setTaskCount(tasks.size());
            option.setStatus(resolveBatchStatus(tasks));
            result.add(option);
        }
        return result;
    }

    private List<AutoInboundTask> readTasks(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<AutoInboundTask>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("反序列化自动入库任务失败", e);
        }
    }

    private String resolveBatchStatus(List<AutoInboundTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return "EMPTY";
        }
        boolean allCommitted = tasks.stream().allMatch(t -> "COMMITTED".equalsIgnoreCase(t.getStatus()));
        return allCommitted ? "COMMITTED" : "DRAFT";
    }

    private String formatHistoryTime(long timestamp) {
        if (timestamp <= 0) {
            return "未知时间";
        }
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        return HISTORY_TIME_FORMATTER.format(time);
    }

    private String historyKey(User user) {
        Integer userId = user == null ? null : user.getId();
        return HISTORY_PREFIX + (userId == null ? "anonymous" : userId);
    }

    private Product resolveTaskProduct(AutoInboundTask task) {
        Integer productId = task.getType() == AutoInboundType.SEMI_PRODUCT ? task.getSemiProductId() : task.getProductId();
        return productId == null ? null : productMapper.selectById(productId);
    }

    private Warehouse resolveTaskWarehouse(AutoInboundTask task) {
        Integer warehouseId = task.getType() == AutoInboundType.SEMI_PRODUCT ? task.getSemiWarehouseId() : task.getWarehouseId();
        return warehouseId == null ? null : warehouseMapper.selectById(warehouseId);
    }

    private Integer getBoardQuantity(AutoInboundTask task) {
        return task.getType() == AutoInboundType.SEMI_PRODUCT ? task.getSemiBoardQuantity() : task.getFinishedBoardQuantity();
    }

    private Integer getPieceQuantity(AutoInboundTask task) {
        return task.getType() == AutoInboundType.SEMI_PRODUCT ? task.getSemiPieceQuantity() : task.getFinishedPieceQuantity();
    }

    private int countAvailableFixedQr(Integer productId) {
        Long count = palletCodeMapper.selectCount(new QueryWrapper<PalletCode>()
                .eq("fixed_product_id", productId)
                .eq("fixed_mode_enabled", true)
                .eq("status", "FREE"));
        return count == null ? 0 : count.intValue();
    }

    private LocalDate resolveEntryDate(ParsedInboundItem item, LocalDate fallback) {
        if (notBlank(item.getProductionDate())) {
            try {
                return LocalDate.parse(item.getProductionDate());
            } catch (Exception ignored) {
                // fall through to request date
            }
        }
        return fallback == null ? LocalDate.now() : fallback;
    }

    private String resolveSide(ParsedInboundItem item) {
        if (notBlank(item.getSide())) {
            return item.getSide().contains("右") ? "右" : "左";
        }
        String text = safe(item.getLocation()) + safe(item.getRawBlock());
        return text.contains("右") ? "右" : "左";
    }

    private List<String> joinReasons(List<String> reasons, List<String> warnings) {
        List<String> all = new ArrayList<>();
        all.addAll(reasons);
        all.addAll(warnings);
        return all;
    }

    private List<String> distinct(List<String> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

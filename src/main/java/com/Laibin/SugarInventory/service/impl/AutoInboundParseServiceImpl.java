package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AutoInboundParseRequest;
import com.Laibin.SugarInventory.domain.dto.SemiRecordDTO;
import com.Laibin.SugarInventory.domain.enumObject.AutoInboundRiskLevel;
import com.Laibin.SugarInventory.domain.enumObject.AutoInboundType;
import com.Laibin.SugarInventory.domain.po.*;
import com.Laibin.SugarInventory.domain.redis.AutoInboundTask;
import com.Laibin.SugarInventory.domain.redis.AutoInboundTaskItem;
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
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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

        // === sources → 建议 semiRecords（不再做本地匹配，只做“提示用”） ===
        List<SemiRecordDTO> semiRecords = buildSuggestedSemiRecords(item.getSources(), reasons, remarks);
        task.setSuggestedSemiRecords(semiRecords);

        refreshTaskValidation(task, missingFields, warnings, reasons);
        task.setRemark(String.join("；", remarks));
        return task;
    }

    /**
     * 成品里的 sources，仅作为“建议用半成品记录”
     * 每个 source 拆成多条 SemiRecordDTO（板一条、件一条）。
     */
    private List<SemiRecordDTO> buildSuggestedSemiRecords(List<ParsedSemiSource> sources,
                                                          List<String> reasons,
                                                          List<String> remarks) {
        if (sources == null || sources.isEmpty()) {
            return Collections.emptyList();
        }

        List<SemiRecordDTO> result = new ArrayList<>();

        for (ParsedSemiSource src : sources) {
            // ---------- 公共字段：名称、日期 ----------

            String productName = src.getProductName();
            if (productName == null || productName.isBlank()) {
                productName = src.getProductName(); // 兜底用规范名
            }

            Integer semiProductId = src.getSemiProductId();
            if (semiProductId == null) {
                reasons.add("半成品 " + productName + " 未匹配到产品ID");
            }

            LocalDate prodDate = null;
            if (notBlank(src.getProductionDate())) {
                try {
                    prodDate = LocalDate.parse(src.getProductionDate());
                } catch (Exception e) {
                    reasons.add("半成品生产日期格式无法解析：" + src.getProductionDate());
                }
            } else {
                reasons.add("半成品生产日期缺失：" + productName);
            }

            // 仓位提示、备注、批号只追加一次到 remark 列表
            if (notBlank(src.getWarehouseHint())) {
                remarks.add("半成品仓位提示：" + src.getWarehouseHint());
            }
            if (notBlank(src.getRemark())) {
                remarks.add("半成品备注：" + src.getRemark());
            }
            if (notBlank(src.getBatchNo())) {
                remarks.add("半成品批号：" + src.getBatchNo());
            }

            Integer boardCount = src.getBoardCount();
            Integer pieceCount = src.getPieceCount();

            boolean hasBoard = boardCount != null && boardCount > 0;
            boolean hasPiece = pieceCount != null && pieceCount > 0;

            // ---------- A. 有板数 -> 生成一条“板”的记录 ----------
            if (hasBoard) {
                SemiRecordDTO boardDto = new SemiRecordDTO();
                boardDto.setSemiProductId(src.getSemiProductId());
                boardDto.setProductName(productName);
                boardDto.setProductionDate(prodDate);
                boardDto.setWarehouseId(null);   // 库位同样交给前端选
                boardDto.setQuantity(boardCount);
                boardDto.setUnit("0");           // 0 = 板
                boardDto.setUseAssay(false);

                result.add(boardDto);
            }

            // ---------- B. 有件数 -> 生成一条“件”的记录 ----------
            if (hasPiece) {
                SemiRecordDTO pieceDto = new SemiRecordDTO();
                // 同样只先填名称
                pieceDto.setProductName(productName);
                pieceDto.setSemiProductId(semiProductId);
                pieceDto.setProductionDate(prodDate);
                pieceDto.setWarehouseId(null);
                pieceDto.setQuantity(pieceCount);
                pieceDto.setUnit("1");           // 1 = 件
                pieceDto.setUseAssay(false);

                result.add(pieceDto);
            }

            // ---------- C. 板/件都没有识别到 ----------
            if (!hasBoard && !hasPiece) {
                reasons.add("半成品 " + productName +
                        " 的板数和件数均无法识别，请人工补录数量");

                SemiRecordDTO emptyDto = new SemiRecordDTO();
                emptyDto.setSemiProductId(semiProductId);
                emptyDto.setProductName(productName);
                emptyDto.setProductionDate(prodDate);
                emptyDto.setWarehouseId(null);
                emptyDto.setQuantity(null);
                emptyDto.setUnit("1");   // 默认件
                emptyDto.setUseAssay(false);

                result.add(emptyDto);
            }
        }

        return result;
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

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

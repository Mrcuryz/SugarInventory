package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AutoInboundConfirmRequest;
import com.Laibin.SugarInventory.domain.dto.BindPalletTaskDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmPalletInItemDTO;
import com.Laibin.SugarInventory.domain.enumObject.AutoInboundType;
import com.Laibin.SugarInventory.domain.po.PalletCode;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.domain.redis.AutoInboundTask;
import com.Laibin.SugarInventory.domain.redis.AutoInboundTaskItem;
import com.Laibin.SugarInventory.domain.vo.AutoInboundParseResponse;
import com.Laibin.SugarInventory.domain.vo.InVO;
import com.Laibin.SugarInventory.mapper.PalletCodeMapper;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String REDIS_PREFIX = "auto_inbound:batch:";

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
            executeTask(task, user);
            taskMap.put(taskId, task);
        }

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
        String remark = appendRemark(task.getRemark(), "报数识别入库：" + item.getDisplayQuantity());

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
        return base + "；" + extra;
    }

    private void saveCommittedBatch(String batchId, AutoInboundParseResponse response) {
        try {
            stringRedisTemplate.opsForValue()
                    .set(REDIS_PREFIX + batchId, objectMapper.writeValueAsString(response.getTasks()), 24, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            throw new BusinessException("保存报数入库结果失败");
        }
    }
}

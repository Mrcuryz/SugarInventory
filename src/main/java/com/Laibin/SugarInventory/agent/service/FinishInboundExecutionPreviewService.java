package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.agent.model.FinishInboundExecutionPreviewSnapshot;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.FinishInboundExecutionPreviewDTO;
import com.Laibin.SugarInventory.domain.dto.FinishInboundExecutionPreviewItemDTO;
import com.Laibin.SugarInventory.domain.po.PalletCode;
import com.Laibin.SugarInventory.domain.po.PalletTask;
import com.Laibin.SugarInventory.domain.po.PalletTaskSemiItem;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.SemiPreparePoolBalance;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.domain.vo.FinishInboundExecutionPreviewVO;
import com.Laibin.SugarInventory.mapper.PalletTaskMapper;
import com.Laibin.SugarInventory.mapper.PalletTaskSemiItemMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.SemiPreparePoolBalanceMapper;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.production.domain.po.ProductionOrderOutputCode;
import com.Laibin.SugarInventory.production.mapper.ProductionOrderOutputCodeMapper;
import com.Laibin.SugarInventory.service.PalletCodeService;
import com.Laibin.SugarInventory.service.model.PalletInventoryOccupancyRule;
import com.Laibin.SugarInventory.service.model.WarehouseLocationGranularityPolicy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinishInboundExecutionPreviewService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int PREVIEW_VERSION = 2;
    private static final int PREVIEW_TTL_MINUTES = 5;

    private final PalletCodeService palletCodeService;
    private final PalletTaskMapper palletTaskMapper;
    private final ProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;
    private final ProductionOrderOutputCodeMapper productionOutputCodeMapper;
    private final PalletTaskSemiItemMapper palletTaskSemiItemMapper;
    private final SemiPreparePoolBalanceMapper semiPreparePoolBalanceMapper;

    public FinishInboundExecutionPreviewVO preview(FinishInboundExecutionPreviewDTO request, User user) {
        requireUser(user);
        if (request == null || !Integer.valueOf(PREVIEW_VERSION).equals(request.getPreviewVersion())) {
            throw new BusinessException(400, "当前仅支持第 2 版成品入库执行预览协议");
        }
        List<FinishInboundExecutionPreviewItemDTO> rawItems = request.getItems();
        if (rawItems == null || rawItems.isEmpty()) {
            throw new BusinessException(400, "成品入库预览列表不能为空");
        }
        if (rawItems.size() > 20) {
            throw new BusinessException(400, "单次最多预览20个托盘");
        }

        List<String> blockingIssues = new ArrayList<>();
        List<ResolvedInput> resolved = resolveInputs(rawItems, blockingIssues);
        List<ResolvedPreviewItem> eligible = new ArrayList<>();
        Map<Integer, WarehouseOccupancy> occupancyByWarehouse = new LinkedHashMap<>();
        for (ResolvedInput input : resolved) {
            try {
                eligible.add(resolvePreviewItem(input, occupancyByWarehouse));
            } catch (BusinessException exception) {
                blockingIssues.add("托盘 " + input.canonicalCode() + "：" + safeIssue(exception.getMessage()));
            }
        }
        eligible.sort(Comparator.comparing(item -> item.pallet().getId()));

        boolean ready = blockingIssues.isEmpty() && eligible.size() == rawItems.size();
        LocalDateTime previewedAt = LocalDateTime.now(BUSINESS_ZONE);
        List<FinishInboundExecutionPreviewVO.Item> publicItems = eligible.stream()
                .map(ResolvedPreviewItem::publicItem)
                .toList();
        FinishInboundExecutionPreviewSnapshot snapshot = ready
                ? FinishInboundExecutionPreviewSnapshot.builder()
                .previewVersion(PREVIEW_VERSION)
                .items(eligible.stream().map(ResolvedPreviewItem::snapshotItem).toList())
                .build()
                : null;

        return FinishInboundExecutionPreviewVO.builder()
                .dataScope("FINISH_INBOUND_EXECUTION_PREVIEW")
                .previewVersion(PREVIEW_VERSION)
                .previewStatus(ready ? "READY" : "CONFLICT")
                .previewedAt(previewedAt)
                .expiresAt(previewedAt.plusMinutes(PREVIEW_TTL_MINUTES))
                .readyForUserConfirmation(ready)
                .requestedItemCount(rawItems.size())
                .eligibleItemCount(eligible.size())
                .items(publicItems)
                .blockingIssues(List.copyOf(blockingIssues))
                .warnings(ready
                        ? List.of("最终执行前仍会重新读取任务、托盘、产品、库位和生产关联；状态变化后需要重新预览。")
                        : List.of("本次预览未生成可确认快照，请修正问题后重新预览。"))
                .limitations(List.of(
                        "本预览只核对已填写的成品入库表单，不执行入库、任务确认或库存变更。",
                        "库存只精确到目标库位，不记录库位内部位置。",
                        "本预览不签发执行令牌，也不能作为已经完成入库的证明。"))
                .serverSnapshot(snapshot)
                .build();
    }

    private List<ResolvedInput> resolveInputs(
            List<FinishInboundExecutionPreviewItemDTO> rawItems,
            List<String> blockingIssues) {
        Set<String> seenInputs = new LinkedHashSet<>();
        Set<Integer> seenPalletIds = new LinkedHashSet<>();
        List<ResolvedInput> resolved = new ArrayList<>();
        for (FinishInboundExecutionPreviewItemDTO raw : rawItems) {
            if (raw == null) {
                blockingIssues.add("成品入库预览项不能为空。");
                continue;
            }
            String inputCode = normalizeCodeText(raw.getCode());
            if (inputCode == null) {
                blockingIssues.add("托盘码不能为空。");
                continue;
            }
            if (!seenInputs.add(inputCode)) {
                blockingIssues.add("本次预览包含重复托盘 " + inputCode + "。");
                continue;
            }
            try {
                PalletCode pallet = palletCodeService.parseAndFind(inputCode);
                if (pallet.getId() == null) {
                    throw new BusinessException("托盘数据异常");
                }
                String canonicalCode = normalizeCodeText(pallet.getCode());
                if (!seenPalletIds.add(pallet.getId())) {
                    blockingIssues.add("本次预览有多个输入指向同一托盘 " + canonicalCode + "。");
                    continue;
                }
                resolved.add(new ResolvedInput(raw, pallet, canonicalCode));
            } catch (BusinessException exception) {
                blockingIssues.add("托盘 " + inputCode + "：" + safeIssue(exception.getMessage()));
            }
        }
        return resolved;
    }

    private ResolvedPreviewItem resolvePreviewItem(
            ResolvedInput input,
            Map<Integer, WarehouseOccupancy> occupancyByWarehouse) {
        FinishInboundExecutionPreviewItemDTO request = input.request();
        PalletCode pallet = input.pallet();
        if ("ORDER_RESERVED".equalsIgnoreCase(pallet.getStatus())) {
            throw new BusinessException("该码是订单预打印标签，需先由生产管理确认生产结束后再入库");
        }
        if (!"PENDING".equalsIgnoreCase(pallet.getStatus())) {
            throw new BusinessException("当前托盘状态不支持成品入库预览");
        }
        int cycleNo = pallet.getCurrentCycleNo() == null ? 0 : pallet.getCurrentCycleNo();
        PalletTask task = palletTaskMapper.selectPendingByCycle(pallet.getId(), cycleNo);
        if (task == null || !"FINISH_IN".equals(task.getTaskType())) {
            throw new BusinessException("未找到当前轮次的待处理成品入库任务");
        }

        Product product = productMapper.selectById(task.getProductId());
        if (product == null) {
            throw new BusinessException("产品不存在");
        }
        String warehouseName = normalizeRequiredText(request.getWarehouseName(), "目标库位不能为空", 100);
        Warehouse warehouse = warehouseMapper.selectByWarehouseName(warehouseName);
        if (warehouse == null) {
            throw new BusinessException("目标库位不存在");
        }
        LocalDate entryDate = request.getEntryDate() != null ? request.getEntryDate() : task.getProductionDate();
        if (entryDate == null) {
            entryDate = pallet.getProductionDate();
        }
        if (entryDate == null) {
            throw new BusinessException("无法确定入库日期");
        }

        ProductionOrderOutputCode outputCode = productionOutputCodeMapper.selectByTaskId(task.getId());
        String unit = normalizeUnit(outputCode == null ? request.getUnit() : outputCode.getUnit());
        int quantity = normalizeQuantity(outputCode == null ? request.getQuantity() : outputCode.getQuantity());
        PalletInventoryOccupancyRule.validateSingleQrInventory(
                unit, quantity, product.getPiecesPerPallet(), "成品入库预览");
        String remark = normalizeOptionalText(request.getRemark(), 255, "备注长度不能超过255个字符");

        MaterialSnapshot materialSnapshot = resolveMaterialSnapshot(task);
        WarehouseOccupancy occupancy = occupancyByWarehouse.computeIfAbsent(
                warehouse.getId(), ignored -> loadWarehouseOccupancy(warehouse));
        occupancy.reserve();
        FinishInboundExecutionPreviewVO.Item publicItem = FinishInboundExecutionPreviewVO.Item.builder()
                .palletCode(input.canonicalCode())
                .productName(product.getProductName())
                .productionDate(task.getProductionDate())
                .warehouseName(warehouse.getWarehouseName())
                .entryDate(entryDate)
                .quantity(quantity)
                .unitLabel("1".equals(unit) ? "件" : "板")
                .remark(remark)
                .quantityLockedByProductionOutput(outputCode != null)
                .build();

        FinishInboundExecutionPreviewSnapshot.NormalizedInput normalizedInput =
                FinishInboundExecutionPreviewSnapshot.NormalizedInput.builder()
                        .code(input.canonicalCode())
                        .warehouseName(warehouse.getWarehouseName())
                        .entryDate(entryDate)
                        .quantity(quantity)
                        .unit(unit)
                        .remark(remark)
                        .build();
        FinishInboundExecutionPreviewSnapshot.Item snapshotItem =
                FinishInboundExecutionPreviewSnapshot.Item.builder()
                        .normalizedInput(normalizedInput)
                        .task(FinishInboundExecutionPreviewSnapshot.TaskState.builder()
                                .id(task.getId()).taskType(task.getTaskType()).status(task.getStatus())
                                .cycleNo(task.getCycleNo()).createdAt(task.getCreatedAt()).build())
                        .pallet(FinishInboundExecutionPreviewSnapshot.PalletState.builder()
                                .id(pallet.getId()).status(pallet.getStatus())
                                .currentCycleNo(pallet.getCurrentCycleNo()).productId(pallet.getProductId())
                                .updatedAt(pallet.getUpdatedAt()).build())
                        .product(FinishInboundExecutionPreviewSnapshot.ProductState.builder()
                                .id(product.getId()).status(product.getStatus())
                                .piecesPerPallet(product.getPiecesPerPallet()).canStack(product.getCanStack())
                                .updatedAt(product.getUpdatedAt()).build())
                        .warehouse(FinishInboundExecutionPreviewSnapshot.WarehouseState.builder()
                                .id(warehouse.getId()).status(warehouse.getStatus())
                                .maxCapacity(warehouse.getMaxCapacity()).curCapacity(warehouse.getCurCapacity())
                                .updatedAt(warehouse.getUpdatedAt()).build())
                        .productionOutput(outputCode == null ? null
                                : FinishInboundExecutionPreviewSnapshot.ProductionOutputState.builder()
                                .id(outputCode.getId()).status(outputCode.getStatus())
                                .quantity(outputCode.getQuantity()).unit(outputCode.getUnit())
                                .inventoryId(outputCode.getInventoryId()).build())
                        .materialInputs(materialSnapshot.inputs())
                        .materialBalances(materialSnapshot.balances())
                        .build();
        return new ResolvedPreviewItem(pallet, publicItem, snapshotItem);
    }

    private WarehouseOccupancy loadWarehouseOccupancy(Warehouse warehouse) {
        return new WarehouseOccupancy(
                WarehouseLocationGranularityPolicy.requireCapacity(warehouse),
                WarehouseLocationGranularityPolicy.currentCapacity(warehouse));
    }

    private MaterialSnapshot resolveMaterialSnapshot(PalletTask task) {
        List<PalletTaskSemiItem> semiItems = palletTaskSemiItemMapper.selectList(
                new LambdaQueryWrapper<PalletTaskSemiItem>()
                        .eq(PalletTaskSemiItem::getPalletTaskId, task.getId())
                        .orderByAsc(PalletTaskSemiItem::getId));
        if (semiItems == null || semiItems.isEmpty()) {
            return new MaterialSnapshot(List.of(), List.of());
        }
        Map<Long, Integer> requiredByBalance = new LinkedHashMap<>();
        List<FinishInboundExecutionPreviewSnapshot.MaterialInputState> inputs = new ArrayList<>();
        for (PalletTaskSemiItem item : semiItems) {
            if (item.getPrepareBalanceId() == null) {
                throw new BusinessException("关联的半成品历史批次不存在");
            }
            int requiredPieces = item.getTotalPieces() != null
                    ? item.getTotalPieces()
                    : (item.getQuantity() == null ? 0 : item.getQuantity());
            // Keep the preview aligned with consumePrepareBalancesAfterFinishIn: non-positive
            // legacy quantities do not consume balance, but the referenced balance must still
            // exist because buildSemiRecords reads it before the business operation starts.
            requiredByBalance.merge(item.getPrepareBalanceId(), Math.max(requiredPieces, 0), Integer::sum);
            inputs.add(FinishInboundExecutionPreviewSnapshot.MaterialInputState.builder()
                    .id(item.getId()).balanceId(item.getPrepareBalanceId())
                    .quantity(item.getQuantity()).unit(item.getUnit())
                    .totalPieces(item.getTotalPieces()).useAssay(item.getUseAssay()).build());
        }
        List<FinishInboundExecutionPreviewSnapshot.MaterialBalanceState> balances = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : requiredByBalance.entrySet()) {
            SemiPreparePoolBalance balance = semiPreparePoolBalanceMapper.selectById(entry.getKey());
            if (balance == null) {
                throw new BusinessException("关联的半成品历史批次不存在");
            }
            int remaining = balance.getRemainingPieces() == null ? 0 : balance.getRemainingPieces();
            if (remaining < entry.getValue()) {
                throw new BusinessException("关联的半成品历史批次余额不足，无法完成成品入库");
            }
            balances.add(FinishInboundExecutionPreviewSnapshot.MaterialBalanceState.builder()
                    .id(balance.getId()).status(balance.getStatus())
                    .remainingPieces(balance.getRemainingPieces()).updatedAt(balance.getUpdatedAt()).build());
        }
        balances.sort(Comparator.comparing(FinishInboundExecutionPreviewSnapshot.MaterialBalanceState::getId));
        return new MaterialSnapshot(List.copyOf(inputs), List.copyOf(balances));
    }

    private static String normalizeUnit(String value) {
        String normalized = value == null || value.isBlank() ? "0" : value.trim();
        if (!Set.of("0", "1").contains(normalized)) {
            throw new BusinessException("单位仅支持板或件");
        }
        return normalized;
    }

    private static int normalizeQuantity(Integer value) {
        return value == null || value <= 0 ? 1 : value;
    }

    private static String normalizeCodeText(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeRequiredText(String value, String missingMessage, int maxLength) {
        if (value == null || value.isBlank()) throw new BusinessException(missingMessage);
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new BusinessException("目标库位名称过长");
        return normalized;
    }

    private static String normalizeOptionalText(String value, int maxLength, String tooLongMessage) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new BusinessException(tooLongMessage);
        return normalized;
    }

    private static String safeIssue(String value) {
        return value == null || value.isBlank() ? "当前信息无法生成精确预览" : value;
    }

    private static void requireUser(User user) {
        if (user == null || user.getId() == null || user.getId() <= 0) {
            throw new BusinessException(401, "当前用户未登录");
        }
    }

    private record ResolvedInput(
            FinishInboundExecutionPreviewItemDTO request,
            PalletCode pallet,
            String canonicalCode) {
    }

    private record ResolvedPreviewItem(
            PalletCode pallet,
            FinishInboundExecutionPreviewVO.Item publicItem,
            FinishInboundExecutionPreviewSnapshot.Item snapshotItem) {
    }

    private record MaterialSnapshot(
            List<FinishInboundExecutionPreviewSnapshot.MaterialInputState> inputs,
            List<FinishInboundExecutionPreviewSnapshot.MaterialBalanceState> balances) {
    }

    private static final class WarehouseOccupancy {
        private final int maxCapacity;
        private final int initialCapacity;
        private int reservedCount;

        private WarehouseOccupancy(int maxCapacity, int initialCapacity) {
            this.maxCapacity = maxCapacity;
            this.initialCapacity = initialCapacity;
        }

        private void reserve() {
            if (initialCapacity + reservedCount >= maxCapacity) {
                throw new BusinessException("目标库位当前没有可用容量");
            }
            reservedCount++;
        }
    }
}

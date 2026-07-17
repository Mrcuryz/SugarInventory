package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.InventoryLedgerAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PreparePoolBalanceAgentQueryDTO;
import com.Laibin.SugarInventory.domain.po.ScreenMesh;
import com.Laibin.SugarInventory.domain.vo.InventoryLedgerAgentVO;
import com.Laibin.SugarInventory.domain.vo.PreparePoolBalanceAgentVO;
import com.Laibin.SugarInventory.domain.vo.SemiPreparePoolBalanceVO;
import com.Laibin.SugarInventory.mapper.InventorySummaryMapper;
import com.Laibin.SugarInventory.mapper.model.InventoryLedgerAgentRow;
import com.Laibin.SugarInventory.service.InventoryAgentReadService;
import com.Laibin.SugarInventory.service.InventoryService;
import com.Laibin.SugarInventory.service.ScreenMeshService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryAgentReadServiceImpl implements InventoryAgentReadService {
    private final InventorySummaryMapper inventorySummaryMapper;
    private final InventoryService inventoryService;
    private final ScreenMeshService screenMeshService;

    @Override
    public InventoryLedgerAgentVO queryInventoryLedger(InventoryLedgerAgentQueryDTO query) {
        InventoryLedgerAgentQueryDTO source = query == null ? new InventoryLedgerAgentQueryDTO() : query;
        int page = page(source.getPage()), size = size(source.getSize()); validateRange(source.getEntryDateStart(), source.getEntryDateEnd());
        String product = text(source.getProductName(), 100, "productName"), warehouse = text(source.getWarehouseName(), 100, "warehouseName");
        String mesh = text(source.getScreenMeshName(), 100, "screenMeshName"), status = text(source.getProductStatus(), 50, "productStatus");
        long offset = (long) (page - 1) * size;
        List<InventoryLedgerAgentRow> rows = safe(inventorySummaryMapper.selectInventoryLedger(product, warehouse, mesh, status,
                source.getEntryDateStart(), source.getEntryDateEnd(), offset, size));
        Long total = inventorySummaryMapper.countInventoryLedger(product, warehouse, mesh, status, source.getEntryDateStart(), source.getEntryDateEnd());
        return InventoryLedgerAgentVO.builder().dataScope("CURRENT_INVENTORY_LEDGER_ROWS").total(total == null ? 0 : total)
                .page(page).size(size).inventoryAsOf(LocalDateTime.now()).records(rows.stream().map(this::ledgerRow).toList())
                .limitations(List.of("这是当前 inventory 表中的库存行快照，不是完整历史流水；recordedAt 仅表示该库存行记录时间。",
                        "生产日期来自托盘码已登记值，可能为空；结果不证明当前库存批次质量合格，也不执行库存变更。"))
                .build();
    }

    @Override
    public PreparePoolBalanceAgentVO queryPreparePoolBalance(PreparePoolBalanceAgentQueryDTO query) {
        PreparePoolBalanceAgentQueryDTO source = query == null ? new PreparePoolBalanceAgentQueryDTO() : query;
        if (Boolean.FALSE.equals(source.getPositiveOnly())) throw new BusinessException(422, "当前仅支持 positiveOnly=true 的现存正余额范围");
        int page = page(source.getPage()), size = size(source.getSize()); validateRange(source.getProductionDateStart(), source.getProductionDateEnd());
        String product = text(source.getProductName(), 100, "productName"), type = text(source.getProductType(), 50, "productType");
        Integer meshId = exactScreenMeshId(text(source.getScreenMeshName(), 100, "screenMeshName"));
        PageResult<SemiPreparePoolBalanceVO> result = inventoryService.pagePreparePoolBalance(product, type, meshId,
                source.getProductionDateStart(), source.getProductionDateEnd(), page, size);
        List<PreparePoolBalanceAgentVO.Row> rows = safe(result == null ? null : result.getRecords()).stream().map(this::balanceRow).toList();
        return PreparePoolBalanceAgentVO.builder().dataScope("CURRENT_POSITIVE_PREPARE_POOL_BALANCE")
                .total(result == null ? 0 : result.getTotal()).page(page).size(size).balanceAsOf(LocalDateTime.now()).records(rows)
                .limitations(List.of("仅包含 remainingPieces > 0 的历史半成品备料池当前余额，不提供零余额或已关闭记录的完整历史账。",
                        "余额不等于已为某生产订单保留、质量合格或可直接领用；本工具不执行占用、领用或库存调整。"))
                .build();
    }

    private InventoryLedgerAgentVO.Row ledgerRow(InventoryLedgerAgentRow item) {
        String location = (item.getSide() == null ? "" : item.getSide()) + (item.getRowNumber() == null ? "" : "-" + item.getRowNumber()) + (item.getLayer() == null ? "" : "-" + item.getLayer());
        return InventoryLedgerAgentVO.Row.builder().warehouseName(item.getWarehouseName()).productName(item.getProductName())
                .productType(item.getProductType()).productStatus(item.getProductStatus()).screenMeshName(item.getScreenMeshName())
                .palletCode(item.getPalletCode()).location(location.isBlank() ? null : location).palletQuantity(item.getPalletQuantity())
                .pieces(item.getPieces()).entryDate(item.getEntryDate()).productionDate(item.getProductionDate()).recordedAt(item.getRecordedAt()).build();
    }
    private PreparePoolBalanceAgentVO.Row balanceRow(SemiPreparePoolBalanceVO item) {
        return PreparePoolBalanceAgentVO.Row.builder().productName(item.getProductName()).productionDate(item.getProductionDate())
                .screenMeshName(item.getScreenMeshName()).inPieces(item.getInPieces()).consumedPieces(item.getConsumedPieces())
                .remainingPieces(item.getRemainingPieces()).piecesPerPallet(item.getPiecesPerPallet()).weightPerPiece(item.getWeightPerPiece())
                .remainingWeight(item.getRemainingWeight()).status(item.getStatus()).firstInAt(item.getFirstInAt()).lastInAt(item.getLastInAt())
                .lastConsumedAt(item.getLastConsumedAt()).build();
    }
    private Integer exactScreenMeshId(String name) {
        if (name == null) return null;
        List<ScreenMesh> matches = safe(screenMeshService.findScreenMeshes(name)).stream().filter(item -> name.equals(item.getMeshName())).toList();
        if (matches.isEmpty()) throw new BusinessException(404, "未找到名称完全匹配的筛网");
        if (matches.size() > 1) throw new BusinessException(409, "筛网名称存在多个完全匹配项，无法安全选择");
        return matches.getFirst().getId();
    }
    private void validateRange(LocalDate start, LocalDate end) { if (start != null && end != null && start.isAfter(end)) throw new BusinessException(400, "开始日期不能晚于结束日期"); }
    private int page(Integer value) { if (value == null) return 1; if (value < 1) throw new BusinessException(400, "page 必须大于 0"); return value; }
    private int size(Integer value) { int result = value == null ? 20 : value; if (result < 1 || result > 50) throw new BusinessException(400, "size 必须在 1 到 50 之间"); return result; }
    private String text(String value, int max, String field) { if (value == null || value.isBlank()) return null; String result = value.trim(); if (result.length() > max) throw new BusinessException(400, field + " 长度超限"); return result; }
    private <T> List<T> safe(List<T> rows) { return rows == null ? List.of() : rows; }
}

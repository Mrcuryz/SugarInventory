package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.domain.bo.AssayResolveResult;
import com.Laibin.SugarInventory.domain.po.Assay;
import com.Laibin.SugarInventory.domain.po.Inventory;
import com.Laibin.SugarInventory.domain.po.PalletCode;
import com.Laibin.SugarInventory.domain.po.PalletFlowRecord;
import com.Laibin.SugarInventory.domain.po.PalletTask;
import com.Laibin.SugarInventory.mapper.AssayMapper;
import com.Laibin.SugarInventory.mapper.InventoryMapper;
import com.Laibin.SugarInventory.mapper.PalletCodeMapper;
import com.Laibin.SugarInventory.mapper.PalletFlowRecordMapper;
import com.Laibin.SugarInventory.mapper.PalletTaskMapper;
import com.Laibin.SugarInventory.service.AssayResolveService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class AssayResolveServiceImpl implements AssayResolveService {

    @Autowired
    private AssayMapper assayMapper;
    @Autowired
    private PalletCodeMapper palletCodeMapper;
    @Autowired
    private InventoryMapper inventoryMapper;
    @Autowired
    private PalletTaskMapper palletTaskMapper;
    @Autowired
    private PalletFlowRecordMapper palletFlowRecordMapper;

    @Override
    @Transactional
    public AssayResolveResult resolveForPallet(PalletCode palletCode,
                                               PalletTask task,
                                               Inventory inventory,
                                               LocalDate businessDate,
                                               Integer operatorId,
                                               boolean writeBack) {
        if (palletCode == null) {
            return AssayResolveResult.none();
        }

        Assay explicit = selectMatchingAssay(palletCode.getAssayId(), palletCode);
        if (explicit != null) {
            return AssayResolveResult.found(explicit, "explicit_pallet_assay_id");
        }
        if (writeBack && palletCode.getAssayId() != null) {
            palletCode.setAssayId(null);
            palletCodeMapper.updateById(palletCode);
        }

        explicit = inventory == null ? null : selectMatchingAssay(inventory.getAssayId(), palletCode);
        if (explicit != null) {
            AssayResolveResult result = AssayResolveResult.found(explicit, "explicit_inventory_assay_id");
            backfillIfNeeded(result, palletCode, task, inventory, explicit, operatorId, "关联化验", writeBack);
            return result;
        }

        explicit = task == null ? null : selectMatchingAssay(task.getAssayId(), palletCode);
        if (explicit != null) {
            AssayResolveResult result = AssayResolveResult.found(explicit, "explicit_task_assay_id");
            backfillIfNeeded(result, palletCode, task, inventory, explicit, operatorId, "关联化验", writeBack);
            return result;
        }

        LocalDate fallbackDate = palletCode.getProductionDate() != null
                ? palletCode.getProductionDate()
                : (businessDate != null ? businessDate : (inventory == null ? null : inventory.getEntryDate()));
        if (palletCode.getProductId() == null || fallbackDate == null) {
            return AssayResolveResult.none();
        }

        List<Assay> candidates = assayMapper.selectCandidatesByProductIdAndDate(palletCode.getProductId(), fallbackDate);
        if (candidates == null || candidates.isEmpty()) {
            return AssayResolveResult.none();
        }

        Assay selected = selectUniqueLatestVersion(candidates);
        if (selected == null) {
            return AssayResolveResult.multiple(candidates.size());
        }

        AssayResolveResult result = AssayResolveResult.found(selected, "fallback_by_product_and_date");
        backfillIfNeeded(result, palletCode, task, inventory, selected, operatorId, "补关联化验", writeBack);
        return result;
    }

    private Assay selectMatchingAssay(Integer assayId, PalletCode palletCode) {
        if (assayId == null || palletCode == null) {
            return null;
        }
        Assay assay = assayMapper.selectById(assayId);
        if (assay == null) {
            return null;
        }
        LocalDate date = palletCode.getProductionDate();
        return Objects.equals(assay.getProductId(), palletCode.getProductId())
                && (date == null || Objects.equals(assay.getSampleDate(), date))
                ? assay : null;
    }

    private Assay selectUniqueLatestVersion(List<Assay> candidates) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        Integer maxVersion = candidates.stream()
                .map(Assay::getVersion)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);
        if (maxVersion == null) {
            return null;
        }
        List<Assay> latest = candidates.stream()
                .filter(assay -> Objects.equals(assay.getVersion(), maxVersion))
                .toList();
        return latest.size() == 1 ? latest.get(0) : null;
    }

    private void backfillIfNeeded(AssayResolveResult result,
                                  PalletCode palletCode,
                                  PalletTask task,
                                  Inventory inventory,
                                  Assay assay,
                                  Integer operatorId,
                                  String operationName,
                                  boolean writeBack) {
        if (!writeBack || assay == null || palletCode == null) {
            return;
        }
        boolean changed = false;
        boolean changedExistingAssay = false;
        if (!Objects.equals(palletCode.getAssayId(), assay.getId())) {
            changedExistingAssay = palletCode.getAssayId() != null;
            palletCode.setAssayId(assay.getId());
            palletCodeMapper.updateById(palletCode);
            changed = true;
        }
        if (inventory != null && !Objects.equals(inventory.getAssayId(), assay.getId())) {
            changedExistingAssay = changedExistingAssay || inventory.getAssayId() != null;
            inventoryMapper.updateAssayById(inventory.getId(), assay.getId());
            inventory.setAssayId(assay.getId());
            changed = true;
        } else if (inventory == null) {
            inventoryMapper.updateAssayByPalletCodeId(palletCode.getId(), assay.getId());
        }
        if (task != null && !Objects.equals(task.getAssayId(), assay.getId())) {
            changedExistingAssay = changedExistingAssay || task.getAssayId() != null;
            task.setAssayId(assay.getId());
            palletTaskMapper.updateById(task);
            changed = true;
        }
        if (changed) {
            result.setAutoBound(true);
            insertAssayFlowIfAbsent(
                    palletCode,
                    task,
                    assay,
                    operatorId,
                    changedExistingAssay ? "更新化验关联" : operationName
            );
        }
    }

    private void insertAssayFlowIfAbsent(PalletCode palletCode, PalletTask task, Assay assay,
                                         Integer operatorId, String operationName) {
        Integer cycleNo = palletCode.getCurrentCycleNo() == null ? 0 : palletCode.getCurrentCycleNo();
        Long existing = palletFlowRecordMapper.selectCount(new LambdaQueryWrapper<PalletFlowRecord>()
                .eq(PalletFlowRecord::getPalletCodeId, palletCode.getId())
                .eq(PalletFlowRecord::getCycleNo, cycleNo)
                .eq(PalletFlowRecord::getOperationType, "ASSAY")
                .eq(PalletFlowRecord::getAssayId, assay.getId()));
        if (existing != null && existing > 0) {
            return;
        }
        PalletFlowRecord flow = new PalletFlowRecord();
        flow.setPalletCodeId(palletCode.getId());
        flow.setTaskId(task == null ? null : task.getId());
        flow.setOperationType("ASSAY");
        flow.setOperationName(operationName);
        flow.setOperationTime(LocalDateTime.now());
        flow.setOperatorId(operatorId);
        flow.setProductId(palletCode.getProductId());
        flow.setProductStatus(palletCode.getProductStatus());
        flow.setAssayId(assay.getId());
        flow.setCycleNo(cycleNo);
        flow.setRemark("系统按当前产品和日期解析并关联化验记录");
        palletFlowRecordMapper.insert(flow);
    }
}

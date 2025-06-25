package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.OutRecordQueryDTO;
import com.Laibin.SugarInventory.domain.dto.OutStockRequestDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.*;
import com.Laibin.SugarInventory.domain.vo.*;
import com.Laibin.SugarInventory.mapper.*;
import com.Laibin.SugarInventory.service.LoggableService;
import com.Laibin.SugarInventory.service.OutStockService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OutStockServiceImpl implements OutStockService, LoggableService<OutStock> {

    private final InventoryMapper inventoryMapper;
    private final WarehouseMapper warehouseMapper;
    private final OutStockMapper outStockMapper;
    private final ProductMapper productMapper;
    private final AssayMapper assayMapper;
    private final UserMapper userMapper;

    @Transactional
    @Override
    public OutVO processOutStock(OutStockRequestDTO dto, Integer operatorId) {
        // 1. 获取库存记录（按先进后出排序）
        int remainingQuantity = dto.getQuantity(); // 需出库的总数量
        String currentSide = dto.getSide(); // 当前出库侧

        Warehouse warehouse = warehouseMapper.selectById(dto.getWarehouseId());

        Inventory curInventory = inventoryMapper.getLast(dto.getWarehouseId());
        if (curInventory == null) {
            throw new BusinessException(ErrorCode.INVENTORY_NOT_FOUND);
        }

        int curLayer = curInventory.getLayer(); // 当前堆积层数

        int curCapacity = 0;

        List<Inventory> inventoryList = new ArrayList<>();

        String nextSide; // 下一个出库侧
        if(currentSide.equals("左"))
            nextSide = "右";
        else
            nextSide = "左";

        // **1. 可堆积产品：先查找第二层**
        if (curLayer == 2 && remainingQuantity > 0) {
            inventoryList.addAll(inventoryMapper.
                    getInventoryForOutStock(dto.getWarehouseId(), currentSide, 2));
            inventoryList.addAll(inventoryMapper.
                    getInventoryForOutStock(dto.getWarehouseId(), nextSide, 2));
        }

        // **2. 查找第一层**
        inventoryList.addAll(inventoryMapper.
                getInventoryForOutStock(dto.getWarehouseId(), currentSide, 1));
        inventoryList.addAll(inventoryMapper.
                getInventoryForOutStock(dto.getWarehouseId(), nextSide, 1));

        curCapacity = inventoryList.size();

        if(curCapacity == 0) {
            throw new BusinessException(ErrorCode.INVENTORY_NOT_FOUND);
        }

        // **3. 开始逐个出库，并按批次统计出库数量**
        Map<String, BatchInfo> batchMap = new HashMap<>();
        int totalOutQuantity = 0;
        for (Inventory inventory : inventoryList) {
            if (remainingQuantity <= 0) break;

            // 创建批次键：产品ID + 生产日期
            String batchKey = inventory.getProductId() + "_" + inventory.getEntryDate();

            // 更新批次信息
            BatchInfo batchInfo = batchMap.getOrDefault(batchKey, new BatchInfo(
                    inventory.getProductId(),
                    inventory.getEntryDate(),
                    0
            ));

            batchInfo.incrementQuantity();
            batchMap.put(batchKey, batchInfo);

            // **直接删除该板**
            inventoryMapper.deleteInventoryById(inventory.getId());
            remainingQuantity--;
            totalOutQuantity++;
        }

        // **4. 为每个批次创建出库记录**
        List<OutStock> outStockList = new ArrayList<>();
        LocalDate outDate = LocalDate.now();
        LocalDateTime createdAt = LocalDateTime.now();

        for (BatchInfo batchInfo : batchMap.values()) {
            Product product = productMapper.selectById(batchInfo.getProductId());

            // 获取化验数据
            Assay assay = assayMapper.selectByProductIdAndDate(batchInfo.getProductId(), batchInfo.getEntryDate());
            if (assay == null) {
                throw new BusinessException(ErrorCode.ASSAY_NOT_FOUND);
            }

            // 计算该批次的总重量
            BigDecimal totalWeight = product.getWeightPerPiece()
                    .multiply(new BigDecimal(batchInfo.getQuantity())
                            .multiply(new BigDecimal(product.getPiecesPerPallet())));

            OutStock outStock = new OutStock();
            outStock.setWarehouseId(dto.getWarehouseId());
            outStock.setProductId(batchInfo.getProductId());
            outStock.setQuantity(batchInfo.getQuantity()); // 该批次的出库数量
            outStock.setTotalWeight(totalWeight);
            outStock.setOperatorId(operatorId);
            outStock.setInDate(batchInfo.getEntryDate()); // 该批次的入库日期
            outStock.setOutDate(outDate);
            outStock.setCreatedAt(createdAt);
            outStock.setAssayId(assay.getId());

            outStockList.add(outStock);
        }

        // 批量插入出库记录
        if (!outStockList.isEmpty()) {
            for (OutStock outStock : outStockList) {
                outStockMapper.insert(outStock);
            }
        }

        // **5. 如果出库数量不足，返回错误**
        if (remainingQuantity > 0) {
            if(warehouse.getMaxRows() * 2 != warehouse.getMaxCapacity())
                warehouseMapper.updateMaxCapacity(warehouse.getId(), warehouse.getMaxRows() * 2);

            warehouseMapper.updateCurCapacity(dto.getWarehouseId(),
                    warehouse.getCurCapacity() - totalOutQuantity);
            OutVO outVO = new OutVO();
            outVO.setRemainingQuantity(remainingQuantity);
            outVO.setMessage("当前库存不足！还差 " + remainingQuantity + " 板");
            return outVO;
        }

        // **6. 同步更新库位信息**
        warehouseMapper.updateCurCapacity(dto.getWarehouseId(),
                warehouse.getCurCapacity() - totalOutQuantity);

        // **7. 返回出库结果**
        OutVO outVO = new OutVO();
        outVO.setRemainingQuantity(0);
        outVO.setMessage("出库成功！");
        return outVO;
    }

    @Transactional
    @Override
    public OutVO processStackOutStock(OutStockRequestDTO dto, Integer operatorId) {
        int remainingQuantity = dto.getQuantity();
        int totalOutQuantity = 0;

        Warehouse warehouse = warehouseMapper.selectById(dto.getWarehouseId());
        if (warehouse == null) throw new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND);

        List<Inventory> inventoryList = inventoryMapper.getInventoryStackOrder(dto.getWarehouseId());
        if (inventoryList.isEmpty()) throw new BusinessException(ErrorCode.INVENTORY_NOT_FOUND);

        Map<String, BatchInfo> batchMap = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDate outDate = LocalDate.now();

        for (Inventory inv : inventoryList) {
            if (remainingQuantity <= 0) break;

            String batchKey = inv.getProductId() + "_" + inv.getEntryDate();
            BatchInfo batch = batchMap.getOrDefault(batchKey, new BatchInfo(inv.getProductId(), inv.getEntryDate(), 0));
            batch.incrementQuantity();
            batchMap.put(batchKey, batch);

            inventoryMapper.deleteInventoryById(inv.getId());
            remainingQuantity--;
            totalOutQuantity++;
        }

        // 批量插入出库记录
        for (BatchInfo batchInfo : batchMap.values()) {
            Product product = productMapper.selectById(batchInfo.getProductId());
            if (product == null) continue;

            Assay assay = assayMapper.selectByProductIdAndDate(batchInfo.getProductId(), batchInfo.getEntryDate());
            if (assay == null) throw new BusinessException(ErrorCode.ASSAY_NOT_FOUND);

            BigDecimal totalWeight = product.getWeightPerPiece()
                    .multiply(new BigDecimal(batchInfo.getQuantity())
                            .multiply(new BigDecimal(product.getPiecesPerPallet())));

            OutStock outStock = new OutStock();
            outStock.setWarehouseId(dto.getWarehouseId());
            outStock.setProductId(batchInfo.getProductId());
            outStock.setQuantity(batchInfo.getQuantity());
            outStock.setTotalWeight(totalWeight);
            outStock.setOperatorId(operatorId);
            outStock.setInDate(batchInfo.getEntryDate());
            outStock.setOutDate(outDate);
            outStock.setCreatedAt(now);
            outStock.setAssayId(assay.getId());

            outStockMapper.insert(outStock);
        }

        warehouseMapper.updateCurCapacity(dto.getWarehouseId(),
                warehouse.getCurCapacity() - totalOutQuantity);

        // 处理最大容量字段自动修正（如果历史有异常）
        if (warehouse.getMaxRows() * 2 != warehouse.getMaxCapacity()) {
            warehouseMapper.updateMaxCapacity(dto.getWarehouseId(), warehouse.getMaxRows() * 2);
        }

        OutVO vo = new OutVO();
        vo.setRemainingQuantity(remainingQuantity);
        vo.setMessage(remainingQuantity > 0
                ? "库存不足，剩余 " + remainingQuantity + " 板未出库"
                : "出库成功！");
        return vo;
    }


    @Override
    public PageResult<OutStockRecordVO> searchOutRecords(OutRecordQueryDTO query, User user) {
        int offset = (query.getPage() - 1) * query.getSize();

        boolean isStaff = user.getRoleCode().equals("STAFF");

        List<OutStockRecordVO> recordVOList = outStockMapper
                .selectOutStockRecordsByQuery(query, offset, query.getSize(), isStaff, user.getName());

        for(OutStockRecordVO record : recordVOList){
            Integer id = record.getTestedBy();
            if(id == null) continue;
            String testerName = userMapper.selectById(id).getName();
            record.setTesterName(testerName);

            if(isStaff){
                record.setAssayId(null);
                record.setSampleDate(null);
                record.setColorValue(null);
                record.setReducingSugar(null);
                record.setDryWeight(null);
                record.setConductivityAsh(null);
                record.setSucrose(null);
                record.setInsolubleImpurity(null);
                record.setPhValue(null);
                record.setTesterName(null);
                record.setIsQualified(null);
                record.setQualifiedStandards(null);
            }
        }

        Long total = outStockMapper.countOutStockRecordsByQuery(query, isStaff, user.getName());

        return new PageResult<>(total, recordVOList);

    }

    @Override
    public OutStock findById(Integer id) {
        return outStockMapper.selectById(id);
    }

    @Override
    public String getTableName() {
        return "out_stock";
    }

    @Getter
    private static class BatchInfo {
        private Integer productId;
        private LocalDate entryDate;
        private int quantity;

        public BatchInfo(Integer productId, LocalDate entryDate, int quantity) {
            this.productId = productId;
            this.entryDate = entryDate;
            this.quantity = quantity;
        }

        public void incrementQuantity() {
            this.quantity++;
        }
    }
}

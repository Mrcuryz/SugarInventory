package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.OutProductQueryDTO;
import com.Laibin.SugarInventory.domain.dto.OutRecordQueryDTO;
import com.Laibin.SugarInventory.domain.dto.OutStockRequestDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.*;
import com.Laibin.SugarInventory.domain.vo.OutProductVO;
import com.Laibin.SugarInventory.domain.vo.OutStockRecordVO;
import com.Laibin.SugarInventory.domain.vo.OutVO;
import com.Laibin.SugarInventory.domain.vo.ProductVO;
import com.Laibin.SugarInventory.mapper.*;
import com.Laibin.SugarInventory.service.LoggableService;
import com.Laibin.SugarInventory.service.OutStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutStockServiceImpl implements OutStockService, LoggableService<OutStock> {

    private final InventoryMapper inventoryMapper;
    private final WarehouseMapper warehouseMapper;
    private final OutStockMapper outStockMapper;
    private final ProductMapper productMapper;

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

        Product product = productMapper.selectById(curInventory.getProductId());

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

        // **3. 开始逐个出库**
        for (Inventory inventory : inventoryList) {
            if (remainingQuantity <= 0) break;

            // **直接删除该板**
            inventoryMapper.deleteInventoryById(inventory.getId());
            remainingQuantity--;
        }

        int quantity = dto.getQuantity() > curCapacity? curCapacity : dto.getQuantity();

        // **4. 如果出库数量不足，返回错误**
        if (remainingQuantity > 0) {
            if(warehouse.getMaxRows() * 2 != warehouse.getMaxCapacity())
                warehouseMapper.updateMaxCapacity(warehouse.getId(), warehouse.getMaxRows() * 2);

            warehouseMapper.updateCurCapacity(dto.getWarehouseId(),
                    warehouse.getCurCapacity() - (quantity * product.getPiecesPerPallet()));
            OutVO outVO = new OutVO();
            outVO.setRemainingQuantity(remainingQuantity);
            outVO.setMessage("当前库存不足！还差 " + remainingQuantity + " 板");
            return outVO;
        }

        BigDecimal totalWeight = product.getWeightPerPiece()
                .multiply(new BigDecimal(quantity)
                        .multiply(new BigDecimal(product.getPiecesPerPallet())));

        // **5. 记录出库信息**
        OutStock outStock = new OutStock();
        outStock.setWarehouseId(dto.getWarehouseId());
        outStock.setProductId(curInventory.getProductId());
        outStock.setQuantity(quantity);
        outStock.setTotalWeight(totalWeight);
        outStock.setOperatorId(operatorId);
        outStock.setInDate(curInventory.getEntryDate());
        outStock.setOutDate(LocalDate.now());
        outStock.setCreatedAt(LocalDateTime.now());
        outStockMapper.insert(outStock);

        // **6. 同步更新库位信息**
        warehouseMapper.updateCurCapacity(dto.getWarehouseId(),
                warehouse.getCurCapacity() - (quantity * product.getPiecesPerPallet()));

        // **7. 返回出库结果**
        OutVO outVO = new OutVO();
        outVO.setRemainingQuantity(0);
        outVO.setMessage("出库成功！");
        return outVO;
    }

    @Override
    public PageResult<OutStockRecordVO> searchOutRecords(OutRecordQueryDTO query) {
        int offset = (query.getPage() - 1) * query.getSize();

        List<OutStockRecordVO> recordVOList = outStockMapper
                .selectOutStockRecordsByQuery(query, offset, query.getSize());

        Long total = outStockMapper.countOutStockRecordsByQuery(query);

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
}

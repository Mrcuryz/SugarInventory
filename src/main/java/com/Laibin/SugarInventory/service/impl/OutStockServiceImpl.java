package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.OutProductQueryDTO;
import com.Laibin.SugarInventory.domain.dto.OutStockRequestDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.*;
import com.Laibin.SugarInventory.domain.vo.OutProductVO;
import com.Laibin.SugarInventory.domain.vo.ProductVO;
import com.Laibin.SugarInventory.mapper.*;
import com.Laibin.SugarInventory.service.LoggableService;
import com.Laibin.SugarInventory.service.OutStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutStockServiceImpl implements OutStockService, LoggableService<OutStock> {

    private final InventoryMapper inventoryMapper;
    private final InventoryLocationMapper locationMapper;
    private final OutStockMapper outStockMapper;
    private final ProductMapper productMapper;

    @Transactional
    @Override
    public void processOutStock(OutStockRequestDTO request, Integer operatorId) {
        // 1. 获取产品信息
        Product product = productMapper.selectById(request.getProductId());
        if (product == null) throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);

        // 2. 获取库存批次
        Inventory inventory = inventoryMapper.existSameInventory(
                request.getWarehouseId(),
                request.getProductId(),
                request.getInDate(),
                request.getScreenMeshId()
        );
        if (inventory == null) throw new BusinessException(ErrorCode.INVENTORY_NOT_FOUND);
        // 3. 扣减库存位置
        Integer totalDeduct = 0;
        for (OutStockRequestDTO.LocationQty item : request.getLocations()) {
            InventoryLocation location = locationMapper.selectForUpdate(
                    inventory.getId(),
                    item.getCoordinates().getX(),
                    item.getCoordinates().getY()
            );

            if (location == null || location.getQuantity().compareTo(item.getQuantity()) < 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
            }

            int affected = locationMapper.deductQuantity(location.getId(), item.getQuantity());
            if (affected <= 0) throw new BusinessException(ErrorCode.UPDATE_INVENTORY_FAILED);
            totalDeduct += item.getQuantity();
        }

        // 4. 更新库存总量
        inventoryMapper.reduceTotalQuantity(
                inventory.getId(),
                totalDeduct
        );

        // 5. 记录出库
        OutStock record = new OutStock();
        record.setWarehouseId(request.getWarehouseId());
        record.setProductId(request.getProductId());
        record.setQuantity(totalDeduct);
        record.setWeightPerPiece(product.getWeightPerPiece());
        record.setInDate(request.getInDate());
        record.setOutDate(LocalDateTime.now());
        record.setOperatorId(operatorId);
        outStockMapper.insert(record);
    }

    @Override
    public List<OutProductVO> searchProducts(OutProductQueryDTO query) {
        return outStockMapper.selectProductsByQuery(query);
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

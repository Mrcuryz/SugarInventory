package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.InventoryQueryDTO;
import com.Laibin.SugarInventory.domain.dto.OutProductQueryDTO;
import com.Laibin.SugarInventory.domain.dto.OutStockBatchQueryDTO;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.vo.OutProductVO;
import com.Laibin.SugarInventory.domain.vo.OutWarehouseVO;
import com.Laibin.SugarInventory.domain.vo.VInventorySummary;
import com.Laibin.SugarInventory.domain.vo.VWarehouseCapacity;
import com.Laibin.SugarInventory.mapper.InventoryMapper;
import com.Laibin.SugarInventory.mapper.InventorySummaryMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {
    @Autowired
    private InventorySummaryMapper summaryMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private InventoryMapper inventoryMapper;

    @Override
    public PageResult<VInventorySummary> getInventorySummary(InventoryQueryDTO query) {
        int offset = (query.getPage() - 1) * query.getSize();

        List<VInventorySummary> records = summaryMapper.selectSummaryList(
                query, offset, query.getSize()
        );
        String stockInfo = "%s板%s件";
        records.forEach(summary -> {
            Product product = productMapper.selectById(summary.getProductId());
            BigDecimal totalWeight = product.getWeightPerPiece()
                    .multiply(new BigDecimal(product.getPiecesPerPallet() * summary.getTotalQuantity()))
                    .add(product.getWeightPerPiece().multiply(new BigDecimal(summary.getTotalPieces())));
            summary.setTotalWeight(totalWeight);
            summary.setStockInfo(String.format(stockInfo,
                    summary.getTotalQuantity() + summary.getTotalPieces() / product.getPiecesPerPallet(),
                    summary.getTotalPieces() % product.getPiecesPerPallet()));
        });

        Long total = summaryMapper.countSummary(query);

        return new PageResult<>(total, records);
    }

    @Override
    public List<OutWarehouseVO> getQualifiedWarehouses(OutProductQueryDTO queryDTO) {
        return inventoryMapper.findWarehousesByCondition(queryDTO);
    }

    @Override
    public List<OutProductVO> getInventoryDetails(Integer warehouseId, OutProductQueryDTO queryDTO) {
        return inventoryMapper.findInventoryByWarehouse(warehouseId, queryDTO);
    }

    @Override
    public List<VWarehouseCapacity> getWarehouses() {
        return summaryMapper.selectCapacityList();
    }

    @Override
    public PageResult<VWarehouseCapacity> queryWarehouses(String warehouseName, List<Integer> warehouseIds, Integer page, Integer size, String status) {
        int offset = (page - 1) * size;

        List<VWarehouseCapacity> records =
                summaryMapper.selectCapacityListByStatus(warehouseName, warehouseIds, status, offset, size);
        Long total = summaryMapper.countCapacityByStatus(warehouseName, status, warehouseIds);

        return new PageResult<>(total, records);
    }

    @Override
    public PageResult<VWarehouseCapacity> batchQueryWarehouses(OutStockBatchQueryDTO queryDTO) {
        int offset = (queryDTO.getPage() - 1) * queryDTO.getSize();

        if (queryDTO.getIds() == null || queryDTO.getIds().isEmpty()) {
            return new PageResult<>(0L, null);
        }

        List<VWarehouseCapacity> records =
                summaryMapper.selectCapacityListByStatus(
                        null,
                        queryDTO.getIds(),
                        null,
                        offset,
                        queryDTO.getSize());
        Long total = summaryMapper.countCapacityByStatus(null, null, queryDTO.getIds());
        return new PageResult<>(total, records);
    }

    @Override
    public List<VInventorySummary> getProductStock(String productStatus, String productName) {
        return summaryMapper.selectProductTotalStock(productStatus, productName);
    }
}

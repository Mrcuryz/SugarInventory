package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.InventoryQueryDTO;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.vo.VInventorySummary;
import com.Laibin.SugarInventory.domain.vo.VWarehouseCapacity;
import com.Laibin.SugarInventory.mapper.InventorySummaryMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {
    @Autowired
    private InventorySummaryMapper summaryMapper;

    @Autowired
    private ProductMapper productMapper;

    @Override
    public PageResult<VInventorySummary> getInventorySummary(InventoryQueryDTO query) {
        int offset = (query.getPage() - 1) * query.getSize();

        List<VInventorySummary> records = summaryMapper.selectSummaryList(
                query, offset, query.getSize()
        );

        records.forEach(summary -> {
            Product product = productMapper.selectById(summary.getProductId());
            summary.setTotalWeight(product.getWeightPerPiece()
                    .multiply(new BigDecimal(product.getPiecesPerPallet() * summary.getTotalQuantity())));
                });

        Long total = summaryMapper.countSummary(query);

        return new PageResult<>(total, records);
    }

    @Override
    public List<VWarehouseCapacity> getWarehouses() {
        return summaryMapper.selectCapacityList();
    }
}

package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.WarehouseCapacityDistributionQueryDTO;
import com.Laibin.SugarInventory.domain.vo.VWarehouseCapacity;
import com.Laibin.SugarInventory.service.impl.WarehouseAgentReadServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WarehouseAgentReadServiceImplTest {
    @Test
    void returnsCapacityFactsWithoutInternalWarehouseIdsOrRiskJudgement() {
        InventoryService inventoryService = mock(InventoryService.class);
        VWarehouseCapacity empty = row(1, "1号库位", 0, 10);
        VWarehouseCapacity high = row(2, "2号库位", 9, 10);
        when(inventoryService.getWarehouses()).thenReturn(List.of(empty, high));
        WarehouseCapacityDistributionQueryDTO query = new WarehouseCapacityDistributionQueryDTO();
        query.setOccupancyBand("HIGH");

        var result = new WarehouseAgentReadServiceImpl(inventoryService, mock(com.Laibin.SugarInventory.mapper.PalletFlowRecordMapper.class), mock(com.Laibin.SugarInventory.mapper.InventorySummaryMapper.class)).queryCapacityDistribution(query);

        assertThat(result.getRecords()).singleElement().satisfies(item -> {
            assertThat(item.getWarehouseName()).isEqualTo("2号库位");
            assertThat(item.getOccupancyRate()).isEqualByComparingTo("90.00");
            assertThat(item.getRemainingCapacity()).isEqualByComparingTo("1");
        });
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("不是业务风险阈值"));
    }

    @Test
    void singleWarehouseScopeAndAvailabilityAreDeterministic() {
        InventoryService inventoryService = mock(InventoryService.class);
        when(inventoryService.getWarehouses()).thenReturn(List.of(row(1, "1号库位", 10, 10), row(2, "2号库位", 2, 10)));
        WarehouseCapacityDistributionQueryDTO query = new WarehouseCapacityDistributionQueryDTO();
        query.getWarehouseScope().setType("SINGLE_WAREHOUSE"); query.getWarehouseScope().setWarehouseId(2);
        query.setOnlyAvailable(true);

        var result = new WarehouseAgentReadServiceImpl(inventoryService, mock(com.Laibin.SugarInventory.mapper.PalletFlowRecordMapper.class), mock(com.Laibin.SugarInventory.mapper.InventorySummaryMapper.class)).queryCapacityDistribution(query);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords().getFirst().getWarehouseName()).isEqualTo("2号库位");
    }

    @Test
    void recentOperationsMapInternalFlowTypesToStableBusinessCategories() {
        InventoryService inventoryService = mock(InventoryService.class);
        var mapper = mock(com.Laibin.SugarInventory.mapper.PalletFlowRecordMapper.class);
        var operation = new com.Laibin.SugarInventory.domain.vo.WarehouseRecentOperationVO();
        operation.setOperationType("ORDER_MATERIAL_PICK"); operation.setProductName("半成品糖");
        when(mapper.queryRecentWarehouseOperations(org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(20))).thenReturn(List.of(operation));
        var query = new com.Laibin.SugarInventory.domain.dto.WarehouseRecentOperationsAgentQueryDTO();
        query.setWarehouseId(2); query.setEventTypes(List.of("OUTBOUND"));

        var result = new WarehouseAgentReadServiceImpl(inventoryService, mapper, mock(com.Laibin.SugarInventory.mapper.InventorySummaryMapper.class)).queryRecentOperations(query);

        assertThat(result.getRecords()).singleElement().satisfies(item -> {
            assertThat(item.getEventType()).isEqualTo("OUTBOUND");
            assertThat(item.getProductName()).isEqualTo("半成品糖");
        });
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("不代表完整操作日志"));
    }

    @Test
    void mixedStorageReturnsFactsWithoutInventingRiskRule() {
        var summaryMapper = mock(com.Laibin.SugarInventory.mapper.InventorySummaryMapper.class);
        var row = new com.Laibin.SugarInventory.mapper.model.WarehouseMixedStorageFactRow();
        row.setWarehouseName("1号库位"); row.setProductCount(2); row.setProductTypeCount(1);
        row.setSpecificationCount(3); row.setInventoryRecordCount(4); row.setProductLabels("单晶冰糖、老冰糖");
        when(summaryMapper.selectWarehouseMixedStorageFacts(null)).thenReturn(List.of(row));
        var query = new com.Laibin.SugarInventory.domain.dto.WarehouseMixedStorageFactsQueryDTO();

        var result = new WarehouseAgentReadServiceImpl(mock(InventoryService.class),
                mock(com.Laibin.SugarInventory.mapper.PalletFlowRecordMapper.class), summaryMapper).queryMixedStorageFacts(query);

        assertThat(result.getRecords()).singleElement().satisfies(item -> {
            assertThat(item.getProductCount()).isEqualTo(2);
            assertThat(item.getObservedFacts()).contains("同库位存在 2 个不同产品");
        });
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("不得自动解释为违规"));
    }

    private VWarehouseCapacity row(int id, String name, int current, int maximum) {
        VWarehouseCapacity row = new VWarehouseCapacity(); row.setWarehouseId(id); row.setWarehouseName(name);
        row.setStatus("正常"); row.setCurCapacity(BigDecimal.valueOf(current)); row.setMaxCapacity(BigDecimal.valueOf(maximum));
        return row;
    }
}

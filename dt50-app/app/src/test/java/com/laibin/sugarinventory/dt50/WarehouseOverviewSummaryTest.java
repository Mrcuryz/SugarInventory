package com.laibin.sugarinventory.dt50;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class WarehouseOverviewSummaryTest {
    @Test
    public void summarizesCapacityUsingActualValues() {
        WarehouseOverviewSummary summary = WarehouseOverviewSummary.from(List.of(
                new WarehouseCapacityItem(1, "1号库位", "空置", 0, 10, 0, 0, 0),
                new WarehouseCapacityItem(2, "2号库位", "正常", 4, 10, 40, 4, 2),
                new WarehouseCapacityItem(3, "3号库位", "满仓", 10, 10, 100, 10, 1)));

        assertEquals(3, summary.warehouseCount);
        assertEquals(1, summary.emptyCount);
        assertEquals(1, summary.fullCount);
        assertEquals(47, summary.occupancyPercentage());
    }

    @Test
    public void emptyInputHasZeroOccupancy() {
        WarehouseOverviewSummary summary = WarehouseOverviewSummary.from(List.of());

        assertEquals(0, summary.warehouseCount);
        assertEquals(0, summary.occupancyPercentage());
    }
}

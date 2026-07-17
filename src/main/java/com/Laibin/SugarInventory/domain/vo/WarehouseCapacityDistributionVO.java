package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Value
@Builder
public class WarehouseCapacityDistributionVO {
    String dataScope;
    long total;
    int page;
    int size;
    Summary summary;
    List<Row> records;
    Map<String, String> occupancyBandDefinition;
    List<String> limitations;

    @Value @Builder
    public static class Summary {
        int warehouseCount;
        BigDecimal currentCapacity;
        BigDecimal maximumCapacity;
        BigDecimal remainingCapacity;
        BigDecimal occupancyRate;
        int emptyCount;
        int fullCount;
    }

    @Value @Builder
    public static class Row {
        String warehouseName;
        String status;
        BigDecimal currentCapacity;
        BigDecimal maximumCapacity;
        BigDecimal remainingCapacity;
        BigDecimal occupancyRate;
        String occupancyBand;
        Integer currentPalletCount;
        Integer currentProductCount;
    }
}

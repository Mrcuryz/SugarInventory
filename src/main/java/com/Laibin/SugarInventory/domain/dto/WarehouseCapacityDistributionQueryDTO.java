package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;

@Data
public class WarehouseCapacityDistributionQueryDTO {
    private WarehouseScope warehouseScope = new WarehouseScope();
    private String occupancyBand = "ANY";
    private Boolean onlyAvailable = false;
    private Integer page = 1;
    private Integer size = 20;

    @Data
    public static class WarehouseScope {
        private String type = "ALL";
        private Integer warehouseId;
    }
}

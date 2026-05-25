package com.Laibin.SugarInventory.production.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProductionLabelReserveDTO {
    private List<Item> items;
    private String remark;

    @Data
    public static class Item {
        private Integer productId;
        private Integer boardCount = 0;
        private Integer pieceCount = 0;
        private Integer qrCount;
    }
}

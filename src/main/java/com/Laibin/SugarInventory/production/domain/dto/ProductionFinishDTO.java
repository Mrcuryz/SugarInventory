package com.Laibin.SugarInventory.production.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class ProductionFinishDTO {
    @NotEmpty
    private List<Item> items;
    private String remark;

    @Data
    public static class Item {
        private Integer productId;
        private LocalDate productionDate;
        private Integer boardCount = 0;
        private Integer pieceCount = 0;
    }
}

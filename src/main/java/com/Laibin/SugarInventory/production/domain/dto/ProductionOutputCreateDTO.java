package com.Laibin.SugarInventory.production.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProductionOutputCreateDTO {
    @NotNull
    private Integer productId;

    @NotNull
    private LocalDate productionDate;

    private Integer boardCount = 0;
    private Integer pieceCount = 0;
    private String remark;
}

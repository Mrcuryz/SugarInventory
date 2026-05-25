package com.Laibin.SugarInventory.production.domain.dto;

import lombok.Data;

@Data
public class ProductionOutputBindQrDTO {
    private Boolean autoPrint = false;
    private String remark;
}

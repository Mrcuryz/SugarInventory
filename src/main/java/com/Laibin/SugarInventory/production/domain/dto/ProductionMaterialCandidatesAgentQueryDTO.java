package com.Laibin.SugarInventory.production.domain.dto;

import lombok.Data;

@Data
public class ProductionMaterialCandidatesAgentQueryDTO {
    private String orderRef;
    private Integer page = 1;
    private Integer size = 20;
}

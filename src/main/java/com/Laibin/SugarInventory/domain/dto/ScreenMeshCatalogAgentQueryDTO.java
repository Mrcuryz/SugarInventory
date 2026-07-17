package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;

@Data
public class ScreenMeshCatalogAgentQueryDTO {
    private String meshName;
    private Integer page = 1;
    private Integer size = 20;
}

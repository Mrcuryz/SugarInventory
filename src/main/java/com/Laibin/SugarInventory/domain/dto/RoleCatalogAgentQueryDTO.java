package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;

@Data
public class RoleCatalogAgentQueryDTO {
    private String keyword;
    private String status;
    private Integer page = 1;
    private Integer size = 20;
}

package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;

@Data
public class ProductCatalogAgentQueryDTO {
    private String productName;
    private String productType;
    private String productStatus;
    private String packagingMethod;
    private String screenMeshName;
    private Integer page = 1;
    private Integer size = 20;
}

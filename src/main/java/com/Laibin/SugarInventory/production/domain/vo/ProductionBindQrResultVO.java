package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class ProductionBindQrResultVO {
    private Long outputId;
    private Integer requiredQrCount;
    private Integer newlyBoundCount;
    private Integer boundQrCount;
    private List<ProductionOutputCodeVO> codes;
}

package com.Laibin.SugarInventory.domain.redis;

import lombok.Data;

import java.util.List;

@Data
public class ProductionConsumptionItem {
    private String materialNameRaw;
    private Integer productId;
    private String productName;
    private String sourceType;
    private String warehouseHint;
    private String batchNo;
    private String remark;
    private Boolean matchedProduct;
    private List<ProductionConsumptionEntry> items;
}

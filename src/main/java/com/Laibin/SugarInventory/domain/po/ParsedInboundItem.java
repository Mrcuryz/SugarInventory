package com.Laibin.SugarInventory.domain.po;

import com.Laibin.SugarInventory.domain.po.ParsedSemiSource;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ParsedInboundItem {

    private Integer index;
    /**
     * "SEMI_PRODUCT_IN" / "FINISHED_PRODUCT_IN" / "OTHER"
     */
    private String type;

    @JsonProperty("raw_block")
    private String rawBlock;

    private String remark;

    // 原始品名
    @JsonProperty("product_name_raw")
    private String productNameRaw;

    // LLM 已经从 productCatalog 里选择好的
    @JsonProperty("product_id")
    private Integer productId;

    @JsonProperty("product_name")
    private String productName;

    // LLM 已经从 warehouseCatalog 里选择好的
    @JsonProperty("warehouse_id")
    private Integer warehouseId;

    @JsonProperty("warehouse_name")
    private String warehouseName;

    /**
     * 文本里的库位原文，如“6号烘房”“3号库位”
     */
    @JsonProperty("location")
    private String location;

    /**
     * "2025-11-25"
     */
    @JsonProperty("production_date")
    private String productionDate;

    /**
     * 数量对象
     */
    private Quantity quantity;

    private List<ParsedSemiSource> sources;

    @Data
    public static class Quantity {
        private Integer pallets;
        private Integer pieces;
    }
}

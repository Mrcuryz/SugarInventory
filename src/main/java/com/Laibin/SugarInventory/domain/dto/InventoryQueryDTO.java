package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "库存详情查询DTO")
public class InventoryQueryDTO {
    @Schema(description = "库位ID")
    private Integer warehouseId;
    @Schema(description = "产品名称")
    private String productName;
    @Schema(description = "页码")
    private Integer page = 1;
    @Schema(description = "每页数量")
    private Integer size = 10;
}
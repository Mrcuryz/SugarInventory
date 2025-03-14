package com.Laibin.SugarInventory.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "符合标准的库位VO")
public class OutWarehouseVO {
    @Schema(description = "库位ID")
    private Integer warehouseId;

    @Schema(description = "库位名称")
    private String warehouseName;
}

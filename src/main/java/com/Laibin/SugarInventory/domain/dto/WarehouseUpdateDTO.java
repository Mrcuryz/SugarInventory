package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "库位更新DTO")
public class WarehouseUpdateDTO {
    @Schema(description = "id")
    private Integer id;
    @Schema(description = "库位名称")
    private String warehouseName;
    @Schema(description = "最大排数")
    private Integer maxRows;
}

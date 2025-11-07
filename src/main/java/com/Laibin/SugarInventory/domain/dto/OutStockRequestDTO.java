package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "出库请求DTO")
public class OutStockRequestDTO {

    @NotNull(message = "产品id不能为空")
    @Schema(description = "产品id", example = "101")
    private Integer productId;

    @NotNull(message = "库位id不能为空")
    @Schema(description = "库位ID", example = "101")
    private Integer warehouseId;

    @NotNull(message = "出库数量不能为空")
    @Min(value = 1, message = "出库数量不能小于1")
    @Schema(description = "出库数量", example = "10")
    private Integer quantity;

    @NotNull(message = "出库数量单位不能为空")
    @Schema(description = "出库单位：0板1件", example = "0")
    private String unit;

    @NotNull(message = "出库类型不能为空")
    @Schema(description = "0整版优先1散件优先", example = "0")
    private Integer outType;

    @Schema(description = "先从左/右侧出库，默认为左", example = "LEFT")
    private String side = "左";
}

package com.Laibin.SugarInventory.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "库位扫码解析结果")
public class WarehouseLocationCodeVO {
    @Schema(description = "受控库位码内容")
    private String code;

    @Schema(description = "库位ID")
    private Integer warehouseId;

    @Schema(description = "库位名称")
    private String warehouseName;

    @Schema(description = "库位状态")
    private String status;

    @Schema(description = "当前库存量（板）")
    private Integer curCapacity;

    @Schema(description = "最大库存量（板）")
    private Integer maxCapacity;
}

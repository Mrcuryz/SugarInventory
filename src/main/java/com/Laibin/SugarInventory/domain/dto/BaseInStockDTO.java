package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class BaseInStockDTO {

    @NotNull(message = "产品ID不能为空")
    @Schema(description = "产品ID", example = "57")
    private Integer productId;

    @NotNull(message = "仓库名称不能为空")
    @Schema(description = "仓库ID", example = "101")
    private String warehouseName;

    @NotNull(message = "入库日期不能为空")
    @Schema(description = "入库日期", example = "2025-05-01")
    private LocalDate entryDate;

    @NotNull(message = "数量（板）不能为空")
    @Schema(description = "数量（板）", example = "30")
    private Integer quantity;

    @NotNull(message = "单位不能为空")
    @Schema(description = "单位0板1件（板/件）", example = "30")
    private String unit;

    @Schema(description = "库位左/右列，默认为左", example = "左")
    private String side = "左";  // 默认左侧

    @Schema(description = "筛网规格ID", example = "2")
    private Integer screenMeshId;

    @Schema(description = "入库ID", hidden = true)
    private Integer inStockId;

    @Schema(description = "托盘码ID", hidden = true)
    private Integer palletCodeId;

    @Schema(description = "标记是否退货入库：0不是1是")
    private String returnInStockFlag = "0";

}

package com.Laibin.SugarInventory.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "出库结果")
@Data
public class OutVO {
    @Schema(description = "需补充的库存数量（板）")
    Integer remainingQuantity;
    @Schema(description = "详细信息")
    String message;
}

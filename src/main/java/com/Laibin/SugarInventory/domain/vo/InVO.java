package com.Laibin.SugarInventory.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "入库结果VO")
@Data
public class InVO {
    @Schema(description = "多余的产品数量（板）")
    Integer remainingQuantity;
    @Schema(description = "详细信息")
    String message;
}

package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
/**
 * 成品任务使用的单条半成品明细，语义与 in_stock_item 对应。
 */
public class TaskSemiItemDTO {
    @NotBlank
    @Schema(description = "半成品托盘码")
    private String semiPalletCode;

    @NotNull
    @Schema(description = "数量")
    private Integer quantity;

    @NotBlank
    @Schema(description = "单位：0板，1件")
    private String unit;

    @Schema(description = "是否套用化验数据")
    private Boolean useAssay = Boolean.FALSE;
}

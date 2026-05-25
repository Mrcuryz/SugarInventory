package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
/**
 * 历史成品任务登记的半成品用量。
 */
public class TaskSemiItemDTO {
    @Schema(description = "旧版半成品托盘码，已停用，仅保留兼容")
    private String semiPalletCode;

    @Schema(description = "半成品历史余额ID")
    private Long prepareBalanceId;

    @Schema(description = "板数")
    private Integer boardCount;

    @Schema(description = "件数")
    private Integer pieceCount;

    @Schema(description = "旧版数量，已停用")
    private Integer quantity;

    @Schema(description = "旧版单位：0板，1件，已停用")
    private String unit;

    @Schema(description = "旧版是否套用化验数据，已停用")
    private Boolean useAssay = Boolean.FALSE;
}

package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "创建调拨任务明细")
public class CreateTransferTaskItemDTO {
    @NotBlank
    @Schema(description = "托盘码")
    private String code;

    @NotBlank
    @Schema(description = "目标仓库名称")
    private String targetWarehouseName;

    @Schema(description = "兼容字段，后端固定为左", hidden = true)
    private String targetSide;

    @Schema(description = "备注，可选")
    private String remark;
}

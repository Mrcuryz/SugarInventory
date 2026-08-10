package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "成品入库精确执行预览项")
public class FinishInboundExecutionPreviewItemDTO {
    @NotBlank
    @Size(max = 100)
    @Schema(description = "托盘码")
    private String code;

    @NotBlank
    @Size(max = 100)
    @Schema(description = "目标库位名称")
    private String warehouseName;

    @Schema(description = "入库日期；为空时沿用现有任务/托盘生产日期规则")
    private LocalDate entryDate;

    @Size(max = 1)
    @Schema(description = "存放侧：左或右")
    private String side = "左";

    @Min(1)
    @Schema(description = "本次入库数量")
    private Integer quantity = 1;

    @Size(max = 1)
    @Schema(description = "单位：0=板，1=件")
    private String unit = "0";

    @Size(max = 255)
    @Schema(description = "备注")
    private String remark;
}

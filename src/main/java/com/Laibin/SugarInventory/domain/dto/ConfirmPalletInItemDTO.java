package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "单个托盘入库确认项")
public class ConfirmPalletInItemDTO {
    @NotBlank
    @Schema(description = "托盘码，例如 BT0A3ZK")
    private String code;

    @NotBlank
    @Schema(description = "入库仓库名称，对应 warehouse.warehouse_name")
    private String warehouseName;

    @Schema(description = "入库日期，默认使用任务的生产日期")
    private LocalDate entryDate;

    @Schema(description = "优先存放侧，左/右，默认左")
    private String side = "左";

    @Schema(description = "本次入库数量（板/件），不传默认1")
    private Integer quantity = 1;

    @Schema(description = "单位：0=板，1=件，不传默认0")
    private String unit = "0";

    @Schema(description = "备注，可选")
    private String remark;
}

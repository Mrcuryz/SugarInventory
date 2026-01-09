package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
/**
 * 入库任务绑定请求：将产品信息绑定到托盘并生成入库任务（不处理化验）。
 */
public class BindPalletTaskDTO {
    @NotBlank
    @Schema(description = "扫码得到的托盘码，例如 BT0A3ZK")
    private String code;

    @NotNull
    @Schema(description = "绑定的产品ID")
    private Integer productId;

    @NotBlank
    @Schema(description = "产品状态：半成品/成品")
    private String productStatus;

    @NotNull
    @Schema(description = "生产日期")
    private LocalDate productionDate;

    @Schema(description = "数量，默认为1板；件数需单独输入", example = "20")
    private Integer quantity;

    @NotNull(message = "单位不能为空")
    @Schema(description = "单位0板1件（板/件）", example = "0")
    private String unit;

    @Schema(description = "备注，可选")
    private String remark;
}

package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
/**
 * 为成品入库任务绑定半成品明细（全量覆盖）。
 */
public class BindTaskSemiItemsDTO {
    @NotBlank
    @Schema(description = "成品托盘码")
    private String code;

    @NotEmpty
    @Valid
    @Schema(description = "半成品明细列表（全量覆盖）")
    private List<TaskSemiItemDTO> items;
}

package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
/**
 * 为成品入库任务登记备料池半成品用量（全量覆盖）。
 */
public class BindTaskSemiItemsDTO {
    @NotBlank
    @Schema(description = "成品托盘码")
    private String code;

    @NotEmpty
    @Valid
    @Schema(description = "备料池半成品用量明细列表（全量覆盖）")
    private List<TaskSemiItemDTO> items;
}

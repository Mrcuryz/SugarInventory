package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "确认历史生产占用请求")
public class ConfirmSemiPrepareBatchDTO {
    @NotEmpty
    @Valid
    @Schema(description = "半成品托盘码列表")
    private List<String> codes;

    @Schema(description = "备注，可选")
    private String remark;
}

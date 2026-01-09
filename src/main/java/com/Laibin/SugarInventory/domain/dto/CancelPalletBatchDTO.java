package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "托盘批量作废/取消请求")
public class CancelPalletBatchDTO {
    @NotEmpty
    @Valid
    @Schema(description = "托盘码列表")
    private List<String> codes;

    @Schema(description = "备注，可选")
    private String remark;
}

package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "托盘入库确认批量请求")
public class ConfirmPalletInBatchDTO {
    @NotEmpty
    @Size(max = 20, message = "单次最多处理20个托盘")
    @Valid
    @Schema(description = "待确认入库的托盘列表")
    private List<ConfirmPalletInItemDTO> items;
}

package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "托盘二维码导出请求")
public class PalletQrExportDTO {

    @NotEmpty(message = "托盘码列表不能为空")
    @Valid
    @Schema(description = "托盘码列表")
    private List<String> codes;
}

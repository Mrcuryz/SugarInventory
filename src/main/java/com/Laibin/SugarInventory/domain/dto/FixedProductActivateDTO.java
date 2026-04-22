package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "固定产品二维码打印并启用请求")
public class FixedProductActivateDTO {

    @NotEmpty(message = "二维码列表不能为空")
    @Valid
    @Schema(description = "待打印并启用的二维码列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> codes;

    @NotNull(message = "生产日期不能为空")
    @Schema(description = "生产日期", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate productionDate;
}

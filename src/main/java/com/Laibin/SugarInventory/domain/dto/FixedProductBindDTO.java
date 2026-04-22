package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "固定产品二维码批量绑定请求")
public class FixedProductBindDTO {

    @NotNull(message = "产品不能为空")
    @Schema(description = "产品ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer productId;

    @NotNull(message = "绑定数量不能为空")
    @Min(value = 1, message = "绑定数量必须大于 0")
    @Max(value = 1000, message = "单次最多绑定 1000 个二维码")
    @Schema(description = "需要绑定的二维码数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer num;
}

package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "入库记录批量更新请求DTO，仅允许修改数量")
public class InStockUpdateDTO {

    @NotNull(message = "记录ID不能为空")
    @Schema(description = "入库记录ID", example = "1")
    private Integer id;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于0")
    @Schema(description = "更新后的数量", example = "100")
    private Integer quantity;
}
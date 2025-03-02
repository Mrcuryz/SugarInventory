package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "化验记录提交DTO，用于导入或更新化验记录")
public class AssaySubmitDTO {
    @NotNull(message = "产品ID不能为空")
    @Schema(description = "产品ID", example = "1")
    private Integer productId;

    @NotNull(message = "采样日期不能为空")
    @Schema(description = "采样日期", example = "2025-02-24")
    private LocalDate sampleDate;

    @DecimalMin(value = "0.00", message = "色值不能小于0")
    @DecimalMax(value = "500.00", message = "色值不能大于500")
    @Schema(description = "色值", example = "75.5")
    private BigDecimal colorValue;

    @DecimalMin(value = "0.00", message = "还原糖不能小于0")
    @DecimalMax(value = "50.00", message = "还原糖不能大于50")
    private BigDecimal reducingSugar;

    @DecimalMin(value = "0.00", message = "pH值不能小于0")
    @DecimalMax(value = "14.00", message = "pH值不能大于14")
    @Schema(description = "pH值", example = "6.2")
    private BigDecimal phValue;
}
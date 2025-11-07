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

    @Schema(description = "产品ID", example = "1")
    private Integer productId;

    @Schema(description = "选择类型：1产品默认，2添加的验收标准", example = "0")
    private Integer selectType;


    @Schema(description = "验收标准id", example = "0")
    private Integer relatedId;

    @NotNull(message = "采样日期不能为空")
    @Schema(description = "采样日期", example = "2025-02-24")
    private LocalDate sampleDate;

    @DecimalMin(value = "0.00", message = "色值不能小于0")
    @Schema(description = "色值", example = "75.5")
    private BigDecimal colorValue;

    @Schema(description = "还原糖分", example = "1.0")
    @DecimalMin(value = "0.00", message = "还原糖分不能小于0")
    private BigDecimal reducingSugar;

    @Schema(description = "干燥失重", example = "1.0")
    @DecimalMin(value = "0.00", message = "干燥失重不能小于0")
    private BigDecimal dryWeight;

    @Schema(description = "电导灰分", example = "0.05")
    @DecimalMin(value = "0.00", message = "电导灰分不能小于0")
    private BigDecimal conductivityAsh;

    @Schema(description = "蔗糖分", example = "98.0")
    @DecimalMin(value = "0.00", message = "蔗糖分不能小于0")
    private BigDecimal sucrose;

    @Schema(description = "不溶于水杂质", example = "50")
    private BigDecimal insolubleImpurity;

    @DecimalMin(value = "0.00", message = "pH值不能小于0")
    @DecimalMax(value = "14.00", message = "pH值不能大于14")
    @Schema(description = "pH值", example = "6.2")
    private BigDecimal phValue;
}

package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "新增半成品记录请求DTO")
public class AddSemiProductRecordDTO extends BaseDTO {
    @NotBlank(message = "产品名称不能为空")
    @Schema(description = "产品名称", example = "正中冰")
    private String productName;

    @Min(value = 1, message = "数量必须大于0")
    @Schema(description = "数量（件）", example = "100")
    private Integer quantity;

    @Override
    public Integer getId() {
        return null;
    }
}
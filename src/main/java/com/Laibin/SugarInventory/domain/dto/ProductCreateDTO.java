package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "产品创建请求DTO，用于创建新产品")
public class ProductCreateDTO extends BaseDTO {
    @NotBlank(message = "产品名称不能为空")
    @Schema(description = "产品名称", example = "中冰")
    private String productName;

    @NotNull(message = "产品类型不能为空")
    @Schema(description = "产品类型", example = "白冰糖")
    private String type;

    @NotNull(message = "产品状态不能为空")
    @Schema(description = "产品状态（如半成品、成品）", example = "半成品")
    private String status;

    @Schema(description = "产品包装方式（如袋、箱、罐）", example = "箱")
    private String packaging;

    @DecimalMin(value = "0.1", message = "重量必须大于0")
    @Schema(description = "每件产品重量（kg）", example = "40")
    private BigDecimal weightPerPiece;

    @Override
    public Integer getId() {
        return null;
    }
}
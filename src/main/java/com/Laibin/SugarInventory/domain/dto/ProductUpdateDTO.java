package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "产品更新请求DTO，用于更新产品信息")
public class ProductUpdateDTO extends BaseDTO {
    @Schema(description = "产品ID", example = "1")
    private Integer productId;

    @Schema(description = "产品名称", example = "中冰")
    private String productName;

    @Schema(description = "产品类型", example = "白冰糖")
    private String productType;

    @Schema(description = "产品状态", example = "成品")
    private String status;

    @Schema(description = "产品包装方式", example = "箱")
    private String packagingMethod;

    @Schema(description = "每件产品重量（kg）", example = "1.5")
    private BigDecimal weightPerPiece;

    @Schema(description = "每板数量", example = "25")
    private Integer piecesPerPallet;

    @Schema(description = "是否可堆叠", example = "false")
    private Boolean canStack;

    @Override
    public Integer getId() {
        return productId;
    }
}
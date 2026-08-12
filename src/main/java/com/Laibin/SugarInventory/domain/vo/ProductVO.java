package com.Laibin.SugarInventory.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "产品详情视图对象，包含产品的详细信息")
public class ProductVO extends BaseVO {
    @Schema(description = "产品ID", example = "1")
    private Integer id;

    @Schema(description = "产品名称", example = "中冰")
    private String productName;

    @Schema(description = "产品类型", example = "白冰糖")
    private String type;

    @Schema(description = "产品类型", example = "白冰糖")
    private String productType;

    private String status;
    private String packagingMethod;
    private BigDecimal weightPerPiece;
    private Integer piecesPerPallet;
    private Boolean canStack;
    private Integer screenMeshId;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private Integer updatedBy;
    private LocalDateTime updatedAt;
    private String defaultStandardName;
}

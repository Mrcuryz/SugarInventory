package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;
import java.math.BigDecimal;
import java.util.List;

@Value @Builder
public class ProductDetailAgentVO {
    String dataScope; String productName; String productType; String productStatus; String packagingMethod;
    BigDecimal weightPerPiece; Integer piecesPerPallet; Boolean canStack; String screenMeshName;
    String conversionSummary; List<String> limitations;
}

package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;
import java.math.BigDecimal;
import java.util.List;

@Value @Builder
public class ProductCatalogAgentVO {
    String dataScope; long total; int page; int size; List<Row> records; List<String> limitations;
    @Value @Builder
    public static class Row {
        String productName; String productType; String productStatus; String packagingMethod;
        BigDecimal weightPerPiece; Integer piecesPerPallet; Boolean canStack; String screenMeshName;
    }
}

package com.Laibin.SugarInventory.production.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductionQuantitySplitVO {
    private Integer finalBoardCount;
    private Integer finalPieces;
    private Integer totalPieces;
    private Integer requiredQrCount;
}

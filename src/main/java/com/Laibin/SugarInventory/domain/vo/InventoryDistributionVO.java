package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class InventoryDistributionVO {
    String scopeLabel;
    String productLabel;
    String groupBy;
    long rawFullPallets;
    long rawLoosePieces;
    Long normalizedPallets;
    Long normalizedLoosePieces;
    long totalEquivalentPieces;
    String totalStockText;
    String totalWeightText;
    long warehouseCount;
    long productCount;
    long palletCount;
    String calculationNote;
    List<InventoryDistributionGroupVO> groups;
    List<String> notes;
}

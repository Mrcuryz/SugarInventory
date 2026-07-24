package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class InventoryQualityVO {
    String queryType;
    String queryLabel;
    long totalGroups;
    long totalEquivalentPieces;
    String totalWeightText;
    boolean truncated;
    List<InventoryQualityRecordVO> records;
    List<String> notes;
}

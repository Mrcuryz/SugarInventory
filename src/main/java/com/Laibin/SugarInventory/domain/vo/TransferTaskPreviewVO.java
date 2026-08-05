package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 待确认调拨任务的只读资格校验结果。
 */
@Value
@Builder
public class TransferTaskPreviewVO {
    List<Item> items;
    List<String> blockingIssues;

    @Value
    @Builder
    public static class Item {
        String palletCode;
        String currentWarehouseName;
        String currentSide;
        Integer currentRowNumber;
        Integer currentLayer;
        Integer currentInventoryQuantity;
        String currentInventoryUnit;
        String targetWarehouseName;
        String targetSide;
        Integer plannedTargetRowNumber;
        Integer plannedTargetLayer;
    }
}

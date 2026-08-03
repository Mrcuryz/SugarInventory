package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Data;

@Data
public class ProductionFlowOutputQualityRowVO {
    private Integer draftOutputExcludedCount;
    private Integer canceledOutputExcludedCount;
    private Integer crossDayInboundCount;
    private Integer unattributedOrderCount;
}

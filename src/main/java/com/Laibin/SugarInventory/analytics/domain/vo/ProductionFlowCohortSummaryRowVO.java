package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductionFlowCohortSummaryRowVO {
    private Integer cohortOrderCount;
    private Integer completedOrderCount;
    private Integer completedOrdersWithInputCount;
    private Integer completedOrdersMissingInputCount;
    private Integer completedOrdersWithStableOutputCount;
    private Integer completedOrdersMissingOutputCount;
    private Integer ordersWithInputCount;
    private Integer ordersMissingInputCount;
    private Integer ordersWithStableOutputCount;
    private Integer ordersMissingOutputCount;
    private BigDecimal cohortMaterialInputWeightKg;
    private BigDecimal cohortBoilingInputWeightKg;
    private BigDecimal cohortStableOutputWeightKg;
}

package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProductionDailyReportGroupVO {
    private String groupCode;
    private String groupName;
    private boolean productGroup;
    private List<String> allowedProductStatuses = new ArrayList<>();
    private List<ProductionDailyMetricValueVO> metricValues = new ArrayList<>();
    private List<ProductionDailyProductLineVO> productLines = new ArrayList<>();
}

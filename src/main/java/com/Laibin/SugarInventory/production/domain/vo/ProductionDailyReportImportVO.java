package com.Laibin.SugarInventory.production.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProductionDailyReportImportVO {
    private boolean saved;
    private int unmatchedCount;
    private ProductionDailyReportVO report;
}

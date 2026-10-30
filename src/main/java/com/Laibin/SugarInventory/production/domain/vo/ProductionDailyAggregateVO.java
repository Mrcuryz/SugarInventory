package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Saved daily values only. Quantities and saved tonnage have independent coverage. */
@Data
public class ProductionDailyAggregateVO {
    private String sourceLabel = "生产日报";
    private int requestedDays;
    private boolean truncated;
    private List<Series> series;

    @Data
    public static class Series extends ProductionDailyReportAgentVO.Overview {
        private String seriesKey;
        private boolean tonnageApplicable;
        private long tonnageObservedDays;
        private List<Point> points;
    }

    @Data
    public static class Point {
        private String seriesKey;
        private LocalDate businessDate;
        private BigDecimal quantity;
        private BigDecimal convertedTons;
        private long recordCount;
        private long quantityRows;
        private long tonnageRows;
        private long draftRows;
    }
}

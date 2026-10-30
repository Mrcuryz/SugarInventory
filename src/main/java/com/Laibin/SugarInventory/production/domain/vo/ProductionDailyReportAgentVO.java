package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class ProductionDailyReportAgentVO {
    private String sourceLabel = "生产日报";
    private String dateRangeLabel;
    private String scopeLabel;
    @com.fasterxml.jackson.annotation.JsonProperty("isEmpty")
    private boolean isEmpty;
    private int page;
    private int size;
    private long total;
    private boolean hasMore;
    private List<Report> reports;
    private List<Row> records;
    private List<String> limitations;
    private List<Overview> overview;
    private boolean overviewTruncated;
    private int requestedDays;

    /** Full-filter aggregates, independent of the detail page. Never includes cumulative columns. */
    @Data
    public static class Overview {
        private String summaryType;
        private String departmentName;
        private String label;
        private String unit;
        private BigDecimal quantity;
        private BigDecimal convertedTons;
        private long recordCount;
        private long quantityRows;
        private long tonnageRows;
        private long observedDays;
        private long draftRows;
        private String coverageLabel;
    }

    @Data
    public static class Report {
        private LocalDate reportDate;
        private String statusLabel;
    }

    @Data
    public static class Row {
        private LocalDate reportDate;
        private String departmentName;
        private String sectionStatusLabel;
        private String groupName;
        private String itemName;
        private String itemKindLabel;
        private String unit;
        private BigDecimal dailyActual;
        private BigDecimal convertedTons;
        private BigDecimal monthQuantity;
        private BigDecimal monthTons;
        private BigDecimal yearTons;
    }
}

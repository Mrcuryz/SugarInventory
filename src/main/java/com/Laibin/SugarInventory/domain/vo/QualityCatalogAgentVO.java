package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class QualityCatalogAgentVO {
    private QualityCatalogAgentVO() { }
    @Value @Builder public static class AssayGroups { String dataScope; long total; int page; int size; List<AssayGroupRow> records; List<String> limitations; }
    @Value @Builder public static class AssayGroupRow { String groupName; List<String> productNames; LocalDate createdAt; LocalDate updatedAt; String remark; }
    @Value @Builder public static class Standards { String dataScope; long total; int page; int size; List<StandardRow> records; List<String> limitations; }
    @Value @Builder public static class StandardRow { String standardCode; String standardName; String productType; String standardLevel; Integer version; String status; int metricCount; int relatedProductCount; String remark; }
    @Value @Builder public static class StandardDetail { String dataScope; String standardCode; String standardName; String productType; String standardLevel; Integer version; String status; String remark; List<Metric> metrics; List<String> relatedProductNames; List<String> limitations; }
    @Value @Builder public static class Metric { String metricCode; String metricName; BigDecimal minValue; BigDecimal maxValue; String unit; String compareType; Integer sortOrder; String remark; }
    @Value @Builder public static class ProductRelations { String dataScope; String productName; int count; List<Relation> records; List<String> limitations; }
    @Value @Builder public static class Relation { String standardCode; String standardName; Integer standardVersion; String standardStatus; Boolean isDefault; Integer priority; Boolean enabled; LocalDateTime effectiveFrom; LocalDateTime effectiveTo; String remark; }
}

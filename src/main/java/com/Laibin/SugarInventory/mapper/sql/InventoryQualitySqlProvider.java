package com.Laibin.SugarInventory.mapper.sql;

import com.Laibin.SugarInventory.domain.dto.InventoryDistributionQueryDTO;
import com.Laibin.SugarInventory.domain.dto.InventoryQualityQueryDTO;

import java.util.Map;

public class InventoryQualitySqlProvider {
    private static final String QUANTITY_COLUMNS = """
            COALESCE(SUM(CASE WHEN COALESCE(i.pieces, 0) > 0 THEN 0 ELSE COALESCE(i.quantity, 0) END), 0) AS rawFullPallets,
            COALESCE(SUM(i.pieces), 0) AS rawLoosePieces,
            COALESCE(SUM(CASE WHEN COALESCE(i.pieces, 0) > 0
                THEN COALESCE(i.pieces, 0)
                ELSE COALESCE(i.quantity, 0) * p.pieces_per_pallet END), 0) AS totalEquivalentPieces,
            COALESCE(SUM((CASE WHEN COALESCE(i.pieces, 0) > 0
                THEN COALESCE(i.pieces, 0)
                ELSE COALESCE(i.quantity, 0) * p.pieces_per_pallet END) * p.weight_per_piece), 0) AS totalWeight,
            COUNT(DISTINCT i.pallet_code_id) AS palletCount
            """;

    public String selectRecords(Map<String, Object> params) {
        InventoryQualityQueryDTO query = query(params);
        String metric = metricExpression(query);
        String select = """
                SELECT a.id AS assayId,
                       i.product_id AS productId,
                       p.product_name AS productName,
                       p.packaging_method AS packagingMethod,
                       p.weight_per_piece AS weightPerPiece,
                       p.pieces_per_pallet AS piecesPerPallet,
                       %s AS productionDate,
                       w.warehouse_name AS warehouseName,
                """.formatted(InventoryCurrentAssayFactSql.BATCH_PRODUCTION_DATE);
        return InventoryCurrentAssayFactSql.CTE + select + QUANTITY_COLUMNS + ","
                + " a.judge_result AS judgeResult, a.applied_standard_name AS appliedStandardName,"
                + " a.applied_standard_version AS appliedStandardVersion,"
                + " a.failed_metric_count AS failedMetricCount, a.failed_metrics_json AS failedMetricsJson,"
                + metric + " AS metricValue "
                + InventoryCurrentAssayFactSql.INVENTORY_FROM
                + where(query)
                + groupBy(metric)
                + " ORDER BY " + InventoryCurrentAssayFactSql.BATCH_PRODUCTION_DATE
                + " DESC, totalEquivalentPieces DESC, p.product_name ASC, w.warehouse_name ASC"
                + " LIMIT #{query.limit}";
    }

    public String selectAggregate(Map<String, Object> params) {
        InventoryQualityQueryDTO query = query(params);
        String inner = "SELECT i.product_id, " + InventoryCurrentAssayFactSql.BATCH_PRODUCTION_DATE
                + " AS productionDate, i.warehouse_id, "
                + "COALESCE(SUM(CASE WHEN COALESCE(i.pieces, 0) > 0 THEN COALESCE(i.pieces, 0) "
                + "ELSE COALESCE(i.quantity, 0) * p.pieces_per_pallet END), 0) AS totalEquivalentPieces, "
                + "COALESCE(SUM((CASE WHEN COALESCE(i.pieces, 0) > 0 THEN COALESCE(i.pieces, 0) "
                + "ELSE COALESCE(i.quantity, 0) * p.pieces_per_pallet END) * p.weight_per_piece), 0) AS totalWeight "
                + InventoryCurrentAssayFactSql.INVENTORY_FROM + where(query)
                + " GROUP BY i.product_id, " + InventoryCurrentAssayFactSql.BATCH_PRODUCTION_DATE
                + ", i.warehouse_id";
        return InventoryCurrentAssayFactSql.CTE
                + "SELECT COUNT(*) AS totalGroups, COALESCE(SUM(matched.totalEquivalentPieces), 0) AS totalEquivalentPieces, "
                + "COALESCE(SUM(matched.totalWeight), 0) AS totalWeight FROM (" + inner + ") matched";
    }

    private String where(InventoryQualityQueryDTO query) {
        StringBuilder sql = new StringBuilder(" WHERE 1=1");
        appendProductScope(sql, query.getProductScope());
        if ("SINGLE_WAREHOUSE".equals(query.getWarehouseScope().getType())) {
            sql.append(" AND i.warehouse_id = #{query.warehouseScope.warehouseId}");
        }
        switch (query.getMode()) {
            case "JUDGE_STATUS" -> appendJudgeStatus(sql, query.getJudgeStatus());
            case "STANDARD" -> appendStandardMatch(sql);
            case "METRIC" -> appendMetricCondition(sql, query);
            default -> throw new IllegalArgumentException("unsupported inventory quality mode");
        }
        return sql.toString();
    }

    private void appendProductScope(StringBuilder sql, InventoryDistributionQueryDTO.ProductScope scope) {
        switch (scope.getType()) {
            case "SINGLE_PRODUCT" -> sql.append(" AND i.product_id = #{query.productScope.productId}");
            case "EXACT_PRODUCT_NAME_GROUP" -> sql.append(" AND (p.product_name = #{query.productScope.productName}")
                    .append(" OR p.product_name LIKE CONCAT(#{query.productScope.productName}, '（%')")
                    .append(" OR p.product_name LIKE CONCAT(#{query.productScope.productName}, '(%'))");
            case "PRODUCT_TYPE_GROUP" -> sql.append(" AND p.product_type = #{query.productScope.productType}");
            default -> { }
        }
    }

    private void appendJudgeStatus(StringBuilder sql, String status) {
        if ("MISSING_ASSAY".equals(status)) {
            sql.append(" AND a.id IS NULL");
        } else {
            sql.append(" AND a.judge_result = #{query.judgeStatus}");
        }
    }

    private void appendStandardMatch(StringBuilder sql) {
        String actual = standardMetricExpression();
        sql.append(" AND a.id IS NOT NULL")
                .append(" AND p.product_type = #{query.resolvedStandardProductType}")
                .append(" AND EXISTS (SELECT 1 FROM quality_standard_item present")
                .append(" WHERE present.quality_standard_id = #{query.resolvedStandardId})")
                .append(" AND NOT EXISTS (SELECT 1 FROM quality_standard_item qsi")
                .append(" WHERE qsi.quality_standard_id = #{query.resolvedStandardId}")
                .append(" AND ((").append(actual).append(") IS NULL")
                .append(" OR (qsi.min_value IS NOT NULL AND (").append(actual).append(") < qsi.min_value)")
                .append(" OR (qsi.max_value IS NOT NULL AND (").append(actual).append(") > qsi.max_value)))");
    }

    private void appendMetricCondition(StringBuilder sql, InventoryQualityQueryDTO query) {
        String actual = metricExpression(query);
        sql.append(" AND a.id IS NOT NULL AND ").append(actual).append(" IS NOT NULL AND ");
        switch (query.getMetricCondition().getOperator()) {
            case "GT" -> sql.append(actual).append(" > #{query.metricCondition.value}");
            case "GTE" -> sql.append(actual).append(" >= #{query.metricCondition.value}");
            case "LT" -> sql.append(actual).append(" < #{query.metricCondition.value}");
            case "LTE" -> sql.append(actual).append(" <= #{query.metricCondition.value}");
            case "EQ" -> sql.append(actual).append(" = #{query.metricCondition.value}");
            case "BETWEEN" -> sql.append(actual).append(" BETWEEN #{query.metricCondition.minValue} AND #{query.metricCondition.maxValue}");
            default -> throw new IllegalArgumentException("unsupported metric operator");
        }
    }

    private String groupBy(String metric) {
        return " GROUP BY a.id, i.product_id, p.product_name, p.packaging_method, p.weight_per_piece,"
                + " p.pieces_per_pallet, " + InventoryCurrentAssayFactSql.BATCH_PRODUCTION_DATE
                + ", i.warehouse_id, w.warehouse_name, a.judge_result,"
                + " a.applied_standard_name, a.applied_standard_version, a.failed_metric_count,"
                + " a.failed_metrics_json, " + metric;
    }

    private String metricExpression(InventoryQualityQueryDTO query) {
        if (!"METRIC".equals(query.getMode()) || query.getMetricCondition() == null) {
            return "NULL";
        }
        return switch (query.getMetricCondition().getMetricCode()) {
            case "color_value" -> "a.color_value";
            case "reducing_sugar" -> "a.reducing_sugar";
            case "dry_weight_loss" -> "a.dry_weight";
            case "conductivity_ash" -> "a.conductivity_ash";
            case "sucrose" -> "a.sucrose";
            case "insoluble_impurity" -> "a.insoluble_impurity";
            case "ph" -> "a.ph_value";
            default -> throw new IllegalArgumentException("unsupported assay metric");
        };
    }

    private String standardMetricExpression() {
        return """
                CASE qsi.metric_code
                    WHEN 'color_value' THEN a.color_value
                    WHEN 'reducing_sugar' THEN a.reducing_sugar
                    WHEN 'dry_weight_loss' THEN a.dry_weight
                    WHEN 'dry_weight' THEN a.dry_weight
                    WHEN 'conductivity_ash' THEN a.conductivity_ash
                    WHEN 'sucrose' THEN a.sucrose
                    WHEN 'insoluble_impurity' THEN a.insoluble_impurity
                    WHEN 'ph' THEN a.ph_value
                    WHEN 'ph_value' THEN a.ph_value
                    ELSE NULL
                END
                """;
    }

    private InventoryQualityQueryDTO query(Map<String, Object> params) {
        return (InventoryQualityQueryDTO) params.get("query");
    }
}

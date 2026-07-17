package com.Laibin.SugarInventory.mapper.sql;

import com.Laibin.SugarInventory.domain.dto.AssayAbnormalitiesQueryDTO;

import java.util.Map;

public class AssayAbnormalitiesSqlProvider {
    private static final String FROM = """
            FROM assay a
            INNER JOIN product p ON p.id = a.product_id
            """;

    public String selectSummary(Map<String, Object> params) {
        AssayAbnormalitiesQueryDTO query = query(params);
        return "<script>" + """
                SELECT
                    COUNT(*) AS total,
                    SUM(CASE WHEN a.judge_result = 'FAIL' THEN 1 ELSE 0 END) AS failedCount,
                    SUM(CASE WHEN a.judge_result = 'NO_STANDARD' THEN 1 ELSE 0 END) AS noStandardCount,
                    SUM(CASE WHEN a.judge_result = 'MULTIPLE_CANDIDATES' THEN 1 ELSE 0 END) AS multipleCandidatesCount,
                    MAX(a.sample_date) AS latestSampleDate
                """ + FROM + where(query) + "</script>";
    }

    public String selectGroups(Map<String, Object> params) {
        AssayAbnormalitiesQueryDTO query = query(params);
        String selectGroup = switch (query.getGroupBy()) {
            case "date" -> "DATE_FORMAT(a.sample_date, '%Y-%m-%d') AS groupKey, DATE_FORMAT(a.sample_date, '%Y-%m-%d') AS groupLabel, NULL AS productName, NULL AS packagingMethod, NULL AS weightPerPiece, NULL AS piecesPerPallet";
            case "abnormal_type" -> "a.judge_result AS groupKey, a.judge_result AS groupLabel, NULL AS productName, NULL AS packagingMethod, NULL AS weightPerPiece, NULL AS piecesPerPallet";
            case "metric" -> "COALESCE(JSON_UNQUOTE(JSON_EXTRACT(metric_item.metric_json, '$.metricName')), '未命名指标') AS groupKey, COALESCE(JSON_UNQUOTE(JSON_EXTRACT(metric_item.metric_json, '$.metricName')), '未命名指标') AS groupLabel, NULL AS productName, NULL AS packagingMethod, NULL AS weightPerPiece, NULL AS piecesPerPallet";
            default -> "CAST(a.product_id AS CHAR) AS groupKey, p.product_name AS groupLabel, p.product_name AS productName, p.packaging_method AS packagingMethod, p.weight_per_piece AS weightPerPiece, p.pieces_per_pallet AS piecesPerPallet";
        };
        String groupBy = switch (query.getGroupBy()) {
            case "date" -> " GROUP BY a.sample_date";
            case "abnormal_type" -> " GROUP BY a.judge_result";
            case "metric" -> " GROUP BY groupKey, groupLabel";
            default -> " GROUP BY a.product_id, p.product_name, p.packaging_method, p.weight_per_piece, p.pieces_per_pallet";
        };
        String metricJoin = "metric".equals(query.getGroupBy())
                ? " JOIN JSON_TABLE(CASE WHEN JSON_VALID(a.failed_metrics_json) THEN a.failed_metrics_json ELSE '[]' END, '$[*]' COLUMNS(metric_json JSON PATH '$')) metric_item"
                : "";
        String metricWhere = "metric".equals(query.getGroupBy()) ? " AND a.judge_result = 'FAIL'" : "";
        return "<script>" + """
                SELECT
                    %s,
                    COUNT(*) AS total,
                    SUM(CASE WHEN a.judge_result = 'FAIL' THEN 1 ELSE 0 END) AS failedCount,
                    SUM(CASE WHEN a.judge_result = 'NO_STANDARD' THEN 1 ELSE 0 END) AS noStandardCount,
                    SUM(CASE WHEN a.judge_result = 'MULTIPLE_CANDIDATES' THEN 1 ELSE 0 END) AS multipleCandidatesCount,
                    MAX(a.sample_date) AS latestSampleDate
                """.formatted(selectGroup)
                + FROM + metricJoin + where(query) + metricWhere
                + groupBy
                + " ORDER BY total DESC, latestSampleDate DESC, groupLabel ASC"
                + " LIMIT #{query.limit}"
                + "</script>";
    }

    private String where(AssayAbnormalitiesQueryDTO query) {
        StringBuilder sql = new StringBuilder(" WHERE 1=1");
        if (query.getProductScope() != null) {
            switch (query.getProductScope().getType()) {
                case "SINGLE_PRODUCT" -> sql.append(" AND a.product_id = #{query.productScope.productId}");
                case "EXACT_PRODUCT_NAME_GROUP" -> sql.append(" AND (p.product_name = #{query.productScope.productName}")
                        .append(" OR p.product_name LIKE CONCAT(#{query.productScope.productName}, '（%')")
                        .append(" OR p.product_name LIKE CONCAT(#{query.productScope.productName}, '(%'))");
                case "PRODUCT_TYPE_GROUP" -> sql.append(" AND p.product_type = #{query.productScope.productType}");
                default -> { }
            }
        }
        if (query.getResolvedFrom() != null) {
            sql.append(" AND a.sample_date >= #{query.resolvedFrom}");
        }
        if (query.getResolvedTo() != null) {
            // The provider result is parsed as a MyBatis XML script, so '<' must be escaped.
            sql.append(" AND a.sample_date &lt;= #{query.resolvedTo}");
        }
        sql.append(" AND a.judge_result IN ");
        sql.append("<foreach collection='query.resolvedJudgeResults' item='judge' open='(' separator=',' close=')'>#{judge}</foreach>");
        return sql.toString();
    }

    private AssayAbnormalitiesQueryDTO query(Map<String, Object> params) {
        return (AssayAbnormalitiesQueryDTO) params.get("query");
    }
}

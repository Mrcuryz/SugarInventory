package com.Laibin.SugarInventory.mapper.sql;

import com.Laibin.SugarInventory.domain.dto.AssayRecordsQueryDTO;

import java.util.Map;

public class AssayRecordsSqlProvider {
    private static final String FROM = """
            FROM assay a
            INNER JOIN product p ON p.id = a.product_id
            LEFT JOIN user u ON u.id = a.tested_by
            """;

    public String selectSummary(Map<String, Object> params) {
        AssayRecordsQueryDTO query = query(params);
        return """
                SELECT
                    COUNT(*) AS total,
                    SUM(CASE WHEN a.judge_result = 'PASS' THEN 1 ELSE 0 END) AS passCount,
                    SUM(CASE WHEN a.judge_result = 'FAIL' THEN 1 ELSE 0 END) AS failedCount,
                    SUM(CASE WHEN a.judge_result = 'NO_STANDARD' THEN 1 ELSE 0 END) AS noStandardCount,
                    SUM(CASE WHEN a.judge_result = 'MULTIPLE_CANDIDATES' THEN 1 ELSE 0 END) AS multipleCandidatesCount,
                    MAX(a.sample_date) AS latestSampleDate
                """ + FROM + where(query);
    }

    public String selectRecords(Map<String, Object> params) {
        AssayRecordsQueryDTO query = query(params);
        String orderColumn = "createdAt".equals(query.getSortBy()) ? "a.created_at" : "a.sample_date";
        String direction = "ASC".equals(query.getSortDirection()) ? "ASC" : "DESC";
        return """
                SELECT
                    a.id,
                    a.product_id AS productId,
                    p.product_name AS productName,
                    p.product_type AS productType,
                    p.packaging_method AS packagingMethod,
                    p.weight_per_piece AS weightPerPiece,
                    p.pieces_per_pallet AS piecesPerPallet,
                    a.sample_date AS sampleDate,
                    a.created_at AS createdAt,
                    a.judge_result AS judgeResult,
                    a.is_qualified AS isQualified,
                    a.judge_message AS judgeMessage,
                    a.failed_metric_count AS failedMetricCount,
                    a.failed_metrics_json AS failedMetricsJson,
                    a.applied_standard_name AS appliedStandardName,
                    a.applied_standard_version AS appliedStandardVersion,
                    u.name AS testerName
                """ + FROM + where(query)
                + " ORDER BY " + orderColumn + " " + direction
                + ", a.created_at " + direction + ", a.id " + direction
                + " LIMIT #{offset}, #{size}";
    }

    private String where(AssayRecordsQueryDTO query) {
        StringBuilder sql = new StringBuilder(" WHERE 1=1");
        AssayRecordsQueryDTO.ProductScope productScope = query.getProductScope();
        switch (productScope.getType()) {
            case "SINGLE_PRODUCT" -> sql.append(" AND a.product_id = #{query.productScope.productId}");
            case "EXACT_PRODUCT_NAME_GROUP" -> sql.append(" AND (p.product_name = #{query.productScope.productName}")
                    .append(" OR p.product_name LIKE CONCAT(#{query.productScope.productName}, '（%')")
                    .append(" OR p.product_name LIKE CONCAT(#{query.productScope.productName}, '(%'))");
            case "PRODUCT_TYPE_GROUP" -> sql.append(" AND p.product_type = #{query.productScope.productType}");
            default -> { }
        }
        if (query.getResolvedFrom() != null) {
            sql.append(" AND a.sample_date >= #{query.resolvedFrom}");
        }
        if (query.getResolvedTo() != null) {
            sql.append(" AND a.sample_date <= #{query.resolvedTo}");
        }
        if (query.getResolvedJudgeResult() != null) {
            sql.append(" AND a.judge_result = #{query.resolvedJudgeResult}");
        }
        return sql.toString();
    }

    private AssayRecordsQueryDTO query(Map<String, Object> params) {
        return (AssayRecordsQueryDTO) params.get("query");
    }
}

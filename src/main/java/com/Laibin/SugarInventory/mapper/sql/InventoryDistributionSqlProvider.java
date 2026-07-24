package com.Laibin.SugarInventory.mapper.sql;

import com.Laibin.SugarInventory.domain.dto.InventoryDistributionQueryDTO;

import java.util.List;
import java.util.Map;

public class InventoryDistributionSqlProvider {
    private static final String METRICS = """
            COALESCE(SUM(CASE WHEN COALESCE(i.pieces, 0) > 0 THEN 0 ELSE COALESCE(i.quantity, 0) END), 0) AS rawFullPallets,
            COALESCE(SUM(i.pieces), 0) AS rawLoosePieces,
            COALESCE(SUM(CASE WHEN COALESCE(i.pieces, 0) > 0
                THEN COALESCE(i.pieces, 0)
                ELSE COALESCE(i.quantity, 0) * p.pieces_per_pallet END), 0) AS totalEquivalentPieces,
            COALESCE(SUM((CASE WHEN COALESCE(i.pieces, 0) > 0
                THEN COALESCE(i.pieces, 0)
                ELSE COALESCE(i.quantity, 0) * p.pieces_per_pallet END) * p.weight_per_piece), 0) AS totalWeight,
            COUNT(DISTINCT i.warehouse_id) AS warehouseCount,
            COUNT(DISTINCT i.product_id) AS productCount,
            COUNT(DISTINCT i.pallet_code_id) AS palletCount,
            SUM(CASE WHEN a.id IS NULL THEN 1 ELSE 0 END) AS missingAssayCount,
            SUM(CASE WHEN a.judge_result = 'FAIL' THEN 1 ELSE 0 END) AS failedAssayCount,
            SUM(CASE WHEN a.judge_result = 'NO_STANDARD' THEN 1 ELSE 0 END) AS noStandardAssayCount,
            SUM(CASE WHEN pc.id IS NOT NULL AND pc.status <> 'INSTOCK' THEN 1 ELSE 0 END) AS abnormalPalletCount,
            MIN(p.pieces_per_pallet) AS minPiecesPerPallet,
            MAX(p.pieces_per_pallet) AS maxPiecesPerPallet
            """;
    private static final String FROM = InventoryCurrentAssayFactSql.INVENTORY_FROM;

    public String selectAggregate(Map<String, Object> params) {
        InventoryDistributionQueryDTO query = query(params);
        return InventoryCurrentAssayFactSql.CTE + "SELECT " + METRICS + FROM + where(query);
    }

    public String selectGroups(Map<String, Object> params) {
        InventoryDistributionQueryDTO query = query(params);
        String groupBy = query.getGroupBy();
        String dimensions = switch (groupBy) {
            case "product" -> "NULL AS warehouseName, p.product_name AS productName, p.packaging_method AS packagingMethod, p.weight_per_piece AS weightPerPiece, p.pieces_per_pallet AS piecesPerPallet,";
            case "warehouse_product" -> "w.warehouse_name AS warehouseName, p.product_name AS productName, p.packaging_method AS packagingMethod, p.weight_per_piece AS weightPerPiece, p.pieces_per_pallet AS piecesPerPallet,";
            default -> "w.warehouse_name AS warehouseName, NULL AS productName, NULL AS packagingMethod, NULL AS weightPerPiece, NULL AS piecesPerPallet,";
        };
        String grouping = switch (groupBy) {
            case "product" -> " GROUP BY i.product_id, p.product_name, p.packaging_method, p.weight_per_piece, p.pieces_per_pallet";
            case "warehouse_product" -> " GROUP BY i.warehouse_id, w.warehouse_name, i.product_id, p.product_name, p.packaging_method, p.weight_per_piece, p.pieces_per_pallet";
            default -> " GROUP BY i.warehouse_id, w.warehouse_name";
        };
        return InventoryCurrentAssayFactSql.CTE
                + "SELECT " + dimensions + METRICS + ", MAX(i.entry_date) AS latestInboundTime "
                + FROM + where(query) + grouping
                + " ORDER BY totalEquivalentPieces DESC, warehouseName ASC, productName ASC LIMIT #{query.limit}";
    }

    private String where(InventoryDistributionQueryDTO query) {
        StringBuilder sql = new StringBuilder(" WHERE 1=1");
        InventoryDistributionQueryDTO.ProductScope productScope = query.getProductScope();
        switch (productScope.getType()) {
            case "SINGLE_PRODUCT" -> sql.append(" AND i.product_id = #{query.productScope.productId}");
            case "EXACT_PRODUCT_NAME_GROUP" -> sql.append(" AND (p.product_name = #{query.productScope.productName}")
                    .append(" OR p.product_name LIKE CONCAT(#{query.productScope.productName}, '（%')")
                    .append(" OR p.product_name LIKE CONCAT(#{query.productScope.productName}, '(%'))");
            case "PRODUCT_TYPE_GROUP" -> sql.append(" AND p.product_type = #{query.productScope.productType}");
            default -> { }
        }
        if ("SINGLE_WAREHOUSE".equals(query.getWarehouseScope().getType())) {
            sql.append(" AND i.warehouse_id = #{query.warehouseScope.warehouseId}");
        }
        InventoryDistributionQueryDTO.StatusFilter filter = query.getStatusFilter();
        if (filter == null) {
            return sql.toString();
        }
        appendIn(sql, "i.product_status", "productStatuses", filter.getProductStatuses());
        appendIn(sql, "w.status", "warehouseStatuses", filter.getWarehouseStatuses());
        appendIn(sql, "pc.status", "palletStatuses", filter.getPalletStatuses());
        if ("HAS_ASSAY".equals(filter.getAssayStatus())) {
            sql.append(" AND a.id IS NOT NULL");
        } else if ("MISSING_ASSAY".equals(filter.getAssayStatus())) {
            sql.append(" AND a.id IS NULL");
        } else if (filter.getAssayStatus() != null) {
            sql.append(" AND a.judge_result = #{query.statusFilter.assayStatus}");
        }
        String dateColumn = dateFilterColumn(filter);
        if (filter.getEntryDateFrom() != null) {
            sql.append(" AND ").append(dateColumn).append(" >= #{query.statusFilter.entryDateFrom}");
        }
        if (filter.getEntryDateTo() != null) {
            sql.append(" AND ").append(dateColumn).append(" <= #{query.statusFilter.entryDateTo}");
        }
        return sql.toString();
    }

    private String dateFilterColumn(InventoryDistributionQueryDTO.StatusFilter filter) {
        if (filter == null || filter.getAssayStatus() == null || "MISSING_ASSAY".equals(filter.getAssayStatus())) {
            return "i.entry_date";
        }
        return "a.sample_date";
    }

    private void appendIn(StringBuilder sql, String column, String property, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        sql.append(" AND ").append(column).append(" IN (");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                sql.append(',');
            }
            sql.append("#{query.statusFilter.").append(property).append('[').append(index).append("]}");
        }
        sql.append(')');
    }

    private InventoryDistributionQueryDTO query(Map<String, Object> params) {
        return (InventoryDistributionQueryDTO) params.get("query");
    }
}

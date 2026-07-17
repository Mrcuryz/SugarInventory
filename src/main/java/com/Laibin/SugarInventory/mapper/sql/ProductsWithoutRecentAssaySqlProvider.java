package com.Laibin.SugarInventory.mapper.sql;

import com.Laibin.SugarInventory.domain.dto.AssayRecordsQueryDTO;
import com.Laibin.SugarInventory.domain.dto.ProductsWithoutRecentAssayQueryDTO;

import java.util.Map;

public class ProductsWithoutRecentAssaySqlProvider {
    private static final String METRICS = """
            COALESCE(SUM(CASE WHEN COALESCE(i.pieces, 0) > 0 THEN 0 ELSE COALESCE(i.quantity, 0) END), 0) AS rawFullPallets,
            COALESCE(SUM(i.pieces), 0) AS rawLoosePieces,
            COALESCE(SUM(CASE WHEN COALESCE(i.pieces, 0) > 0
                THEN COALESCE(i.pieces, 0)
                ELSE COALESCE(i.quantity, 0) * p.pieces_per_pallet END), 0) AS totalEquivalentPieces,
            COALESCE(SUM((CASE WHEN COALESCE(i.pieces, 0) > 0
                THEN COALESCE(i.pieces, 0)
                ELSE COALESCE(i.quantity, 0) * p.pieces_per_pallet END) * p.weight_per_piece), 0) AS totalWeight,
            COUNT(DISTINCT i.id) AS inventoryRecordCount,
            COUNT(DISTINCT i.pallet_code_id) AS palletCount,
            COUNT(DISTINCT i.warehouse_id) AS warehouseCount,
            COUNT(DISTINCT i.product_id) AS productCount,
            MIN(p.pieces_per_pallet) AS minPiecesPerPallet,
            MAX(p.pieces_per_pallet) AS maxPiecesPerPallet
            """;
    private static final String FROM = """
            FROM inventory i
            INNER JOIN product p ON p.id = i.product_id
            INNER JOIN warehouse w ON w.id = i.warehouse_id
            LEFT JOIN (
                SELECT product_id, MAX(sample_date) AS latestSampleDate
                FROM assay
                WHERE sample_date >= #{query.resolvedFrom}
                  AND sample_date <= #{query.resolvedTo}
                GROUP BY product_id
            ) recent_assay ON recent_assay.product_id = i.product_id
            """;

    public String selectAggregate(Map<String, Object> params) {
        ProductsWithoutRecentAssayQueryDTO query = query(params);
        return "SELECT " + METRICS + FROM + where(query);
    }

    public String selectGroups(Map<String, Object> params) {
        ProductsWithoutRecentAssayQueryDTO query = query(params);
        String groupBy = query.getGroupBy();
        String dimensions = switch (groupBy) {
            case "warehouse" -> "w.warehouse_name AS warehouseName, NULL AS productName, NULL AS packagingMethod, NULL AS weightPerPiece, NULL AS piecesPerPallet, ";
            case "product_warehouse" -> "w.warehouse_name AS warehouseName, p.product_name AS productName, p.packaging_method AS packagingMethod, p.weight_per_piece AS weightPerPiece, p.pieces_per_pallet AS piecesPerPallet, ";
            default -> "NULL AS warehouseName, p.product_name AS productName, p.packaging_method AS packagingMethod, p.weight_per_piece AS weightPerPiece, p.pieces_per_pallet AS piecesPerPallet, ";
        };
        String grouping = switch (groupBy) {
            case "warehouse" -> " GROUP BY i.warehouse_id, w.warehouse_name";
            case "product_warehouse" -> " GROUP BY i.warehouse_id, w.warehouse_name, i.product_id, p.product_name, p.packaging_method, p.weight_per_piece, p.pieces_per_pallet";
            default -> " GROUP BY i.product_id, p.product_name, p.packaging_method, p.weight_per_piece, p.pieces_per_pallet";
        };
        String warehouseNames = "GROUP_CONCAT(DISTINCT w.warehouse_name ORDER BY w.warehouse_name SEPARATOR '、') AS warehouseNames";
        return "SELECT " + dimensions + METRICS + ", MAX(i.entry_date) AS latestInboundTime, " + warehouseNames + " "
                + FROM + where(query) + grouping
                + " ORDER BY totalEquivalentPieces DESC, productName ASC, warehouseName ASC LIMIT #{query.limit}";
    }

    private String where(ProductsWithoutRecentAssayQueryDTO query) {
        StringBuilder sql = new StringBuilder(" WHERE recent_assay.product_id IS NULL");
        AssayRecordsQueryDTO.ProductScope productScope = query.getProductScope();
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
        return sql.toString();
    }

    private ProductsWithoutRecentAssayQueryDTO query(Map<String, Object> params) {
        return (ProductsWithoutRecentAssayQueryDTO) params.get("query");
    }
}

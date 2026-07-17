package com.Laibin.SugarInventory.mapper.sql;

import com.Laibin.SugarInventory.domain.dto.AssayRecordsQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssayStandardCoverageQueryDTO;

import java.util.Map;

public class AssayStandardCoverageSqlProvider {
    public String selectProductWithoutStandardGroups(Map<String, Object> params) {
        AssayStandardCoverageQueryDTO query = query(params);
        return """
                SELECT inv.productId,
                       inv.productName,
                       inv.packagingMethod,
                       inv.weightPerPiece,
                       inv.piecesPerPallet,
                       inv.rawFullPallets,
                       inv.rawLoosePieces,
                       inv.totalEquivalentPieces,
                       inv.totalWeight,
                       inv.inventoryRecordCount,
                       inv.palletCount,
                       inv.warehouseCount,
                       inv.minPiecesPerPallet,
                       inv.maxPiecesPerPallet,
                       inv.latestInboundTime,
                       inv.warehouseNames,
                       COUNT(DISTINCT r.id) AS relationCount,
                       COUNT(DISTINCT CASE WHEN r.enabled = 1 THEN r.id END) AS enabledRelationCount,
                       COUNT(DISTINCT CASE WHEN r.enabled = 1
                             AND (r.effective_from IS NULL OR r.effective_from <= #{query.resolvedAt})
                             AND (r.effective_to IS NULL OR r.effective_to >= #{query.resolvedAt})
                             AND (qs.status IS NULL OR qs.status = '' OR qs.status = 'ENABLED')
                           THEN qs.id END) AS activeStandardCount,
                       COUNT(DISTINCT CASE WHEN r.enabled = 1
                             AND (r.effective_from IS NULL OR r.effective_from <= #{query.resolvedAt})
                             AND (r.effective_to IS NULL OR r.effective_to >= #{query.resolvedAt})
                             AND (qs.status IS NULL OR qs.status = '' OR qs.status = 'ENABLED')
                             AND COALESCE(qsi.itemCount, 0) < 7
                           THEN qs.id END) AS incompleteActiveStandardCount
                FROM (
                    SELECT i.product_id AS productId,
                           p.product_name AS productName,
                           p.packaging_method AS packagingMethod,
                           p.weight_per_piece AS weightPerPiece,
                           p.pieces_per_pallet AS piecesPerPallet,
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
                           MIN(p.pieces_per_pallet) AS minPiecesPerPallet,
                           MAX(p.pieces_per_pallet) AS maxPiecesPerPallet,
                           MAX(i.entry_date) AS latestInboundTime,
                           GROUP_CONCAT(DISTINCT w.warehouse_name ORDER BY w.warehouse_name SEPARATOR '、') AS warehouseNames
                    FROM inventory i
                    INNER JOIN product p ON p.id = i.product_id
                    INNER JOIN warehouse w ON w.id = i.warehouse_id
                """ + where(query) + """
                    GROUP BY i.product_id, p.product_name, p.packaging_method, p.weight_per_piece, p.pieces_per_pallet
                ) inv
                LEFT JOIN product_quality_standard_relation r ON r.product_id = inv.productId
                LEFT JOIN quality_standards qs ON qs.id = r.quality_standard_id
                LEFT JOIN (
                    SELECT quality_standard_id, COUNT(DISTINCT metric_code) AS itemCount
                    FROM quality_standard_item
                    GROUP BY quality_standard_id
                ) qsi ON qsi.quality_standard_id = qs.id
                GROUP BY inv.productId, inv.productName, inv.packagingMethod, inv.weightPerPiece, inv.piecesPerPallet,
                         inv.rawFullPallets, inv.rawLoosePieces, inv.totalEquivalentPieces, inv.totalWeight,
                         inv.inventoryRecordCount, inv.palletCount, inv.warehouseCount, inv.minPiecesPerPallet,
                         inv.maxPiecesPerPallet, inv.latestInboundTime, inv.warehouseNames
                HAVING activeStandardCount = 0
                ORDER BY totalEquivalentPieces DESC, productName ASC
                LIMIT #{query.limit}
                """;
    }

    private String where(AssayStandardCoverageQueryDTO query) {
        StringBuilder sql = new StringBuilder(" WHERE 1=1");
        AssayRecordsQueryDTO.ProductScope productScope = query.getProductScope();
        switch (productScope.getType()) {
            case "SINGLE_PRODUCT" -> sql.append(" AND i.product_id = #{query.productScope.productId}");
            case "EXACT_PRODUCT_NAME_GROUP" -> sql.append(" AND (p.product_name = #{query.productScope.productName}")
                    .append(" OR p.product_name LIKE CONCAT(#{query.productScope.productName}, '（%')")
                    .append(" OR p.product_name LIKE CONCAT(#{query.productScope.productName}, '(%'))");
            case "PRODUCT_TYPE_GROUP" -> sql.append(" AND p.product_type = #{query.productScope.productType}");
            default -> { }
        }
        return sql.toString();
    }

    private AssayStandardCoverageQueryDTO query(Map<String, Object> params) {
        return (AssayStandardCoverageQueryDTO) params.get("query");
    }
}

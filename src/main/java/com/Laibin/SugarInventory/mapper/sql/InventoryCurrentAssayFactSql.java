package com.Laibin.SugarInventory.mapper.sql;

/**
 * Shared read model for current inventory and the latest assay version of the
 * same product batch. A business batch is identified by product_id and the
 * production date stored in pallet_code.production_date / assay.sample_date.
 * Legacy non-pallet inventory falls back to inventory.entry_date.
 */
public final class InventoryCurrentAssayFactSql {
    public static final String BATCH_PRODUCTION_DATE = "COALESCE(pc.production_date, i.entry_date)";
    public static final String CTE = """
            WITH latest_assay AS (
                SELECT ranked.*
                FROM (
                    SELECT a.*,
                           ROW_NUMBER() OVER (
                               PARTITION BY a.product_id, a.sample_date
                               ORDER BY a.version DESC, a.created_at DESC, a.id DESC
                           ) AS latest_rank
                    FROM assay a
                ) ranked
                WHERE ranked.latest_rank = 1
            )
            """;

    public static final String INVENTORY_FROM = """
            FROM inventory i
            INNER JOIN product p ON p.id = i.product_id
            INNER JOIN warehouse w ON w.id = i.warehouse_id
            LEFT JOIN pallet_code pc ON pc.id = i.pallet_code_id
            LEFT JOIN latest_assay a
                   ON a.product_id = i.product_id
                  AND a.sample_date = COALESCE(pc.production_date, i.entry_date)
            """;

    private InventoryCurrentAssayFactSql() {
    }
}

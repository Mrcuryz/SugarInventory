package com.Laibin.SugarInventory.inventoryhistory.mapper;

import com.Laibin.SugarInventory.inventoryhistory.domain.InventorySnapshotAggregate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InventorySnapshotSourceMapper {
    @Select("""
            SELECT
                p.id AS product_id,
                p.product_name AS product_name,
                COALESCE(i.product_status, p.status) AS product_status,
                w.id AS warehouse_id,
                w.warehouse_name AS warehouse_name,
                COALESCE(pc.production_date, i.entry_date) AS production_date,
                SUM(CASE WHEN COALESCE(i.pieces, 0) > 0
                         THEN 0 ELSE COALESCE(i.quantity, 1) END) AS board_count,
                SUM(CASE WHEN COALESCE(i.pieces, 0) > 0
                         THEN i.pieces ELSE 0 END) AS loose_piece_count,
                SUM(CASE WHEN COALESCE(i.pieces, 0) > 0
                         THEN i.pieces
                         ELSE COALESCE(i.quantity, 1) * COALESCE(p.pieces_per_pallet, 0)
                    END) AS total_pieces,
                SUM(CASE WHEN COALESCE(i.pieces, 0) > 0
                         THEN i.pieces
                         ELSE COALESCE(i.quantity, 1) * COALESCE(p.pieces_per_pallet, 0)
                    END) * COALESCE(p.weight_per_piece, 0) AS total_weight_kg,
                COUNT(*) AS inventory_record_count,
                COUNT(DISTINCT i.pallet_code_id) AS pallet_count,
                p.pieces_per_pallet AS pieces_per_pallet,
                p.weight_per_piece AS weight_per_piece
            FROM inventory i
            JOIN product p ON p.id = i.product_id
            JOIN warehouse w ON w.id = i.warehouse_id
            LEFT JOIN pallet_code pc ON pc.id = i.pallet_code_id
            GROUP BY
                p.id,
                p.product_name,
                COALESCE(i.product_status, p.status),
                w.id,
                w.warehouse_name,
                COALESCE(pc.production_date, i.entry_date),
                p.pieces_per_pallet,
                p.weight_per_piece
            """)
    List<InventorySnapshotAggregate> listCurrentAggregates();
}

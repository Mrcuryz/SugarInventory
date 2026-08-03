package com.Laibin.SugarInventory.analytics.mapper;

import com.Laibin.SugarInventory.analytics.domain.vo.InventoryReplayBalanceRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.InventoryReplayAnchorCheckRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.InventoryReplayMovementRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.InventoryTrustedTrendPointRowVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface InventoryTrendReportMapper {
    @Select("""
            <script>
            WITH movement AS (
                SELECT i.product_id,
                       CASE WHEN i.unit = '1' THEN i.quantity
                            ELSE i.quantity * COALESCE(p.pieces_per_pallet, 0) END AS piece_delta,
                       COALESCE(i.total_weight, 0) AS weight_delta
                FROM in_stock i
                JOIN product p ON p.id = i.product_id
                <if test='productQuery != null and productQuery != ""'>
                    WHERE p.product_name LIKE CONCAT('%', #{productQuery}, '%')
                </if>
                UNION ALL
                SELECT s.product_id,
                       CASE WHEN s.unit = '1' THEN s.quantity
                            ELSE s.quantity * COALESCE(p.pieces_per_pallet, 0) END AS piece_delta,
                       COALESCE(s.total_weight, 0) AS weight_delta
                FROM semi_product_record s
                JOIN product p ON p.id = s.product_id
                <if test='productQuery != null and productQuery != ""'>
                    WHERE p.product_name LIKE CONCAT('%', #{productQuery}, '%')
                </if>
                UNION ALL
                SELECT o.product_id,
                       -CASE WHEN COALESCE(o.pieces, 0) &gt; 0 THEN o.pieces
                             ELSE o.quantity * COALESCE(p.pieces_per_pallet, 0) END AS piece_delta,
                       -COALESCE(o.total_weight, 0) AS weight_delta
                FROM out_stock o
                JOIN product p ON p.id = o.product_id
                <if test='productQuery != null and productQuery != ""'>
                    WHERE p.product_name LIKE CONCAT('%', #{productQuery}, '%')
                </if>
            ), movement_total AS (
                SELECT product_id,
                       COALESCE(SUM(piece_delta), 0) AS expected_pieces,
                       COALESCE(SUM(weight_delta), 0) AS expected_weight_kg
                FROM movement
                GROUP BY product_id
            ), current_total AS (
                SELECT i.product_id,
                       COALESCE(SUM(CASE WHEN COALESCE(i.pieces, 0) &gt; 0 THEN i.pieces
                                         ELSE i.quantity * COALESCE(p.pieces_per_pallet, 0) END), 0)
                           AS current_pieces,
                       COALESCE(SUM(CASE WHEN COALESCE(i.pieces, 0) &gt; 0 THEN i.pieces
                                         ELSE i.quantity * COALESCE(p.pieces_per_pallet, 0) END
                                    * COALESCE(p.weight_per_piece, 0)), 0) AS current_weight_kg
                FROM inventory i
                JOIN product p ON p.id = i.product_id
                <if test='productQuery != null and productQuery != ""'>
                    WHERE p.product_name LIKE CONCAT('%', #{productQuery}, '%')
                </if>
                GROUP BY i.product_id
            ), product_ids AS (
                SELECT product_id FROM movement_total
                UNION
                SELECT product_id FROM current_total
            )
            SELECT ids.product_id,
                   p.product_name,
                   COALESCE(m.expected_pieces, 0) AS expected_pieces,
                   COALESCE(c.current_pieces, 0) AS current_pieces,
                   COALESCE(m.expected_pieces, 0) - COALESCE(c.current_pieces, 0)
                       AS piece_difference,
                   COALESCE(m.expected_weight_kg, 0) AS expected_weight_kg,
                   COALESCE(c.current_weight_kg, 0) AS current_weight_kg,
                   COALESCE(m.expected_weight_kg, 0) - COALESCE(c.current_weight_kg, 0)
                       AS weight_difference_kg
            FROM product_ids ids
            JOIN product p ON p.id = ids.product_id
            LEFT JOIN movement_total m ON m.product_id = ids.product_id
            LEFT JOIN current_total c ON c.product_id = ids.product_id
            ORDER BY ids.product_id
            </script>
            """)
    List<InventoryReplayAnchorCheckRowVO> listReplayAnchorChecks(
            @Param("productQuery") String productQuery
    );

    @Select("""
            <script>
            SELECT
                i.product_id AS product_id,
                MAX(p.product_name) AS product_name,
                COALESCE(SUM(
                    CASE
                        WHEN COALESCE(i.pieces, 0) &gt; 0 THEN i.pieces
                        ELSE COALESCE(i.quantity, 1) * COALESCE(p.pieces_per_pallet, 0)
                    END
                ), 0) AS total_pieces,
                COALESCE(SUM(
                    CASE
                        WHEN COALESCE(i.pieces, 0) &gt; 0 THEN i.pieces
                        ELSE COALESCE(i.quantity, 1) * COALESCE(p.pieces_per_pallet, 0)
                    END * COALESCE(p.weight_per_piece, 0)
                ), 0) AS total_weight_kg
            FROM inventory i
            JOIN product p ON p.id = i.product_id
            <where>
                <if test='productQuery != null and productQuery != ""'>
                    p.product_name LIKE CONCAT('%', #{productQuery}, '%')
                </if>
            </where>
            GROUP BY i.product_id
            ORDER BY i.product_id
            </script>
            """)
    List<InventoryReplayBalanceRowVO> listCurrentProductBalances(
            @Param("productQuery") String productQuery
    );

    @Select("""
            <script>
            SELECT
                movement.business_date,
                movement.product_id,
                MAX(movement.product_name) AS product_name,
                COALESCE(SUM(movement.inbound_pieces), 0) AS inbound_pieces,
                COALESCE(SUM(movement.inbound_weight_kg), 0) AS inbound_weight_kg,
                COALESCE(SUM(movement.outbound_pieces), 0) AS outbound_pieces,
                COALESCE(SUM(movement.outbound_weight_kg), 0) AS outbound_weight_kg,
                COUNT(*) AS movement_record_count,
                MAX(movement.recorded_at) AS latest_recorded_at
            FROM (
                SELECT
                    DATE(i.created_at) AS business_date,
                    i.product_id,
                    p.product_name,
                    CASE WHEN i.unit = '1'
                        THEN i.quantity
                        ELSE i.quantity * COALESCE(p.pieces_per_pallet, 0)
                    END AS inbound_pieces,
                    COALESCE(i.total_weight, 0) AS inbound_weight_kg,
                    0 AS outbound_pieces,
                    0 AS outbound_weight_kg,
                    i.created_at AS recorded_at
                FROM in_stock i
                JOIN product p ON p.id = i.product_id
                WHERE DATE(i.created_at) &gt;= #{startDate}
                <if test='productQuery != null and productQuery != ""'>
                    AND p.product_name LIKE CONCAT('%', #{productQuery}, '%')
                </if>

                UNION ALL

                SELECT
                    DATE(s.created_at) AS business_date,
                    s.product_id,
                    p.product_name,
                    CASE WHEN s.unit = '1'
                        THEN s.quantity
                        ELSE s.quantity * COALESCE(p.pieces_per_pallet, 0)
                    END AS inbound_pieces,
                    COALESCE(s.total_weight, 0) AS inbound_weight_kg,
                    0 AS outbound_pieces,
                    0 AS outbound_weight_kg,
                    s.created_at AS recorded_at
                FROM semi_product_record s
                JOIN product p ON p.id = s.product_id
                WHERE DATE(s.created_at) &gt;= #{startDate}
                <if test='productQuery != null and productQuery != ""'>
                    AND p.product_name LIKE CONCAT('%', #{productQuery}, '%')
                </if>

                UNION ALL

                SELECT
                    DATE(o.created_at) AS business_date,
                    o.product_id,
                    p.product_name,
                    0 AS inbound_pieces,
                    0 AS inbound_weight_kg,
                    CASE WHEN COALESCE(o.pieces, 0) &gt; 0
                        THEN o.pieces
                        ELSE o.quantity * COALESCE(p.pieces_per_pallet, 0)
                    END AS outbound_pieces,
                    COALESCE(o.total_weight, 0) AS outbound_weight_kg,
                    o.created_at AS recorded_at
                FROM out_stock o
                JOIN product p ON p.id = o.product_id
                WHERE DATE(o.created_at) &gt;= #{startDate}
                <if test='productQuery != null and productQuery != ""'>
                    AND p.product_name LIKE CONCAT('%', #{productQuery}, '%')
                </if>
            ) movement
            GROUP BY movement.business_date, movement.product_id
            ORDER BY movement.business_date DESC, movement.product_id
            </script>
            """)
    List<InventoryReplayMovementRowVO> listReplayMovementsAfter(
            @Param("startDate") LocalDate startDate,
            @Param("productQuery") String productQuery
    );

    @Select("""
            <script>
            SELECT
                s.snapshot_date AS business_date,
                s.product_id,
                MAX(s.product_name_snapshot) AS product_name,
                COALESCE(SUM(s.total_pieces), 0) AS total_pieces,
                COALESCE(SUM(s.total_weight_kg), 0) AS total_weight_kg,
                MAX(r.data_as_of) AS data_as_of
            FROM inventory_daily_snapshot s
            JOIN inventory_snapshot_run r
              ON r.snapshot_run_id = s.snapshot_run_id
             AND r.snapshot_type = 'DAILY_CLOSE'
             AND r.status = 'COMPLETED'
            JOIN (
                SELECT snapshot_date, MAX(revision) AS revision
                FROM inventory_snapshot_run
                WHERE snapshot_type = 'DAILY_CLOSE'
                  AND status = 'COMPLETED'
                  AND snapshot_date BETWEEN #{startDate} AND #{endDate}
                GROUP BY snapshot_date
            ) latest_snapshot
              ON latest_snapshot.snapshot_date = r.snapshot_date
             AND latest_snapshot.revision = r.revision
            JOIN (
                SELECT reconciliation.business_date
                FROM inventory_reconciliation_run reconciliation
                JOIN (
                    SELECT business_date, MAX(revision) AS revision
                    FROM inventory_reconciliation_run
                    WHERE business_date BETWEEN #{startDate} AND #{endDate}
                    GROUP BY business_date
                ) latest_reconciliation
                  ON latest_reconciliation.business_date = reconciliation.business_date
                 AND latest_reconciliation.revision = reconciliation.revision
                WHERE reconciliation.status = 'PASSED'
            ) passed ON passed.business_date = s.snapshot_date
            WHERE s.snapshot_date BETWEEN #{startDate} AND #{endDate}
            <if test='productQuery != null and productQuery != ""'>
                AND s.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
            </if>
            GROUP BY s.snapshot_date, s.product_id
            ORDER BY s.snapshot_date, s.product_id
            </script>
            """)
    List<InventoryTrustedTrendPointRowVO> listTrustedTrendPoints(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("productQuery") String productQuery
    );
}

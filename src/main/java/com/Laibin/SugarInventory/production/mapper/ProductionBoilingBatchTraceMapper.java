package com.Laibin.SugarInventory.production.mapper;

import com.Laibin.SugarInventory.production.domain.vo.ProductionTraceMaterialRowVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionTraceOutputCodeRowVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ProductionBoilingBatchTraceMapper {
    @Select("""
            SELECT
                o.id AS outputId,
                o.production_order_id AS productionOrderId,
                o.order_no AS orderNo,
                po.order_type AS orderType,
                po.status AS orderStatus,
                o.product_name_snapshot AS outputProductName,
                o.product_status AS outputProductStatus,
                o.board_count AS boardCount,
                o.piece_count AS pieceCount,
                o.total_pieces AS outputTotalPieces,
                o.pieces_per_pallet AS piecesPerPallet,
                o.status AS outputStatus,
                o.created_at AS outputCreatedAt,
                c.id AS outputCodeId,
                c.pallet_code_id AS palletCodeId,
                c.pallet_cycle_no AS palletCycleNo,
                c.pallet_code AS palletCode,
                lb.id AS labelBatchId,
                lb.batch_no AS labelBatchNo,
                lb.reserved_count AS labelBatchReservedCount,
                lb.used_count AS labelBatchUsedCount,
                lb.recycled_count AS labelBatchRecycledCount,
                lb.status AS labelBatchStatus,
                lb.created_at AS labelBatchCreatedAt,
                c.quantity AS quantity,
                c.unit AS unit,
                c.pieces AS pieces,
                c.pallet_task_id AS palletTaskId,
                c.inventory_id AS inventoryId,
                c.status AS codeStatus,
                c.created_at AS codeCreatedAt,
                c.inbound_at AS inboundAt,
                COALESCE(i.warehouse_id, fr.to_warehouse_id, t.target_warehouse_id) AS warehouseId,
                COALESCE(wi.warehouse_name, wf.warehouse_name, wt.warehouse_name) AS warehouseName
            FROM production_order_output o
            INNER JOIN production_order po ON po.id = o.production_order_id
            LEFT JOIN production_order_output_code c ON c.output_id = o.id AND c.status != 'CANCELED'
            LEFT JOIN production_order_label_code lc ON lc.id = c.label_code_id
            LEFT JOIN production_order_label_batch lb ON lb.id = lc.batch_id
            LEFT JOIN inventory i ON i.id = c.inventory_id
            LEFT JOIN warehouse wi ON wi.id = i.warehouse_id
            LEFT JOIN pallet_task t ON t.id = c.pallet_task_id
            LEFT JOIN warehouse wt ON wt.id = t.target_warehouse_id
            LEFT JOIN pallet_flow_record fr ON fr.id = (
                SELECT MAX(fr2.id)
                FROM pallet_flow_record fr2
                WHERE fr2.task_id = c.pallet_task_id
                  AND fr2.operation_type IN ('SEMI_INSTOCK', 'FINISH_INSTOCK')
            )
            LEFT JOIN warehouse wf ON wf.id = fr.to_warehouse_id
            WHERE o.production_order_id = #{orderId}
              AND o.status != 'CANCELED'
            ORDER BY o.created_at ASC, o.id ASC, c.id ASC
            """)
    List<ProductionTraceOutputCodeRowVO> listOutputCodeRowsByOrder(@Param("orderId") Long orderId);

    @Select({
            "<script>",
            "SELECT",
            " m.id AS id,",
            " m.production_order_id AS productionOrderId,",
            " m.order_no AS orderNo,",
            " po.order_type AS orderType,",
            " po.status AS orderStatus,",
            " m.pallet_code_id AS palletCodeId,",
            " m.pallet_cycle_no AS palletCycleNo,",
            " m.pallet_code AS palletCode,",
            " m.product_id AS productId,",
            " m.product_name_snapshot AS productName,",
            " m.product_status AS productStatus,",
            " m.warehouse_id AS warehouseId,",
            " m.warehouse_name_snapshot AS warehouseName,",
            " m.quantity AS quantity,",
            " m.unit AS unit,",
            " m.pieces AS pieces,",
            " m.total_pieces AS totalPieces,",
            " m.total_weight AS totalWeight,",
            " m.status AS status,",
            " m.picked_at AS pickedAt",
            "FROM production_order_material m",
            "INNER JOIN production_order po ON po.id = m.production_order_id",
            "WHERE m.status = 'PICKED'",
            "  AND m.pallet_code_id IN",
            "  <foreach collection='palletCodeIds' item='id' open='(' separator=',' close=')'>",
            "    #{id}",
            "  </foreach>",
            "ORDER BY m.picked_at ASC, m.id ASC",
            "</script>"
    })
    List<ProductionTraceMaterialRowVO> listMaterialsByPalletCodeIds(@Param("palletCodeIds") Collection<Integer> palletCodeIds);
}

package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.PalletAnomaliesQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PalletFlowRecordsQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PrintedNotInboundCodesQueryDTO;
import com.Laibin.SugarInventory.domain.dto.QrBatchInboundCompletionQueryDTO;
import com.Laibin.SugarInventory.mapper.model.PalletAnomalyGroupRow;
import com.Laibin.SugarInventory.mapper.model.PalletLifecycleEventRow;
import com.Laibin.SugarInventory.mapper.model.PalletLifecycleSummaryRow;
import com.Laibin.SugarInventory.mapper.model.PalletPrintInfoRow;
import com.Laibin.SugarInventory.mapper.model.PrintedNotInboundGroupRow;
import com.Laibin.SugarInventory.mapper.model.QrBatchInboundCompletionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PalletLifecycleQueryMapper {
    @Select({
            "SELECT pc.id AS palletCodeId, pc.code, pc.status,",
            "p.product_name AS productName, pc.product_status AS productStatus, pc.production_date AS productionDate,",
            "pc.current_cycle_no AS currentCycleNo, w.warehouse_name AS warehouseName,",
            "i.quantity, i.pieces, i.entry_date AS entryDate,",
            "COALESCE(i.assay_id, pc.assay_id) AS assayId, a.sample_date AS assaySampleDate,",
            "a.is_qualified AS assayQualified, a.created_at AS assayCreatedAt",
            "FROM pallet_code pc",
            "LEFT JOIN product p ON p.id = COALESCE(pc.product_id, pc.fixed_product_id)",
            "LEFT JOIN inventory i ON i.pallet_code_id = pc.id",
            "LEFT JOIN warehouse w ON w.id = i.warehouse_id",
            "LEFT JOIN assay a ON a.id = COALESCE(i.assay_id, pc.assay_id)",
            "WHERE pc.code = #{code}",
            "LIMIT 1"
    })
    PalletLifecycleSummaryRow selectLifecycleSummary(@Param("code") String code);

    @Select({
            "<script>",
            "SELECT pc.code AS code, fr.operation_type AS operationType, fr.operation_name AS operationName,",
            "fr.operation_time AS operationTime, p.product_name AS productName,",
            "fw.warehouse_name AS fromWarehouseName, tw.warehouse_name AS toWarehouseName,",
            "u.name AS operatorName, fr.cycle_no AS cycleNo",
            "FROM pallet_flow_record fr",
            "INNER JOIN pallet_code pc ON pc.id = fr.pallet_code_id",
            "LEFT JOIN product p ON p.id = fr.product_id",
            "LEFT JOIN warehouse fw ON fw.id = fr.from_warehouse_id",
            "LEFT JOIN warehouse tw ON tw.id = fr.to_warehouse_id",
            "LEFT JOIN user u ON u.id = fr.operator_id",
            "WHERE fr.pallet_code_id = #{palletCodeId}",
            "ORDER BY fr.operation_time ASC, fr.id ASC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<PalletLifecycleEventRow> selectLifecycleEvents(@Param("palletCodeId") Integer palletCodeId,
                                                        @Param("limit") int limit);

    @Select({
            "SELECT lb.batch_no AS batchNo, lb.order_no AS orderNo, lb.status AS batchStatus,",
            "lc.status AS labelStatus, lb.printed_at AS printedAt, lc.used_at AS usedAt, lc.recycled_at AS recycledAt",
            "FROM production_order_label_code lc",
            "INNER JOIN production_order_label_batch lb ON lb.id = lc.batch_id",
            "WHERE lc.pallet_code_id = #{palletCodeId}",
            "ORDER BY lc.id DESC",
            "LIMIT 1"
    })
    PalletPrintInfoRow selectPrintInfo(@Param("palletCodeId") Integer palletCodeId);

    @Select({
            "<script>",
            "SELECT fr.operation_type AS operationType, fr.operation_name AS operationName,",
            "fr.operation_time AS operationTime, pc.code AS code, p.product_name AS productName,",
            "fw.warehouse_name AS fromWarehouseName, tw.warehouse_name AS toWarehouseName,",
            "u.name AS operatorName, fr.cycle_no AS cycleNo",
            "FROM pallet_flow_record fr",
            "INNER JOIN pallet_code pc ON pc.id = fr.pallet_code_id",
            "LEFT JOIN product p ON p.id = fr.product_id",
            "LEFT JOIN warehouse fw ON fw.id = fr.from_warehouse_id",
            "LEFT JOIN warehouse tw ON tw.id = fr.to_warehouse_id",
            "LEFT JOIN user u ON u.id = fr.operator_id",
            "WHERE 1 = 1",
            "<if test='q.code != null and q.code != \"\"'>AND pc.code = #{q.code}</if>",
            "<if test='q.productScope != null and q.productScope.type == \"SINGLE_PRODUCT\"'>AND COALESCE(fr.product_id, pc.product_id) = #{q.productScope.productId}</if>",
            "<if test='q.productScope != null and q.productScope.type == \"EXACT_PRODUCT_NAME_GROUP\"'>AND p.product_name = #{q.productScope.productName}</if>",
            "<if test='q.productScope != null and q.productScope.type == \"PRODUCT_TYPE_GROUP\"'>AND p.product_type = #{q.productScope.productType}</if>",
            "<if test='q.warehouseId != null'>AND (fr.from_warehouse_id = #{q.warehouseId} OR fr.to_warehouse_id = #{q.warehouseId})</if>",
            "<if test='q.resolvedFrom != null'>AND fr.operation_time &gt;= CONCAT(#{q.resolvedFrom}, ' 00:00:00')</if>",
            "<if test='q.resolvedTo != null'>AND fr.operation_time &lt; DATE_ADD(CONCAT(#{q.resolvedTo}, ' 00:00:00'), INTERVAL 1 DAY)</if>",
            "<if test='q.resolvedEventTypes != null and q.resolvedEventTypes.size() > 0'>",
            "AND fr.operation_type IN",
            "<foreach collection='q.resolvedEventTypes' item='eventType' open='(' separator=',' close=')'>#{eventType}</foreach>",
            "</if>",
            "ORDER BY fr.operation_time DESC, fr.id DESC",
            "LIMIT #{offset}, #{size}",
            "</script>"
    })
    List<PalletLifecycleEventRow> selectFlowRecords(@Param("q") PalletFlowRecordsQueryDTO query,
                                                    @Param("offset") long offset,
                                                    @Param("size") int size);

    @Select({
            "<script>",
            "SELECT COUNT(*) FROM pallet_flow_record fr",
            "INNER JOIN pallet_code pc ON pc.id = fr.pallet_code_id",
            "LEFT JOIN product p ON p.id = fr.product_id",
            "WHERE 1 = 1",
            "<if test='q.code != null and q.code != \"\"'>AND pc.code = #{q.code}</if>",
            "<if test='q.productScope != null and q.productScope.type == \"SINGLE_PRODUCT\"'>AND COALESCE(fr.product_id, pc.product_id) = #{q.productScope.productId}</if>",
            "<if test='q.productScope != null and q.productScope.type == \"EXACT_PRODUCT_NAME_GROUP\"'>AND p.product_name = #{q.productScope.productName}</if>",
            "<if test='q.productScope != null and q.productScope.type == \"PRODUCT_TYPE_GROUP\"'>AND p.product_type = #{q.productScope.productType}</if>",
            "<if test='q.warehouseId != null'>AND (fr.from_warehouse_id = #{q.warehouseId} OR fr.to_warehouse_id = #{q.warehouseId})</if>",
            "<if test='q.resolvedFrom != null'>AND fr.operation_time &gt;= CONCAT(#{q.resolvedFrom}, ' 00:00:00')</if>",
            "<if test='q.resolvedTo != null'>AND fr.operation_time &lt; DATE_ADD(CONCAT(#{q.resolvedTo}, ' 00:00:00'), INTERVAL 1 DAY)</if>",
            "<if test='q.resolvedEventTypes != null and q.resolvedEventTypes.size() > 0'>",
            "AND fr.operation_type IN",
            "<foreach collection='q.resolvedEventTypes' item='eventType' open='(' separator=',' close=')'>#{eventType}</foreach>",
            "</if>",
            "</script>"
    })
    Long countFlowRecords(@Param("q") PalletFlowRecordsQueryDTO query);

    @Select({
            "<script>",
            "SELECT",
            "<choose>",
            "<when test='q.groupBy == \"order\"'>lb.order_no</when>",
            "<when test='q.groupBy == \"product\"'>COALESCE(lc.product_name_snapshot, p.product_name)</when>",
            "<otherwise>lb.batch_no</otherwise>",
            "</choose> AS groupLabel,",
            "COUNT(DISTINCT lc.id) AS printedCount,",
            "COUNT(DISTINCT CASE WHEN oc.inventory_id IS NOT NULL OR oc.inbound_at IS NOT NULL OR oc.status = 'INSTOCK' THEN lc.id END) AS inboundCount,",
            "SUBSTRING_INDEX(GROUP_CONCAT(DISTINCT CASE WHEN oc.inventory_id IS NULL AND oc.inbound_at IS NULL AND (oc.status IS NULL OR oc.status != 'INSTOCK') THEN lc.pallet_code END ORDER BY lc.sequence_no SEPARATOR ','), ',', #{q.limit}) AS notInboundExamples",
            "FROM production_order_label_batch lb",
            "INNER JOIN production_order_label_code lc ON lc.batch_id = lb.id",
            "LEFT JOIN production_order_output_code oc ON oc.label_code_id = lc.id AND oc.status != 'CANCELED'",
            "LEFT JOIN product p ON p.id = lc.product_id",
            "WHERE lb.printed_at IS NOT NULL AND lc.status != 'CANCELED'",
            "<if test='q.productScope != null and q.productScope.type == \"SINGLE_PRODUCT\"'>AND lc.product_id = #{q.productScope.productId}</if>",
            "<if test='q.productScope != null and q.productScope.type == \"EXACT_PRODUCT_NAME_GROUP\"'>AND COALESCE(lc.product_name_snapshot, p.product_name) = #{q.productScope.productName}</if>",
            "<if test='q.productScope != null and q.productScope.type == \"PRODUCT_TYPE_GROUP\"'>AND p.product_type = #{q.productScope.productType}</if>",
            "<if test='q.orderNo != null and q.orderNo != \"\"'>AND lb.order_no = #{q.orderNo}</if>",
            "<if test='q.batchNo != null and q.batchNo != \"\"'>AND lb.batch_no = #{q.batchNo}</if>",
            "<if test='q.resolvedFrom != null'>AND lb.printed_at &gt;= CONCAT(#{q.resolvedFrom}, ' 00:00:00')</if>",
            "<if test='q.resolvedTo != null'>AND lb.printed_at &lt; DATE_ADD(CONCAT(#{q.resolvedTo}, ' 00:00:00'), INTERVAL 1 DAY)</if>",
            "<choose>",
            "<when test='q.groupBy == \"order\"'>GROUP BY lb.order_no</when>",
            "<when test='q.groupBy == \"product\"'>GROUP BY COALESCE(lc.product_name_snapshot, p.product_name)</when>",
            "<otherwise>GROUP BY lb.batch_no</otherwise>",
            "</choose>",
            "ORDER BY (printedCount - inboundCount) DESC, groupLabel ASC",
            "LIMIT #{q.limit}",
            "</script>"
    })
    List<PrintedNotInboundGroupRow> selectPrintedNotInboundGroups(@Param("q") PrintedNotInboundCodesQueryDTO query);

    @Select({
            "<script>",
            "SELECT lb.batch_no AS batchNo, lb.order_no AS orderNo, COUNT(DISTINCT lc.id) AS printedCount,",
            "COUNT(DISTINCT CASE WHEN oc.inventory_id IS NOT NULL OR oc.inbound_at IS NOT NULL OR oc.status = 'INSTOCK' THEN lc.id END) AS inboundCount,",
            "SUBSTRING_INDEX(GROUP_CONCAT(DISTINCT CASE WHEN oc.inventory_id IS NULL AND oc.inbound_at IS NULL AND (oc.status IS NULL OR oc.status != 'INSTOCK') THEN lc.pallet_code END ORDER BY lc.sequence_no SEPARATOR ','), ',', #{q.limit}) AS unfinishedExamples",
            "FROM production_order_label_batch lb",
            "INNER JOIN production_order_label_code lc ON lc.batch_id = lb.id",
            "LEFT JOIN production_order_output_code oc ON oc.label_code_id = lc.id AND oc.status != 'CANCELED'",
            "WHERE lb.printed_at IS NOT NULL AND lc.status != 'CANCELED'",
            "<if test='q.batchNo != null and q.batchNo != \"\"'>AND lb.batch_no = #{q.batchNo}</if>",
            "<if test='q.orderNo != null and q.orderNo != \"\"'>AND lb.order_no = #{q.orderNo}</if>",
            "<if test='q.productId != null'>AND lc.product_id = #{q.productId}</if>",
            "<if test='q.resolvedFrom != null'>AND lb.printed_at &gt;= CONCAT(#{q.resolvedFrom}, ' 00:00:00')</if>",
            "<if test='q.resolvedTo != null'>AND lb.printed_at &lt; DATE_ADD(CONCAT(#{q.resolvedTo}, ' 00:00:00'), INTERVAL 1 DAY)</if>",
            "GROUP BY lb.id, lb.batch_no, lb.order_no",
            "ORDER BY lb.printed_at DESC, lb.id DESC",
            "LIMIT #{q.limit}",
            "</script>"
    })
    List<QrBatchInboundCompletionRow> selectBatchInboundCompletion(@Param("q") QrBatchInboundCompletionQueryDTO query);

    @Select({
            "<script>",
            "SELECT anomalyType, anomalyCount, examples FROM (",
            "SELECT 'STATUS_INVENTORY_MISMATCH' AS anomalyType, COUNT(DISTINCT pc.id) AS anomalyCount,",
            "GROUP_CONCAT(DISTINCT pc.code ORDER BY pc.code SEPARATOR ',') AS examples",
            "FROM pallet_code pc LEFT JOIN inventory i ON i.pallet_code_id = pc.id",
            "LEFT JOIN product p ON p.id = COALESCE(pc.product_id, pc.fixed_product_id)",
            "WHERE ((pc.status = 'INSTOCK' AND i.id IS NULL) OR (pc.status != 'INSTOCK' AND i.id IS NOT NULL))",
            "<if test='q.productScope != null and q.productScope.type == \"SINGLE_PRODUCT\"'>AND COALESCE(pc.product_id, pc.fixed_product_id) = #{q.productScope.productId}</if>",
            "<if test='q.productScope != null and q.productScope.type == \"EXACT_PRODUCT_NAME_GROUP\"'>AND p.product_name = #{q.productScope.productName}</if>",
            "<if test='q.productScope != null and q.productScope.type == \"PRODUCT_TYPE_GROUP\"'>AND p.product_type = #{q.productScope.productType}</if>",
            "<if test='q.warehouseId != null'>AND i.warehouse_id = #{q.warehouseId}</if>",
            "<if test='q.resolvedFrom != null'>AND pc.updated_at &gt;= CONCAT(#{q.resolvedFrom}, ' 00:00:00')</if>",
            "<if test='q.resolvedTo != null'>AND pc.updated_at &lt; DATE_ADD(CONCAT(#{q.resolvedTo}, ' 00:00:00'), INTERVAL 1 DAY)</if>",
            "UNION ALL",
            "SELECT 'DUPLICATE_INBOUND', COUNT(*), GROUP_CONCAT(t.code ORDER BY t.code SEPARATOR ',') FROM (",
            "SELECT pc.id, pc.code FROM pallet_code pc INNER JOIN pallet_flow_record fr ON fr.pallet_code_id = pc.id",
            "LEFT JOIN product p ON p.id = COALESCE(fr.product_id, pc.product_id)",
            "WHERE fr.operation_type IN ('SEMI_INSTOCK', 'FINISH_INSTOCK')",
            "<if test='q.productScope != null and q.productScope.type == \"SINGLE_PRODUCT\"'>AND COALESCE(fr.product_id, pc.product_id) = #{q.productScope.productId}</if>",
            "<if test='q.productScope != null and q.productScope.type == \"EXACT_PRODUCT_NAME_GROUP\"'>AND p.product_name = #{q.productScope.productName}</if>",
            "<if test='q.productScope != null and q.productScope.type == \"PRODUCT_TYPE_GROUP\"'>AND p.product_type = #{q.productScope.productType}</if>",
            "<if test='q.warehouseId != null'>AND (fr.from_warehouse_id = #{q.warehouseId} OR fr.to_warehouse_id = #{q.warehouseId})</if>",
            "<if test='q.resolvedFrom != null'>AND fr.operation_time &gt;= CONCAT(#{q.resolvedFrom}, ' 00:00:00')</if>",
            "<if test='q.resolvedTo != null'>AND fr.operation_time &lt; DATE_ADD(CONCAT(#{q.resolvedTo}, ' 00:00:00'), INTERVAL 1 DAY)</if>",
            "GROUP BY pc.id, pc.code HAVING COUNT(*) &gt; 1",
            ") t",
            "UNION ALL",
            "SELECT 'OUTBOUND_WITHOUT_INBOUND', COUNT(*), GROUP_CONCAT(t.code ORDER BY t.code SEPARATOR ',') FROM (",
            "SELECT DISTINCT pc.id, pc.code FROM pallet_code pc INNER JOIN pallet_flow_record out_fr ON out_fr.pallet_code_id = pc.id",
            "LEFT JOIN product p ON p.id = COALESCE(out_fr.product_id, pc.product_id)",
            "WHERE out_fr.operation_type IN ('OUT', 'PREPARE_CONSUMED', 'ORDER_MATERIAL_PICK')",
            "AND NOT EXISTS (SELECT 1 FROM pallet_flow_record in_fr WHERE in_fr.pallet_code_id = pc.id AND in_fr.operation_type IN ('SEMI_INSTOCK', 'FINISH_INSTOCK'))",
            "<if test='q.productScope != null and q.productScope.type == \"SINGLE_PRODUCT\"'>AND COALESCE(out_fr.product_id, pc.product_id) = #{q.productScope.productId}</if>",
            "<if test='q.productScope != null and q.productScope.type == \"EXACT_PRODUCT_NAME_GROUP\"'>AND p.product_name = #{q.productScope.productName}</if>",
            "<if test='q.productScope != null and q.productScope.type == \"PRODUCT_TYPE_GROUP\"'>AND p.product_type = #{q.productScope.productType}</if>",
            "<if test='q.warehouseId != null'>AND (out_fr.from_warehouse_id = #{q.warehouseId} OR out_fr.to_warehouse_id = #{q.warehouseId})</if>",
            "<if test='q.resolvedFrom != null'>AND out_fr.operation_time &gt;= CONCAT(#{q.resolvedFrom}, ' 00:00:00')</if>",
            "<if test='q.resolvedTo != null'>AND out_fr.operation_time &lt; DATE_ADD(CONCAT(#{q.resolvedTo}, ' 00:00:00'), INTERVAL 1 DAY)</if>",
            ") t",
            "UNION ALL",
            "SELECT 'PRODUCT_BINDING_MISMATCH', COUNT(DISTINCT pc.id), GROUP_CONCAT(DISTINCT pc.code ORDER BY pc.code SEPARATOR ',')",
            "FROM pallet_code pc INNER JOIN inventory i ON i.pallet_code_id = pc.id",
            "LEFT JOIN product p ON p.id = pc.product_id",
            "WHERE pc.product_id IS NOT NULL AND i.product_id != pc.product_id",
            "<if test='q.productScope != null and q.productScope.type == \"SINGLE_PRODUCT\"'>AND pc.product_id = #{q.productScope.productId}</if>",
            "<if test='q.productScope != null and q.productScope.type == \"EXACT_PRODUCT_NAME_GROUP\"'>AND p.product_name = #{q.productScope.productName}</if>",
            "<if test='q.productScope != null and q.productScope.type == \"PRODUCT_TYPE_GROUP\"'>AND p.product_type = #{q.productScope.productType}</if>",
            "<if test='q.warehouseId != null'>AND i.warehouse_id = #{q.warehouseId}</if>",
            "<if test='q.resolvedFrom != null'>AND pc.updated_at &gt;= CONCAT(#{q.resolvedFrom}, ' 00:00:00')</if>",
            "<if test='q.resolvedTo != null'>AND pc.updated_at &lt; DATE_ADD(CONCAT(#{q.resolvedTo}, ' 00:00:00'), INTERVAL 1 DAY)</if>",
            ") anomalies WHERE anomalyCount &gt; 0",
            "</script>"
    })
    List<PalletAnomalyGroupRow> selectAnomalyGroups(@Param("q") PalletAnomaliesQueryDTO query);
}

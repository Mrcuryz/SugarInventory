package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.PalletFlowRecord;
import com.Laibin.SugarInventory.domain.vo.PalletFlowCyclePageVO;
import com.Laibin.SugarInventory.domain.vo.PalletFlowDetailVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PalletFlowRecordMapper extends BaseMapper<PalletFlowRecord> {

    @Select({
            "SELECT",
            " x.cycleNo AS cycleNo,",
            " first_flow.product_id AS productId,",
            " p.product_name AS productName,",
            " first_flow.product_status AS productStatus,",
            " x.startTime AS startTime,",
            " x.endTime AS endTime,",
            " x.flowCount AS flowCount,",
            " CASE WHEN x.cycleNo = pc.current_cycle_no THEN TRUE ELSE FALSE END AS isCurrentCycle,",
            " CASE",
            "   WHEN x.cycleNo < pc.current_cycle_no THEN TRUE",
            "   WHEN x.cycleNo = pc.current_cycle_no AND pc.status IN ('FREE', 'INVALID') THEN TRUE",
            "   ELSE FALSE",
            " END AS isEnded",
            " FROM (",
            "   SELECT",
            "     pallet_code_id,",
            "     cycle_no AS cycleNo,",
            "     MIN(operation_time) AS startTime,",
            "     MAX(operation_time) AS endTime,",
            "     COUNT(*) AS flowCount,",
            "     MIN(id) AS firstFlowId",
            "   FROM pallet_flow_record",
            "   WHERE pallet_code_id = #{palletCodeId}",
            "   GROUP BY pallet_code_id, cycle_no",
            " ) x",
            " INNER JOIN pallet_code pc ON pc.id = x.pallet_code_id",
            " LEFT JOIN pallet_flow_record first_flow ON first_flow.id = x.firstFlowId",
            " LEFT JOIN product p ON p.id = first_flow.product_id",
            " ORDER BY x.cycleNo DESC",
            " LIMIT #{offset}, #{size}"
    })
    List<PalletFlowCyclePageVO> pageFlowCycles(@Param("palletCodeId") Integer palletCodeId,
                                               @Param("offset") long offset,
                                               @Param("size") long size);

    @Select("SELECT COUNT(DISTINCT cycle_no) FROM pallet_flow_record WHERE pallet_code_id = #{palletCodeId}")
    Long countFlowCycles(@Param("palletCodeId") Integer palletCodeId);

    @Select({
            "SELECT",
            " fr.id AS id,",
            " fr.cycle_no AS cycleNo,",
            " fr.task_id AS taskId,",
            " fr.operation_type AS operationType,",
            " fr.operation_name AS operationName,",
            " fr.operation_time AS operationTime,",
            " fr.operator_id AS operatorId,",
            " u.name AS operatorName,",
            " fr.product_id AS productId,",
            " p.product_name AS productName,",
            " fr.product_status AS productStatus,",
            " fr.assay_id AS assayId,",
            " fr.from_warehouse_id AS fromWarehouseId,",
            " fw.warehouse_name AS fromWarehouseName,",
            " fr.from_side AS fromSide,",
            " fr.from_row_number AS fromRowNumber,",
            " fr.from_layer AS fromLayer,",
            " fr.to_warehouse_id AS toWarehouseId,",
            " tw.warehouse_name AS toWarehouseName,",
            " fr.to_side AS toSide,",
            " fr.to_row_number AS toRowNumber,",
            " fr.to_layer AS toLayer,",
            " fr.remark AS remark,",
            " fr.ext_data AS extData",
            " FROM pallet_flow_record fr",
            " LEFT JOIN user u ON u.id = fr.operator_id",
            " LEFT JOIN product p ON p.id = fr.product_id",
            " LEFT JOIN warehouse fw ON fw.id = fr.from_warehouse_id",
            " LEFT JOIN warehouse tw ON tw.id = fr.to_warehouse_id",
            " WHERE fr.pallet_code_id = #{palletCodeId}",
            "   AND fr.cycle_no = #{cycleNo}",
            " ORDER BY fr.operation_time ASC, fr.id ASC"
    })
    List<PalletFlowDetailVO> listFlowDetails(@Param("palletCodeId") Integer palletCodeId,
                                             @Param("cycleNo") Integer cycleNo);

    @Select({
            "SELECT COUNT(*)",
            " FROM pallet_flow_record fr",
            " INNER JOIN pallet_code pc ON pc.id = fr.pallet_code_id",
            " WHERE fr.operation_time < #{cutoff}",
            "   AND fr.cycle_no <> pc.current_cycle_no"
    })
    int countExpiredCleanableFlows(@Param("cutoff") LocalDateTime cutoff);

    @Delete({
            "DELETE fr",
            " FROM pallet_flow_record fr",
            " INNER JOIN pallet_code pc ON pc.id = fr.pallet_code_id",
            " WHERE fr.operation_time < #{cutoff}",
            "   AND fr.cycle_no <> pc.current_cycle_no"
    })
    int deleteExpiredCleanableFlows(@Param("cutoff") LocalDateTime cutoff);
}


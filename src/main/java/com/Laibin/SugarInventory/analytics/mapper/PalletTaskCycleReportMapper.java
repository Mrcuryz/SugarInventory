package com.Laibin.SugarInventory.analytics.mapper;

import com.Laibin.SugarInventory.analytics.domain.vo.PalletTaskCycleFactRowVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PalletTaskCycleReportMapper {
    @Select("""
            <script>
            SELECT t.id AS taskId,
                   pc.code AS palletCode,
                   t.task_type AS taskType,
                   t.status,
                   p.product_name AS productName,
                   t.product_status AS productStatus,
                   w.warehouse_name AS targetWarehouseName,
                   t.operation_batch_no AS operationBatchNo,
                   t.created_at AS createdAt,
                   t.confirmed_at AS confirmedAt,
                   EXISTS(SELECT 1 FROM pallet_flow_record fr WHERE fr.task_id = t.id) AS hasFlowRecord
            FROM pallet_task t
            LEFT JOIN pallet_code pc ON pc.id = t.pallet_code_id
            LEFT JOIN product p ON p.id = t.product_id
            LEFT JOIN warehouse w ON w.id = t.target_warehouse_id
            WHERE t.created_at &gt;= #{startDate}
              AND t.created_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)
            <if test="productQuery != null and productQuery != ''">
              AND p.product_name LIKE CONCAT('%', #{productQuery}, '%')
            </if>
            <if test="taskType != null and taskType == 'INBOUND'">
              AND t.task_type IN ('SEMI_IN', 'FINISH_IN')
            </if>
            <if test="taskType != null and taskType != 'INBOUND'">
              AND t.task_type = #{taskType}
            </if>
            ORDER BY t.created_at ASC, t.id ASC
            LIMIT #{limit}
            </script>
            """)
    List<PalletTaskCycleFactRowVO> listTaskFacts(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("productQuery") String productQuery,
            @Param("taskType") String taskType,
            @Param("limit") int limit);
}

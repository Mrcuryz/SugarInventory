package com.Laibin.SugarInventory.analytics.mapper;

import com.Laibin.SugarInventory.analytics.domain.vo.ProductionFlowCohortSummaryRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.ProductionFlowCalendarSummaryRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.ProductionFlowDailyInputRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.ProductionFlowDailyOutputRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.ProductionFlowOrderRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.ProductionFlowOutputQualityRowVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ProductionInputOutputReportMapper {

    @Select("""
            <script>
            SELECT
              (SELECT COUNT(*)
               FROM production_order_material m
               WHERE m.status = 'PICKED'
                 AND m.picked_at &gt;= #{startDate}
                 AND m.picked_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)
               <if test="productQuery != null and productQuery != ''">
                 AND EXISTS (
                   SELECT 1 FROM production_order_output scoped_output
                   WHERE scoped_output.production_order_id = m.production_order_id
                     AND scoped_output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                     AND scoped_output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
                 )
               </if>) AS materialInputRecordCount,
              (SELECT COUNT(DISTINCT m.production_order_id)
               FROM production_order_material m
               WHERE m.status = 'PICKED'
                 AND m.picked_at &gt;= #{startDate}
                 AND m.picked_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)
               <if test="productQuery != null and productQuery != ''">
                 AND EXISTS (
                   SELECT 1 FROM production_order_output scoped_output
                   WHERE scoped_output.production_order_id = m.production_order_id
                     AND scoped_output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                     AND scoped_output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
                 )
               </if>) AS materialInputOrderCount,
              (SELECT COUNT(DISTINCT CASE
                   WHEN m.pallet_code_id IS NULL THEN CONCAT('ROW:', m.id)
                   ELSE CONCAT(m.pallet_code_id, ':', COALESCE(m.pallet_cycle_no, -1))
                 END)
               FROM production_order_material m
               WHERE m.status = 'PICKED'
                 AND m.picked_at &gt;= #{startDate}
                 AND m.picked_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)
               <if test="productQuery != null and productQuery != ''">
                 AND EXISTS (
                   SELECT 1 FROM production_order_output scoped_output
                   WHERE scoped_output.production_order_id = m.production_order_id
                     AND scoped_output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                     AND scoped_output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
                 )
               </if>) AS materialInputPalletCount,
              (SELECT SUM(CASE WHEN m.unit = '0' THEN COALESCE(m.quantity, 0) ELSE 0 END)
               FROM production_order_material m
               WHERE m.status = 'PICKED'
                 AND m.picked_at &gt;= #{startDate}
                 AND m.picked_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)
               <if test="productQuery != null and productQuery != ''">
                 AND EXISTS (
                   SELECT 1 FROM production_order_output scoped_output
                   WHERE scoped_output.production_order_id = m.production_order_id
                     AND scoped_output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                     AND scoped_output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
                 )
               </if>) AS materialInputBoardCount,
              (SELECT SUM(CASE WHEN m.unit = '1' THEN COALESCE(m.pieces, m.quantity, 0) ELSE 0 END)
               FROM production_order_material m
               WHERE m.status = 'PICKED'
                 AND m.picked_at &gt;= #{startDate}
                 AND m.picked_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)
               <if test="productQuery != null and productQuery != ''">
                 AND EXISTS (
                   SELECT 1 FROM production_order_output scoped_output
                   WHERE scoped_output.production_order_id = m.production_order_id
                     AND scoped_output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                     AND scoped_output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
                 )
               </if>) AS materialInputLoosePieceCount,
              (SELECT SUM(COALESCE(m.total_pieces, 0))
               FROM production_order_material m
               WHERE m.status = 'PICKED'
                 AND m.picked_at &gt;= #{startDate}
                 AND m.picked_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)
               <if test="productQuery != null and productQuery != ''">
                 AND EXISTS (
                   SELECT 1 FROM production_order_output scoped_output
                   WHERE scoped_output.production_order_id = m.production_order_id
                     AND scoped_output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                     AND scoped_output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
                 )
               </if>) AS materialInputTotalPieces,
              (SELECT SUM(COALESCE(m.total_weight, 0))
               FROM production_order_material m
               WHERE m.status = 'PICKED'
                 AND m.picked_at &gt;= #{startDate}
                 AND m.picked_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)
               <if test="productQuery != null and productQuery != ''">
                 AND EXISTS (
                   SELECT 1 FROM production_order_output scoped_output
                   WHERE scoped_output.production_order_id = m.production_order_id
                     AND scoped_output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                     AND scoped_output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
                 )
               </if>) AS materialInputWeightKg,
              (SELECT COUNT(*)
               FROM production_order_output output
               WHERE output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                 AND output.production_date BETWEEN #{startDate} AND #{endDate}
               <if test="productQuery != null and productQuery != ''">
                 AND output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
               </if>) AS stableOutputRecordCount,
              (SELECT COUNT(DISTINCT output.production_order_id)
               FROM production_order_output output
               WHERE output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                 AND output.production_date BETWEEN #{startDate} AND #{endDate}
               <if test="productQuery != null and productQuery != ''">
                 AND output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
               </if>) AS stableOutputOrderCount,
              (SELECT SUM(COALESCE(output.board_count, 0))
               FROM production_order_output output
               WHERE output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                 AND output.production_date BETWEEN #{startDate} AND #{endDate}
               <if test="productQuery != null and productQuery != ''">
                 AND output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
               </if>) AS stableOutputBoardCount,
              (SELECT SUM(COALESCE(output.piece_count, 0))
               FROM production_order_output output
               WHERE output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                 AND output.production_date BETWEEN #{startDate} AND #{endDate}
               <if test="productQuery != null and productQuery != ''">
                 AND output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
               </if>) AS stableOutputLoosePieceCount,
              (SELECT SUM(COALESCE(output.total_pieces, 0))
               FROM production_order_output output
               WHERE output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                 AND output.production_date BETWEEN #{startDate} AND #{endDate}
               <if test="productQuery != null and productQuery != ''">
                 AND output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
               </if>) AS stableOutputTotalPieces,
              (SELECT SUM(COALESCE(output.total_weight, 0))
               FROM production_order_output output
               WHERE output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                 AND output.production_date BETWEEN #{startDate} AND #{endDate}
               <if test="productQuery != null and productQuery != ''">
                 AND output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
               </if>) AS stableOutputWeightKg,
              ((SELECT COUNT(*)
                FROM production_order_material m
                WHERE m.status = 'PICKED'
                  AND m.total_weight IS NULL
                  AND m.picked_at &gt;= #{startDate}
                  AND m.picked_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)
                <if test="productQuery != null and productQuery != ''">
                  AND EXISTS (
                    SELECT 1 FROM production_order_output scoped_output
                    WHERE scoped_output.production_order_id = m.production_order_id
                      AND scoped_output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                      AND scoped_output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
                  )
                </if>)
               +
               (SELECT COUNT(*)
                FROM production_order_output output
                WHERE output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                  AND output.total_weight IS NULL
                  AND output.production_date BETWEEN #{startDate} AND #{endDate}
                <if test="productQuery != null and productQuery != ''">
                  AND output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
                </if>)) AS rowsMissingWeight,
              ((SELECT COUNT(*)
                FROM production_order_material m
                WHERE m.status = 'PICKED'
                  AND m.total_pieces IS NULL
                  AND m.picked_at &gt;= #{startDate}
                  AND m.picked_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)
                <if test="productQuery != null and productQuery != ''">
                  AND EXISTS (
                    SELECT 1 FROM production_order_output scoped_output
                    WHERE scoped_output.production_order_id = m.production_order_id
                      AND scoped_output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                      AND scoped_output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
                  )
                </if>)
               +
               (SELECT COUNT(*)
                FROM production_order_output output
                WHERE output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                  AND output.total_pieces IS NULL
                  AND output.production_date BETWEEN #{startDate} AND #{endDate}
                <if test="productQuery != null and productQuery != ''">
                  AND output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
                </if>)) AS rowsMissingPieceConversion,
              GREATEST(
                COALESCE((SELECT MAX(m.picked_at)
                          FROM production_order_material m
                          WHERE m.status = 'PICKED'
                            AND m.picked_at &gt;= #{startDate}
                            AND m.picked_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)), '1970-01-01'),
                COALESCE((SELECT MAX(COALESCE(output.updated_at, output.created_at))
                          FROM production_order_output output
                          WHERE output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                            AND output.production_date BETWEEN #{startDate} AND #{endDate}), '1970-01-01')
              ) AS latestRecordedAt
            </script>
            """)
    ProductionFlowCalendarSummaryRowVO selectCalendarSummary(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("productQuery") String productQuery);

    @Select("""
            <script>
            SELECT DATE(m.picked_at) AS businessDate,
                   COUNT(*) AS inputRecordCount,
                   COUNT(DISTINCT m.production_order_id) AS inputOrderCount,
                   COUNT(DISTINCT CASE
                       WHEN m.pallet_code_id IS NULL THEN CONCAT('ROW:', m.id)
                       ELSE CONCAT(m.pallet_code_id, ':', COALESCE(m.pallet_cycle_no, -1))
                   END) AS inputPalletCount,
                   SUM(CASE WHEN m.unit = '0' THEN COALESCE(m.quantity, 0) ELSE 0 END) AS inputBoardCount,
                   SUM(CASE WHEN m.unit = '1' THEN COALESCE(m.pieces, m.quantity, 0) ELSE 0 END)
                       AS inputLoosePieceCount,
                   SUM(COALESCE(m.total_pieces, 0)) AS inputTotalPieces,
                   SUM(COALESCE(m.total_weight, 0)) AS inputWeightKg,
                   SUM(CASE WHEN m.total_weight IS NULL THEN 1 ELSE 0 END) AS rowsMissingWeight,
                   SUM(CASE WHEN m.total_pieces IS NULL THEN 1 ELSE 0 END) AS rowsMissingPieceConversion,
                   MAX(m.picked_at) AS latestRecordedAt
            FROM production_order_material m
            WHERE m.status = 'PICKED'
              AND m.picked_at &gt;= #{startDate}
              AND m.picked_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)
            <if test="productQuery != null and productQuery != ''">
              AND EXISTS (
                SELECT 1
                FROM production_order_output scoped_output
                WHERE scoped_output.production_order_id = m.production_order_id
                  AND scoped_output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                  AND scoped_output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
              )
            </if>
            GROUP BY DATE(m.picked_at)
            ORDER BY DATE(m.picked_at)
            </script>
            """)
    List<ProductionFlowDailyInputRowVO> listDailyMaterialInputs(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("productQuery") String productQuery);

    @Select("""
            <script>
            SELECT output.production_date AS businessDate,
                   COUNT(*) AS outputRecordCount,
                   COUNT(DISTINCT output.production_order_id) AS outputOrderCount,
                   SUM(COALESCE(output.board_count, 0)) AS outputBoardCount,
                   SUM(COALESCE(output.piece_count, 0)) AS outputLoosePieceCount,
                   SUM(COALESCE(output.total_pieces, 0)) AS outputTotalPieces,
                   SUM(COALESCE(output.total_weight, 0)) AS outputWeightKg,
                   SUM(CASE WHEN output.total_weight IS NULL THEN 1 ELSE 0 END) AS rowsMissingWeight,
                   SUM(CASE WHEN output.total_pieces IS NULL THEN 1 ELSE 0 END)
                       AS rowsMissingPieceConversion,
                   MAX(COALESCE(output.updated_at, output.created_at)) AS latestRecordedAt
            FROM production_order_output output
            WHERE output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
              AND output.production_date BETWEEN #{startDate} AND #{endDate}
            <if test="productQuery != null and productQuery != ''">
              AND output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
            </if>
            GROUP BY output.production_date
            ORDER BY output.production_date
            </script>
            """)
    List<ProductionFlowDailyOutputRowVO> listDailyStableOutputs(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("productQuery") String productQuery);

    @Select("""
            <script>
            SELECT COUNT(*) AS cohortOrderCount,
                   SUM(CASE WHEN scope.order_status = 'COMPLETED' THEN 1 ELSE 0 END)
                       AS completedOrderCount,
                   SUM(CASE WHEN scope.order_status = 'COMPLETED'
                                 AND scope.input_record_count &gt; 0
                            THEN 1 ELSE 0 END) AS completedOrdersWithInputCount,
                   SUM(CASE WHEN scope.order_status = 'COMPLETED'
                                 AND scope.input_record_count = 0
                            THEN 1 ELSE 0 END) AS completedOrdersMissingInputCount,
                   SUM(CASE WHEN scope.order_status = 'COMPLETED'
                                 AND scope.output_record_count &gt; 0
                            THEN 1 ELSE 0 END) AS completedOrdersWithStableOutputCount,
                   SUM(CASE WHEN scope.order_status = 'COMPLETED'
                                 AND scope.output_record_count = 0
                            THEN 1 ELSE 0 END) AS completedOrdersMissingOutputCount,
                   SUM(CASE WHEN scope.input_record_count &gt; 0 THEN 1 ELSE 0 END)
                       AS ordersWithInputCount,
                   SUM(CASE WHEN scope.input_record_count = 0 THEN 1 ELSE 0 END)
                       AS ordersMissingInputCount,
                   SUM(CASE WHEN scope.output_record_count &gt; 0 THEN 1 ELSE 0 END)
                       AS ordersWithStableOutputCount,
                   SUM(CASE WHEN scope.output_record_count = 0 THEN 1 ELSE 0 END)
                       AS ordersMissingOutputCount,
                   SUM(scope.material_weight_kg) AS cohortMaterialInputWeightKg,
                   SUM(scope.boiling_weight_kg) AS cohortBoilingInputWeightKg,
                   SUM(scope.output_weight_kg) AS cohortStableOutputWeightKg
            FROM (
              SELECT o.id,
                     o.status AS order_status,
                     CASE WHEN o.order_type = 'SEMI'
                          THEN COALESCE(usage_fact.usage_count, 0)
                          ELSE COALESCE(material_fact.material_count, 0)
                     END AS input_record_count,
                     COALESCE(output_fact.output_count, 0) AS output_record_count,
                     COALESCE(material_fact.material_weight_kg, 0) AS material_weight_kg,
                     COALESCE(usage_fact.usage_weight_kg, 0) AS boiling_weight_kg,
                     COALESCE(output_fact.output_weight_kg, 0) AS output_weight_kg
              FROM production_order o
              LEFT JOIN (
                SELECT production_order_id,
                       COUNT(*) AS material_count,
                       SUM(COALESCE(total_weight, 0)) AS material_weight_kg
                FROM production_order_material
                WHERE status = 'PICKED'
                GROUP BY production_order_id
              ) material_fact ON material_fact.production_order_id = o.id
              LEFT JOIN (
                SELECT production_order_id,
                       COUNT(*) AS usage_count,
                       SUM(COALESCE(weight_kg, 0)) AS usage_weight_kg
                FROM production_boiling_batch_usage
                WHERE status = 'CONSUMED'
                GROUP BY production_order_id
              ) usage_fact ON usage_fact.production_order_id = o.id
              LEFT JOIN (
                SELECT production_order_id,
                       COUNT(*) AS output_count,
                       SUM(COALESCE(total_weight, 0)) AS output_weight_kg
                FROM production_order_output
                WHERE status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                <if test="productQuery != null and productQuery != ''">
                  AND product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
                </if>
                GROUP BY production_order_id
              ) output_fact ON output_fact.production_order_id = o.id
              WHERE o.production_date BETWEEN #{startDate} AND #{endDate}
              <if test="productQuery != null and productQuery != ''">
                AND COALESCE(output_fact.output_count, 0) &gt; 0
              </if>
            ) scope
            </script>
            """)
    ProductionFlowCohortSummaryRowVO selectCohortSummary(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("productQuery") String productQuery);

    @Select("""
            <script>
            SELECT o.order_no AS orderNo,
                   o.order_type AS orderType,
                   o.status AS orderStatus,
                   o.production_date AS productionDate,
                   o.completed_at AS completedAt,
                   COALESCE(material_fact.material_count, 0) AS materialInputRecordCount,
                   COALESCE(material_fact.pallet_count, 0) AS materialInputPalletCount,
                   COALESCE(material_fact.total_pieces, 0) AS materialInputTotalPieces,
                   COALESCE(material_fact.material_weight_kg, 0) AS materialInputWeightKg,
                   COALESCE(usage_fact.usage_count, 0) AS boilingInputUsageCount,
                   COALESCE(usage_fact.usage_weight_kg, 0) AS boilingInputWeightKg,
                   COALESCE(output_fact.output_count, 0) AS stableOutputRecordCount,
                   COALESCE(output_fact.total_pieces, 0) AS stableOutputTotalPieces,
                   COALESCE(output_fact.output_weight_kg, 0) AS stableOutputWeightKg,
                   output_fact.output_product_names AS outputProductNames,
                   GREATEST(
                       COALESCE(material_fact.latest_at, '1970-01-01 00:00:00'),
                       COALESCE(usage_fact.latest_at, '1970-01-01 00:00:00'),
                       COALESCE(output_fact.latest_at, '1970-01-01 00:00:00'),
                       COALESCE(o.updated_at, o.created_at)
                   ) AS latestRecordedAt
            FROM production_order o
            LEFT JOIN (
              SELECT production_order_id,
                     COUNT(*) AS material_count,
                     COUNT(DISTINCT CASE
                         WHEN pallet_code_id IS NULL THEN CONCAT('ROW:', id)
                         ELSE CONCAT(pallet_code_id, ':', COALESCE(pallet_cycle_no, -1))
                     END) AS pallet_count,
                     SUM(COALESCE(total_pieces, 0)) AS total_pieces,
                     SUM(COALESCE(total_weight, 0)) AS material_weight_kg,
                     MAX(picked_at) AS latest_at
              FROM production_order_material
              WHERE status = 'PICKED'
              GROUP BY production_order_id
            ) material_fact ON material_fact.production_order_id = o.id
            LEFT JOIN (
              SELECT production_order_id,
                     COUNT(*) AS usage_count,
                     SUM(COALESCE(weight_kg, 0)) AS usage_weight_kg,
                     MAX(COALESCE(updated_at, created_at)) AS latest_at
              FROM production_boiling_batch_usage
              WHERE status = 'CONSUMED'
              GROUP BY production_order_id
            ) usage_fact ON usage_fact.production_order_id = o.id
            LEFT JOIN (
              SELECT production_order_id,
                     COUNT(*) AS output_count,
                     SUM(COALESCE(total_pieces, 0)) AS total_pieces,
                     SUM(COALESCE(total_weight, 0)) AS output_weight_kg,
                     GROUP_CONCAT(DISTINCT product_name_snapshot ORDER BY product_name_snapshot
                         SEPARATOR '、') AS output_product_names,
                     MAX(COALESCE(updated_at, created_at)) AS latest_at
              FROM production_order_output
              WHERE status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
              <if test="productQuery != null and productQuery != ''">
                AND product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
              </if>
              GROUP BY production_order_id
            ) output_fact ON output_fact.production_order_id = o.id
            WHERE o.production_date BETWEEN #{startDate} AND #{endDate}
            <if test="productQuery != null and productQuery != ''">
              AND COALESCE(output_fact.output_count, 0) &gt; 0
            </if>
            ORDER BY o.production_date DESC, o.order_no DESC
            LIMIT #{limit}
            </script>
            """)
    List<ProductionFlowOrderRowVO> listCohortOrderBreakdowns(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("productQuery") String productQuery,
            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT
              (SELECT COUNT(*)
               FROM production_order_output draft_output
               WHERE draft_output.status = 'DRAFT'
                 AND draft_output.production_date BETWEEN #{startDate} AND #{endDate}
               <if test="productQuery != null and productQuery != ''">
                 AND draft_output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
               </if>) AS draftOutputExcludedCount,
              (SELECT COUNT(*)
               FROM production_order_output canceled_output
               WHERE canceled_output.status = 'CANCELED'
                 AND canceled_output.production_date BETWEEN #{startDate} AND #{endDate}
               <if test="productQuery != null and productQuery != ''">
                 AND canceled_output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
               </if>) AS canceledOutputExcludedCount,
              (SELECT COUNT(*)
               FROM production_order_output_code code
               JOIN production_order_output stable_output ON stable_output.id = code.output_id
               WHERE code.status = 'INSTOCK'
                 AND code.inbound_at IS NOT NULL
                 AND DATE(code.inbound_at) &lt;&gt; stable_output.production_date
                 AND stable_output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                 AND stable_output.production_date BETWEEN #{startDate} AND #{endDate}
               <if test="productQuery != null and productQuery != ''">
                 AND stable_output.product_name_snapshot LIKE CONCAT('%', #{productQuery}, '%')
               </if>) AS crossDayInboundCount,
              (SELECT COUNT(*)
               FROM production_order unattributed_order
               WHERE unattributed_order.production_date BETWEEN #{startDate} AND #{endDate}
                 AND NOT EXISTS (
                   SELECT 1
                   FROM production_order_output attributed_output
                   WHERE attributed_output.production_order_id = unattributed_order.id
                     AND attributed_output.status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')
                 )) AS unattributedOrderCount
            </script>
            """)
    ProductionFlowOutputQualityRowVO selectOutputQuality(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("productQuery") String productQuery);
}

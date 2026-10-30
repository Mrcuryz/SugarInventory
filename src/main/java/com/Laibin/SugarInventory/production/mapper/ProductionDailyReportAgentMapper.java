package com.Laibin.SugarInventory.production.mapper;

import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportAgentQueryDTO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportAgentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/** Reads persisted values and bounded, full-filter aggregates; never mixes production stages. */
@Mapper
public interface ProductionDailyReportAgentMapper {
    String SOURCE = """
        FROM production_daily_report r
        JOIN production_daily_report_section s ON s.report_id = r.id
        JOIN (
            SELECT id AS row_id, report_section_id, group_name_snapshot AS group_name,
                   metric_name_snapshot AS item_name, '指标' AS item_kind_label, unit_snapshot AS unit,
                   daily_actual, converted_tons, month_quantity, month_tons, year_tons, display_order
            FROM production_daily_metric_value
            UNION ALL
            SELECT id AS row_id, report_section_id, category_name_snapshot AS group_name,
                   product_name_snapshot AS item_name, '产品' AS item_kind_label, unit,
                   daily_actual, converted_tons, month_quantity, month_tons, year_tons, display_order
            FROM production_daily_product_line
        ) v ON v.report_section_id = s.id
        WHERE r.report_date &gt;= #{query.startDate} AND r.report_date &lt;= #{query.endDate}
        <if test="query.departmentName != null">
          AND s.department_name_snapshot = #{query.departmentName}
        </if>
        <if test="query.itemQuery != null">
          AND LOCATE(#{query.itemQuery}, v.item_name) &gt; 0
        </if>
        """;

    @Select("<script>SELECT COUNT(*) " + SOURCE + "</script>")
    long countRows(@Param("query") ProductionDailyReportAgentQueryDTO query);

    @Select("""
        <script>SELECT r.report_date AS reportDate, s.department_name_snapshot AS departmentName,
          CASE s.status WHEN 'SUBMITTED' THEN '已提交' WHEN 'DRAFT' THEN '草稿'
            ELSE '状态待核实' END AS sectionStatusLabel,
          v.group_name AS groupName, v.item_name AS itemName, v.item_kind_label AS itemKindLabel,
          v.unit, v.daily_actual AS dailyActual, v.converted_tons AS convertedTons,
          v.month_quantity AS monthQuantity, v.month_tons AS monthTons, v.year_tons AS yearTons
        """ + SOURCE + """
        ORDER BY r.report_date DESC, s.id, v.display_order, v.item_kind_label, v.row_id
        LIMIT #{offset}, #{query.size}</script>
        """)
    List<ProductionDailyReportAgentVO.Row> pageRows(
            @Param("query") ProductionDailyReportAgentQueryDTO query, @Param("offset") int offset);

    @Select("""
        SELECT report_date AS reportDate,
          CASE status WHEN 'SUBMITTED' THEN '已提交' WHEN 'DRAFT' THEN '草稿'
            ELSE '状态待核实' END AS statusLabel
        FROM production_daily_report
        WHERE report_date >= #{query.startDate} AND report_date <= #{query.endDate}
        ORDER BY report_date DESC
        """)
    List<ProductionDailyReportAgentVO.Report> listReports(@Param("query") ProductionDailyReportAgentQueryDTO query);

}

package com.Laibin.SugarInventory.production.mapper;

import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportAgentQueryDTO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyAggregateVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/** One saved-fact projection shared by overview and trend; never reads order output. */
@Mapper
public interface ProductionDailyAggregateMapper {
    String FILTER = """
        WHERE r.report_date &gt;= #{query.startDate} AND r.report_date &lt;= #{query.endDate}
        <if test="query.departmentName != null">AND s.department_name_snapshot = #{query.departmentName}</if>
        """;
    String FACTS = """
        SELECT SHA2(CAST(JSON_ARRAY('metric',s.department_code,s.department_name_snapshot,
                 v.metric_code,v.metric_name_snapshot,v.unit_snapshot) AS CHAR),256) AS series_key,
          CASE WHEN v.metric_code='FACTORY_TOTAL_OUTPUT' THEN 0
               WHEN v.metric_code='POLY_OUTPUT' THEN 1 ELSE 2 END AS priority,
          CASE WHEN v.metric_code='FACTORY_TOTAL_OUTPUT' THEN '全厂产量'
               WHEN v.metric_code='POLY_OUTPUT' THEN '车间产量' ELSE '全厂能耗' END AS summary_type,
          s.department_name_snapshot AS department_name,v.metric_name_snapshot AS label,v.unit_snapshot AS unit,
          r.report_date,v.daily_actual AS quantity,
          CASE WHEN v.metric_code IN ('FACTORY_TOTAL_OUTPUT','POLY_OUTPUT') THEN v.converted_tons ELSE NULL END AS tons,
          CASE WHEN v.metric_code IN ('FACTORY_TOTAL_OUTPUT','POLY_OUTPUT') THEN 1 ELSE 0 END AS tonnage_applicable,
          CASE WHEN s.status='SUBMITTED' THEN 0 ELSE 1 END AS draft
        FROM production_daily_metric_value v
        JOIN production_daily_report_section s ON s.id=v.report_section_id
        JOIN production_daily_report r ON r.id=s.report_id
        """ + FILTER + """
        AND ((s.department_code='FACTORY_SUMMARY' AND v.metric_code IN
          ('FACTORY_TOTAL_OUTPUT','FACTORY_TOTAL_STEAM','FACTORY_TOTAL_POWER','FACTORY_TOTAL_WATER'))
          OR (s.department_code='POLYCRYSTAL' AND v.metric_code='POLY_OUTPUT'))
        <if test="query.itemQuery != null">
          <choose><when test="productOnly">AND 1=0</when>
          <otherwise>AND LOCATE(#{query.itemQuery},v.metric_name_snapshot) &gt; 0</otherwise></choose>
        </if>
        UNION ALL
        SELECT SHA2(CAST(JSON_ARRAY('product',s.department_code,s.department_name_snapshot,
                 v.category_code,v.category_name_snapshot,v.product_status_snapshot,v.unit) AS CHAR),256),
          3,'产品分类汇总',s.department_name_snapshot,
          CASE WHEN v.category_code IN ('SEMI_PRODUCT','DIRECT_PRODUCT','SECONDARY_PRODUCT')
            THEN v.category_name_snapshot ELSE CONCAT(v.category_name_snapshot,' · ',v.product_status_snapshot) END,
          v.unit,r.report_date,v.daily_actual,v.converted_tons,1,
          CASE WHEN s.status='SUBMITTED' THEN 0 ELSE 1 END
        FROM production_daily_product_line v
        JOIN production_daily_report_section s ON s.id=v.report_section_id
        JOIN production_daily_report r ON r.id=s.report_id
        """ + FILTER + """
        <if test="query.itemQuery != null">AND LOCATE(#{query.itemQuery},v.product_name_snapshot) &gt; 0</if>
        """;

    // Select series before daily points: limiting rows after grouping by date would silently lose days.
    @Select("<script>SELECT series_key AS seriesKey,summary_type AS summaryType,department_name AS departmentName,"
            + "label,unit,tonnage_applicable AS tonnageApplicable FROM (" + FACTS + ") f "
            + "GROUP BY series_key,priority,summary_type,department_name,label,unit,tonnage_applicable "
            + "ORDER BY priority,department_name,label,unit,series_key LIMIT 11</script>")
    List<ProductionDailyAggregateVO.Series> listSeries(
            @Param("query") ProductionDailyReportAgentQueryDTO query, @Param("productOnly") boolean productOnly);

    @Select("<script>SELECT series_key AS seriesKey,report_date AS businessDate,"
            + "SUM(quantity) AS quantity,SUM(tons) AS convertedTons,COUNT(*) AS recordCount,"
            + "COUNT(quantity) AS quantityRows,COUNT(tons) AS tonnageRows,SUM(draft) AS draftRows FROM (" + FACTS + ") f "
            + "WHERE series_key IN <foreach collection='keys' item='key' open='(' separator=',' close=')'>#{key}</foreach> "
            + "GROUP BY series_key,report_date ORDER BY series_key,report_date</script>")
    List<ProductionDailyAggregateVO.Point> dailyPoints(
            @Param("query") ProductionDailyReportAgentQueryDTO query, @Param("productOnly") boolean productOnly,
            @Param("keys") List<String> keys);
}

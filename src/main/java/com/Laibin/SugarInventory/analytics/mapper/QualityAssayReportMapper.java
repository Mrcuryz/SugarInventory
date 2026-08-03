package com.Laibin.SugarInventory.analytics.mapper;

import com.Laibin.SugarInventory.analytics.domain.vo.QualityAssayReportRowVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface QualityAssayReportMapper {
    @Select("""
            <script>
            SELECT
                a.id AS assayId,
                a.product_id AS productId,
                p.product_name AS productName,
                a.sample_date AS sampleDate,
                a.created_at AS createdAt,
                a.judge_result AS judgeResult,
                a.applied_standard_name AS appliedStandardName,
                a.applied_standard_version AS appliedStandardVersion,
                a.standard_snapshot_json AS standardSnapshotJson,
                a.color_value AS colorValue,
                a.reducing_sugar AS reducingSugar,
                a.dry_weight AS dryWeight,
                a.conductivity_ash AS conductivityAsh,
                a.sucrose AS sucrose,
                a.insoluble_impurity AS insolubleImpurity,
                a.ph_value AS phValue
            FROM assay a
            INNER JOIN product p ON p.id = a.product_id
            WHERE a.sample_date BETWEEN #{startDate} AND #{endDate}
            <if test="productQuery != null and productQuery != ''">
              AND p.product_name LIKE CONCAT('%', #{productQuery}, '%')
            </if>
            ORDER BY a.sample_date ASC, a.created_at ASC, a.id ASC
            </script>
            """)
    List<QualityAssayReportRowVO> listQualityAssaysForReport(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("productQuery") String productQuery);
}

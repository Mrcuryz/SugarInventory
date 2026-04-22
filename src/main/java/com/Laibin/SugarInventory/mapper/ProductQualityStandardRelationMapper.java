package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.ProductQualityStandardRelation;
import com.Laibin.SugarInventory.domain.vo.ProductQualityStandardRelationVO;
import com.Laibin.SugarInventory.domain.vo.QualityStandardRelatedProductVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProductQualityStandardRelationMapper extends BaseMapper<ProductQualityStandardRelation> {
    @Select("""
            <script>
            SELECT r.*,
                   p.product_name,
                   qs.standard_code,
                   qs.standard_name,
                   qs.version AS standard_version,
                   qs.status AS standard_status
            FROM product_quality_standard_relation r
            INNER JOIN product p ON p.id = r.product_id
            INNER JOIN quality_standards qs ON qs.id = r.quality_standard_id
            WHERE r.product_id = #{productId}
            ORDER BY r.is_default DESC, r.priority ASC, r.id ASC
            </script>
            """)
    List<ProductQualityStandardRelationVO> selectViewByProductId(@Param("productId") Integer productId);

    @Select("""
            <script>
            SELECT r.quality_standard_id,
                   p.id AS product_id,
                   p.product_name
            FROM product_quality_standard_relation r
            INNER JOIN product p ON p.id = r.product_id
            WHERE r.quality_standard_id IN
            <foreach collection='standardIds' item='standardId' open='(' separator=',' close=')'>
                #{standardId}
            </foreach>
            ORDER BY r.quality_standard_id ASC, r.is_default DESC, r.priority ASC, r.id ASC
            </script>
            """)
    List<QualityStandardRelatedProductVO> selectRelatedProductsByStandardIds(@Param("standardIds") List<Integer> standardIds);

    @Select("""
            <script>
            SELECT r.*
            FROM product_quality_standard_relation r
            INNER JOIN quality_standards qs ON qs.id = r.quality_standard_id
            WHERE r.product_id = #{productId}
              AND r.enabled = 1
              AND (r.effective_from IS NULL OR r.effective_from &lt;= #{at})
              AND (r.effective_to IS NULL OR r.effective_to &gt;= #{at})
              AND (qs.status IS NULL OR qs.status = '' OR qs.status = 'ENABLED')
            ORDER BY r.is_default DESC, r.priority ASC, r.id ASC
            </script>
            """)
    List<ProductQualityStandardRelation> selectActiveByProductId(@Param("productId") Integer productId,
                                                                 @Param("at") LocalDateTime at);

    @Select("SELECT COUNT(*) FROM product_quality_standard_relation WHERE product_id = #{productId} AND enabled = 1")
    int countEnabledByProductId(@Param("productId") Integer productId);

    @Select("SELECT COUNT(*) FROM product_quality_standard_relation WHERE quality_standard_id = #{qualityStandardId}")
    int countByQualityStandardId(@Param("qualityStandardId") Integer qualityStandardId);

    @Delete("DELETE FROM product_quality_standard_relation WHERE quality_standard_id = #{qualityStandardId}")
    int deleteByQualityStandardId(@Param("qualityStandardId") Integer qualityStandardId);

    @Update("UPDATE product_quality_standard_relation SET is_default = 0, updated_at = NOW(), updated_by = #{updatedBy} WHERE product_id = #{productId}")
    int clearDefaultByProductId(@Param("productId") Integer productId, @Param("updatedBy") Integer updatedBy);
}

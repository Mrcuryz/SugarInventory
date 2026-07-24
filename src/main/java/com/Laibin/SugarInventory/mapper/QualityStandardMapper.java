package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.QualityStandard;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface QualityStandardMapper extends BaseMapper<QualityStandard> {
    @Select("""
            <script>
            SELECT * FROM quality_standards
            WHERE standard_code = #{standardCode}
            <if test='version != null'>AND version = #{version}</if>
            ORDER BY version DESC, id DESC
            LIMIT 1
            </script>
            """)
    QualityStandard selectByCodeAndVersion(@Param("standardCode") String standardCode,
                                           @Param("version") Integer version);

    @Select("SELECT * FROM quality_standards WHERE product_type = #{productType} ORDER BY status DESC, version DESC, id ASC")
    List<QualityStandard> selectByProductType(@Param("productType") String productType);

    @Select("""
            <script>
            SELECT * FROM quality_standards
            <where>
               <if test='productType != null and productType != ""'>
                   AND product_type = #{productType}
               </if>
               <if test='standardName != null and standardName != ""'>
                   AND standard_name LIKE CONCAT('%', #{standardName}, '%')
               </if>
               <if test='status != null and status != ""'>
                   AND status = #{status}
               </if>
            </where>
            ORDER BY CASE WHEN status = 'ENABLED' THEN 0 ELSE 1 END, product_type, standard_name, version DESC, id ASC
            </script>
            """)
    List<QualityStandard> selectByConditions(@Param("productType") String productType,
                                             @Param("standardName") String standardName,
                                             @Param("status") String status);

    @Select("SELECT * FROM quality_standards WHERE id IN (SELECT quality_standard_id FROM product_quality_standard_relation WHERE product_id = #{productId}) ORDER BY version DESC, id ASC")
    List<QualityStandard> selectByProductId(@Param("productId") Integer productId);
}

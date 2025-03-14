package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.QualityStandard;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QualityStandardMapper extends BaseMapper<QualityStandard> {
    @Select("SELECT * FROM quality_standards WHERE product_type = #{productType}")
    List<QualityStandard> selectByProductType(@Param("productType") String productType);

    @Select("<script>" +
            "SELECT * FROM quality_standards " +
            "<where> " +
            "   <if test='productType != null and productType != \"\"'> " +
            "       AND product_type = #{productType} " +
            "   </if> " +
            "   <if test='standardName != null and standardName != \"\"'> " +
            "       AND standard_name = #{standardName} " +
            "   </if> " +
            "</where>" +
            "ORDER BY product_type, standard_name" +
            "</script>")
    List<QualityStandard> selectByProduct(@Param("productType") String productType,
                                          @Param("standardName") String standardName);
}

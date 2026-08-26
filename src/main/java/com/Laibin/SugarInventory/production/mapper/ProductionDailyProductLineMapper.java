package com.Laibin.SugarInventory.production.mapper;

import com.Laibin.SugarInventory.production.domain.po.ProductionDailyProductLine;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductionDailyProductLineMapper extends BaseMapper<ProductionDailyProductLine> {
    @Select("SELECT * FROM production_daily_product_line WHERE report_section_id = #{sectionId} " +
            "ORDER BY category_code, display_order, id")
    List<ProductionDailyProductLine> selectBySectionId(@Param("sectionId") Long sectionId);

    @Delete("DELETE FROM production_daily_product_line WHERE report_section_id = #{sectionId}")
    int deleteBySectionId(@Param("sectionId") Long sectionId);
}

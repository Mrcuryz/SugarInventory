package com.Laibin.SugarInventory.production.mapper;

import com.Laibin.SugarInventory.production.domain.po.ProductionDailyMetricValue;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductionDailyMetricValueMapper extends BaseMapper<ProductionDailyMetricValue> {
    @Select("SELECT * FROM production_daily_metric_value WHERE report_section_id = #{sectionId} ORDER BY display_order")
    List<ProductionDailyMetricValue> selectBySectionId(@Param("sectionId") Long sectionId);

    @Delete("DELETE FROM production_daily_metric_value WHERE report_section_id = #{sectionId}")
    int deleteBySectionId(@Param("sectionId") Long sectionId);
}

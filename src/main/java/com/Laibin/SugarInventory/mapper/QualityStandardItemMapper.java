package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.QualityStandardItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface QualityStandardItemMapper extends BaseMapper<QualityStandardItem> {
    @Select("SELECT * FROM quality_standard_item WHERE quality_standard_id = #{qualityStandardId} ORDER BY sort_order ASC, id ASC")
    List<QualityStandardItem> selectByQualityStandardId(@Param("qualityStandardId") Integer qualityStandardId);

    @Delete("DELETE FROM quality_standard_item WHERE quality_standard_id = #{qualityStandardId}")
    int deleteByQualityStandardId(@Param("qualityStandardId") Integer qualityStandardId);
}

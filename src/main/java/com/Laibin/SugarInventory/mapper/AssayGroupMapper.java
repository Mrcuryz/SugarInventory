package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.AssayGroupQueryDTO;
import com.Laibin.SugarInventory.domain.po.AssayGroup;
import com.Laibin.SugarInventory.domain.vo.AssayGroupVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@Mapper
public interface AssayGroupMapper extends BaseMapper<AssayGroup> {

    @Select("<script>" +
            "SELECT a.*, u.name AS createName, up.name AS updateName " +
            "FROM assay_group a " +
            "LEFT JOIN user u ON a.created_by = u.id " +
            "LEFT JOIN user up ON a.updated_by = up.id " +
            "<where> " +
            "   <if test='query.standardName != null'>AND a.standard_name LIKE CONCAT('%', #{query.standardName}, '%')</if> " +
            "</where> " +
            "ORDER BY a.created_at DESC " +
            "LIMIT #{offset}, #{size} " +
            "</script>")
    List<AssayGroupVO> selectAssayList(@Param("query") AssayGroupQueryDTO query, @Param("offset") int offset, @Param("size") Integer size);

    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM assay_group a " +
            "<where> " +
            "   <if test='query.standardName != null'>AND a.standard_name LIKE CONCAT('%', #{query.standardName}, '%')</if> " +
            "</where> " +
            "</script>")
    Long countAssay(@Param("query") AssayGroupQueryDTO query);
}

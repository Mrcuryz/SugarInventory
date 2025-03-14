package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.AssayQueryDTO;
import com.Laibin.SugarInventory.domain.po.Assay;
import com.Laibin.SugarInventory.domain.vo.AssayVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@Mapper
public interface AssayMapper extends BaseMapper<Assay> {
    @Select("SELECT * FROM assay " +
            "WHERE product_id = #{productId} AND sample_date = #{date} " +
            "ORDER BY created_at DESC, version DESC LIMIT 1")
    Assay selectByProductIdAndDate(@Param("productId") Integer productId,
                                   @Param("date") LocalDate date);

    @Select("<script>" +
            "SELECT a.*, p.product_name, u.name AS tester_name, s.*" +
            "FROM assay a " +
            "LEFT JOIN product p ON a.product_id = p.id " +
            "LEFT JOIN user u ON a.tested_by = u.id " +
            "LEFT JOIN quality_standards s ON p.product_type = s.product_type " +
            "<where> " +
            "   <if test='query.productName != null'>AND p.product_name LIKE CONCAT('%', #{query.productName}, '%')</if> " +
            "   <if test='query.startDate != null'>AND a.sample_date &gt;= #{query.startDate}</if> " +
            "   <if test='query.endDate != null'>AND a.sample_date &lt;= #{query.endDate}</if> " +
            "   <if test='query.testerName != null'>AND u.name LIKE CONCAT('%', #{query.testerName}, 'name'}, '%')</if> " +
            "</where> " +
            "ORDER BY a.sample_date DESC " +
            "LIMIT #{offset}, #{size} " +
            "</script>")
    List<AssayVO> selectAssayList(@Param("query") AssayQueryDTO query,
                                  @Param("offset") int offset,
                                  @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM assay a " +
            "LEFT JOIN product p ON a.product_id = p.id " +
            "LEFT JOIN user u ON a.tested_by = u.id " +
            "<where> " +
            "   <if test='query.productName != null'>AND p.product_name LIKE CONCAT('%', #{query.productName}, '%')</if> " +
            "   <if test='query.startDate != null'>AND a.sample_date &gt;= #{query.startDate}</if> " +
            "   <if test='query.endDate != null'>AND a.sample_date &lt;= #{query.endDate}</if> " +
            "   <if test='query.testerName != null'>AND u.name LIKE CONCAT('%', #{query.testerName}, 'name'}, '%')</if> " +
            "</where> " +
            "</script>")
    Long countAssay(@Param("query") AssayQueryDTO query);
}

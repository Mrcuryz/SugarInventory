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
            "SELECT a.id, p.product_name, a.sample_date, a.color_value, a.reducing_sugar, a.ph_value, u.name AS tester_name, a.created_at " +
            "FROM assay a " +
            "LEFT JOIN product p ON a.product_id = p.id " +
            "LEFT JOIN user u ON a.tested_by = u.id " +
            "<where> " +
            "   <if test='query.productId != null'>AND a.product_id = #{query.productId}</if> " +
            "   <if test='query.productName != null'>AND p.product_name LIKE CONCAT('%', #{query.productName}, '%')</if> " +
            "   <if test='query.startDate != null'>AND a.sample_date &gt;= #{query.startDate}</if> " +
            "   <if test='query.endDate != null'>AND a.sample_date &lt;= #{query.endDate}</if> " +
            "   <if test='query.testedBy != null'>AND a.tested_by = #{query.testedBy}</if> " +
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
            "<where> " +
            "   <if test='query.productId != null'>AND a.product_id = #{query.productId}</if> " +
            "   <if test='query.productName != null'>AND p.product_name LIKE CONCAT('%', #{query.productName}, '%')</if> " +
            "   <if test='query.startDate != null'>AND a.sample_date &gt;= #{query.startDate}</if> " +
            "   <if test='query.endDate != null'>AND a.sample_date &lt;= #{query.endDate}</if> " +
            "   <if test='query.testedBy != null'>AND a.tested_by = #{query.testedBy}</if> " +
            "</where> " +
            "</script>")
    Long countAssay(@Param("query") AssayQueryDTO query);

    @Insert("<script>" +
            "INSERT INTO assay " +
            "(product_id, sample_date, color_value, reducing_sugar, ph_value, tested_by, created_at) " +
            "VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.productId}, #{item.sampleDate}, #{item.colorValue}, " +
            "#{item.reducingSugar}, #{item.phValue}, #{item.testedBy}, #{item.createdAt})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("list") List<Assay> assays);
}

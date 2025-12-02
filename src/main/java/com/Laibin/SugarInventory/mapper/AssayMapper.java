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
 * Mapper 接口
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

    @Select("SELECT COUNT(*) FROM assay " +
            "WHERE product_id = #{productId} " +
            "AND sample_date = #{date}")
    Boolean existsByProductIdAndDate(@Param("productId") Integer productId,
                                     @Param("date") LocalDate date);

    @Select("<script>" +
            "SELECT a.*, p.product_name, u.name AS tester_name " +
            "FROM assay a " +
            "LEFT JOIN product p ON a.product_id = p.id " +
            "LEFT JOIN user u ON a.tested_by = u.id " +
            "<where> " +
            "   <if test='query.productName != null'>AND p.product_name LIKE CONCAT('%', #{query.productName}, '%')</if> " +
            "   <if test='query.startDate != null'>AND a.sample_date &gt;= #{query.startDate}</if> " +
            "   <if test='query.endDate != null'>AND a.sample_date &lt;= #{query.endDate}</if> " +
            "   <if test='query.productId != null'>AND a.product_id = #{query.productId}</if> " +
            "   <if test='query.sampleDate != null'>AND a.sample_date = #{query.sampleDate}</if> " +
            "   <if test='query.isQualified != null'>AND a.is_qualified = #{query.isQualified}</if> " +
            "   <if test='query.testerName != null'>AND u.name LIKE CONCAT('%', #{query.testerName}, '%')</if> " +
            "</where> " +
            "ORDER BY a.sample_date DESC, a.product_id ASC, a.version DESC " +
            "LIMIT #{offset}, #{size} " +
            "</script>")
    List<AssayVO> selectAssayList(@Param("query") AssayQueryDTO query,
                                  @Param("offset") int offset,
                                  @Param("size") int size);

//    @Select("<script>" +
//            "SELECT t1.*  " +
//            "FROM assay t1 " +
//            "INNER JOIN ( " +
//            "    SELECT product_id, sample_date, MAX(version) AS latest_version " +
//            "    FROM assay " +
//            "    GROUP BY product_id, sample_date " +
//            ") t2 ON t1.product_id = t2.product_id AND t1.sample_date = t2.sample_date AND t1.version = t2.latest_version " +
//            "LEFT JOIN product p ON t1.product_id = p.id " +
//            "LEFT JOIN quality_standards s ON p.product_type = s.product_type " +
//            "LEFT JOIN user u ON u.id = t1.tested_by " +
//            "WHERE 1=1 " +
//            "    <if test='query.productName != null'> " +
//            "        AND p.product_name LIKE CONCAT('%', #{query.productName}, '%') " +
//            "    </if> " +
//            "    <if test='query.testerName != null'> " +
//            "        AND u.name LIKE CONCAT('%', #{query.testerName}, '%') " +
//            "    </if> " +
//            "    <if test='query.startDate != null'> " +
//            "        AND t1.sample_date >= #{query.startDate} " +
//            "    </if> " +
//            "    <if test='query.endDate != null'> " +
//            "        AND t1.sample_date <= #{query.endDate} " +
//            "    </if> " +
//            "ORDER BY t1.sample_date DESC " +
//            "LIMIT #{offset}, #{size} " +
//            "</script>")
//    List<AssayVO> selectAssayList(@Param("query") AssayQueryDTO query,
//                                  @Param("offset") int offset,
//                                  @Param("size") int size);


    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM assay a " +
            "LEFT JOIN product p ON a.product_id = p.id " +
            "LEFT JOIN user u ON a.tested_by = u.id " +
            "<where> " +
            "   <if test='query.productName != null'>AND p.product_name LIKE CONCAT('%', #{query.productName}, '%')</if> " +
            "   <if test='query.startDate != null'>AND a.sample_date &gt;= #{query.startDate}</if> " +
            "   <if test='query.endDate != null'>AND a.sample_date &lt;= #{query.endDate}</if> " +
            "   <if test='query.testerName != null'>AND u.name LIKE CONCAT('%', #{query.testerName}, '%')</if> " +
            "</where> " +
            "</script>")
    Long countAssay(@Param("query") AssayQueryDTO query);
}

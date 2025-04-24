package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.SemiProductRecordDTO;
import com.Laibin.SugarInventory.domain.po.SemiProductRecord;
import com.Laibin.SugarInventory.domain.vo.RecordDetailVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
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
public interface SemiProductRecordMapper extends BaseMapper<SemiProductRecord> {
    @Select("SELECT s.*, p.product_name " +
            "FROM semi_product_record s " +
            "JOIN product p ON s.product_id = p.id " +
            "WHERE s.id = #{id}")
    RecordDetailVO selectSemiProductRecordById(@Param("id") Integer id);

    @Select("<script>" +
            "SELECT r.*, p.product_name " +
            "FROM semi_product_record r " +
            "JOIN user u ON r.operator = u.name " +
            "JOIN product p ON r.product_id = p.id " +
            "WHERE u.openid = #{openid} " +
            "<if test='date != null'>" +
            "   AND DATE(r.operation_date) = DATE(#{date}) " +
            "</if>" +
            "</script>")
    List<RecordDetailVO> selectByOperator(@Param("openid") String openid,
                                          @Param("date") LocalDate date);

    @Select("SELECT COUNT(*) FROM semi_product_record " +
            "WHERE product_id = #{productId} " +
            "AND operation_date = #{operationDate} ")
    int existsByProductIdAndDate(@Param("productId") Integer productId,
                                               @Param("operationDate") LocalDate operationDate);

    @Select("<script>" +
            "SELECT s.*, p.product_name " +
            "FROM semi_product_record s " +
            "JOIN product p ON s.product_id = p.id " +
            "WHERE s.id IN " +
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<RecordDetailVO> selectSemiProductRecordsByIds(@Param("ids") List<Integer> ids);

    @Update("<script>" +
            "UPDATE semi_product_record " +
            "<set>" +
            "   <if test='quantity != null'>quantity = #{quantity},</if>" +
            "   modify_count = modify_count + 1 " +
            "</set>" +
            "WHERE id = #{id} " +
            "AND operator = #{operator} " + // 限制只能修改自己的记录
            "</script>")
    int updateWithLimit(@Param("id") Integer id,
                        @Param("quantity") Integer quantity,
                        @Param("operator") String operator);

    // 根据传入的条件（产品名称、库位名称、操作员、日期范围）分页查询数据
    @Select("<script>" +
            "SELECT s.*, p.product_name, w.warehouse_name, a.*, u.name AS tester_name, sm.mesh_name " +
            "FROM semi_product_record s " +
            "JOIN product p ON s.product_id = p.id " +
            "JOIN warehouse w ON s.warehouse_id = w.id " +
            "JOIN screen_mesh sm ON s.screen_mesh_id = sm.id " +
            "LEFT JOIN ( " +
            "    SELECT * " +
            "    FROM ( " +
            "        SELECT a.*, " +
            "               ROW_NUMBER() OVER (PARTITION BY product_id, sample_date ORDER BY version DESC) AS rn " +
            "        FROM assay a " +
            "    ) ranked " +
            "    WHERE rn = 1 " +
            ") a ON s.product_id = a.product_id AND s.operation_date = a.sample_date " +
            "JOIN user u ON a.tested_by = u.id " +
            "WHERE 1=1 " +
            "<if test='dto.productName != null and dto.productName != \"\"'>" +
            "   AND p.product_name LIKE CONCAT('%', #{dto.productName}, '%') " +
            "</if>" +
            "<if test='dto.warehouseName != null and dto.warehouseName != \"\"'>" +
            "   AND w.warehouse_name LIKE CONCAT('%', #{dto.warehouseName}, '%') " +
            "</if>" +
            "<if test='dto.operatorName != null and dto.operatorName != \"\"'>" +
            "   AND s.operator LIKE CONCAT('%', #{dto.operatorName}, '%') " +
            "</if>" +
            "<if test='dto.startDate != null and dto.endDate != null'>" +
            "   AND s.operation_date BETWEEN #{dto.startDate} AND #{dto.endDate} " +
            "</if>" +
            "<if test='isStaff'>" +
            "   AND s.operator = #{operator} " +
            "</if>" +
            "ORDER BY s.created_at DESC " +
            "LIMIT #{offset}, #{size}" +
            "</script>")
    List<RecordDetailVO> getRecordsByConditions(@Param("dto") SemiProductRecordDTO dto,
                                                @Param("offset") Integer offset,
                                                @Param("size") Integer size,
                                                @Param("isStaff") boolean isStaff,
                                                @Param("operator") String operator);

    // 根据传入的条件（产品名称、库位名称、操作员、日期范围）统计总数
    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM semi_product_record s " +
            "JOIN product p ON s.product_id = p.id " +
            "JOIN warehouse w ON s.warehouse_id = w.id " +
            "JOIN assay as a ON s.assay_id = a.id " +
            "WHERE 1=1 " +
            "<if test='dto.productName != null and dto.productName != \"\"'>" +
            "   AND p.product_name LIKE CONCAT('%', #{dto.productName}, '%') " +
            "</if>" +
            "<if test='dto.warehouseName != null and dto.warehouseName != \"\"'>" +
            "   AND w.warehouse_name LIKE CONCAT('%', #{dto.warehouseName}, '%') " +
            "</if>" +
            "<if test='dto.operatorName != null and dto.operatorName != \"\"'>" +
            "   AND s.operator LIKE CONCAT('%', #{dto.operatorName}, '%') " +
            "</if>" +
            "<if test='dto.startDate != null and dto.endDate != null'>" +
            "   AND s.operation_date BETWEEN #{dto.startDate} AND #{dto.endDate} " +
            "</if>" +
            "<if test='isStaff'>" +
            "   AND s.operator = #{operator} " +
            "</if>" +
            "</script>")
    Long countByConditions(@Param("dto") SemiProductRecordDTO dto,
                           @Param("isStaff") boolean isStaff,
                           @Param("operator") String operator);
}

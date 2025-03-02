package com.Laibin.SugarInventory.mapper;

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
    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("INSERT INTO semi_product_record" +
            "(product_id, quantity, weight_per_piece, operation_date, operator, create_at, modify_count) " +
            "VALUES(#{productId}, #{quantity}, #{weightPerPiece}, #{operationDate}, #{operator}, #{createAt}, #{modifyCount})")
    int insertRecord(SemiProductRecord record);

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

    @Select("SELECT * FROM semi_product_record " +
            "WHERE product_id = #{productId} " +
            "AND operation_date = #{operationDate} ")
    SemiProductRecord selectByProductIdAndDate(@Param("productId") Integer productId,
                                               @Param("operationDate") LocalDate operationDate);

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

    // 根据传入的条件查询数据
    @Select("<script>" +
            "SELECT s.*, p.product_name " +
            "FROM semi_product_record s " +
            "JOIN product p ON s.product_id = p.id " +
            "WHERE 1=1 " +
            "<if test='productName != null and productName != \"\"'>AND product_id IN " +
            "(SELECT id FROM product WHERE product_name LIKE CONCAT('%', #{productName}, '%'))</if>" +
            "<if test='operationDate != null'>AND operation_date = #{operationDate}</if>" +
            "<if test='operatorName != null and operatorName != \"\"'>AND operator = #{operatorName}</if>" +
            "ORDER BY operation_date DESC" +
            "</script>")
    List<SemiProductRecord> getRecordsByConditions(@Param("productName") String productName,
                                                     @Param("operationDate") LocalDate operationDate,
                                                     @Param("operatorName") String operatorName);

}

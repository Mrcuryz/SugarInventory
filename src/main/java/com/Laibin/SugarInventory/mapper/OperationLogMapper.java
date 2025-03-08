package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.OperationLogQueryDTO;
import com.Laibin.SugarInventory.domain.po.OperationLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
public interface OperationLogMapper extends BaseMapper<OperationLog> {
    @Select("<script>" +
            "SELECT * FROM operation_log " +
            "<where>" +
            "   <if test='query.tableName != null'> AND table_name LIKE CONCAT('%', #{query.tableName}, '%') </if> " +
            "   <if test='query.operationType != null'> AND operation_type = #{query.operationType} </if> " +
            "   <if test='query.operator != null'> AND operator LIKE CONCAT('%', #{query.operator}, '%') </if> " +
            "   <if test='query.startTime != null'> AND operation_time &gt;= #{query.startTime} </if> " +
            "   <if test='query.endTime != null'> AND operation_time &lt;= #{query.endTime} </if> " +
            "</where>" +
            "ORDER BY operation_time DESC " +
            "LIMIT #{offset}, #{size}" +
            "</script>")
    List<OperationLog> selectOperationLogs(@Param("query") OperationLogQueryDTO query,
                                           @Param("offset") int offset,
                                           @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM operation_log " +
            "<where>" +
            "   <if test='query.tableName != null'> AND table_name LIKE CONCAT('%', #{query.tableName}, '%') </if> " +
            "   <if test='query.operationType != null'> AND operation_type = #{query.operationType} </if> " +
            "   <if test='query.operator != null'> AND operator LIKE CONCAT('%', #{query.operator}, '%') </if> " +
            "   <if test='query.startTime != null'> AND operation_time &gt;= #{query.startTime} </if> " +
            "   <if test='query.endTime != null'> AND operation_time &lt;= #{query.endTime} </if> " +
            "</where>" +
            "</script>")
    Long countOperationLogs(@Param("query") OperationLogQueryDTO query);

}

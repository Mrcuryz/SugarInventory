package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.OutProductQueryDTO;
import com.Laibin.SugarInventory.domain.dto.OutRecordQueryDTO;
import com.Laibin.SugarInventory.domain.po.OutStock;
import com.Laibin.SugarInventory.domain.vo.OutProductVO;
import com.Laibin.SugarInventory.domain.vo.OutStockRecordVO;
import com.Laibin.SugarInventory.domain.vo.OutWarehouseVO;
import com.Laibin.SugarInventory.domain.vo.ProductVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OutStockMapper extends BaseMapper<OutStock> {
    // 根据DTO批量查询出库记录
    @Select("<script>" +
            "SELECT " +
            "   w.warehouse_name, " +
            "   p.product_name, " +
            "   o.quantity, " +
            "   o.in_date, " +
            "   o.total_weight, " +
            "   o.out_date, " +
            "   u.name as operator_name, " +
            "   o.created_at " +
            "FROM out_stock o " +
            "INNER JOIN warehouse w ON o.warehouse_id = w.id " +
            "INNER JOIN product p ON o.product_id = p.id " +
            "INNER JOIN user u ON o.operator_id = u.id " +
            "WHERE 1 = 1 " +
            "<if test='query.warehouseName != null'>" +
            "   AND w.warehouse_name LIKE CONCAT('%', #{query.warehouseName}, '%') " +
            "</if> " +
            "<if test='query.productName != null'>" +
            "   AND p.product_name LIKE CONCAT('%', #{query.productName}, '%') " +
            "</if> " +
            "<if test='query.startDate != null'>" +
            "   AND o.in_date &gt;= #{query.startDate} " +
            "</if> " +
            "<if test='query.endDate != null'>" +
            "   AND o.in_date &lt;= #{query.endDate} " +
            "</if> " +
            "ORDER BY o.out_date DESC " +
            "LIMIT #{offset}, #{size}" +
            "</script>")
    List<OutStockRecordVO> selectOutStockRecordsByQuery(@Param("query") OutRecordQueryDTO query,
                                                         @Param("offset") Integer offset,
                                                         @Param("size") Integer size
    );

    // 计算批量查询出库记录的总数
    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM out_stock o " +
            "INNER JOIN warehouse w ON o.warehouse_id = w.id " +
            "INNER JOIN product p ON o.product_id = p.id " +
            "INNER JOIN user u ON o.operator_id = u.id " +
            "WHERE 1 = 1 " +
            "<if test='query.warehouseName != null'>" +
            "   AND w.warehouse_name LIKE CONCAT('%', #{query.warehouseName}, '%') " +
            "</if> " +
            "<if test='query.productName != null'>" +
            "   AND p.product_name LIKE CONCAT('%', #{query.productName}, '%') " +
            "</if> " +
            "<if test='query.startDate != null'>" +
            "   AND o.in_date &gt;= #{query.startDate} " +
            "</if> " +
            "<if test='query.endDate != null'>" +
            "   AND o.in_date &lt;= #{query.endDate} " +
            "</if> " +
            "</script>")
    Long countOutStockRecordsByQuery(@Param("query") OutRecordQueryDTO query);
}

package com.Laibin.SugarInventory.production.mapper;

import com.Laibin.SugarInventory.production.domain.dto.ProductionOrderQueryDTO;
import com.Laibin.SugarInventory.production.domain.po.ProductionOrder;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderOptionVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductionOrderMapper extends BaseMapper<ProductionOrder> {
    @Select("SELECT order_no FROM production_order " +
            "WHERE order_no LIKE CONCAT(#{prefix}, '%') " +
            "ORDER BY order_no DESC LIMIT 1 FOR UPDATE")
    String selectLatestOrderNoForUpdate(@Param("prefix") String prefix);

    @Select("<script>" +
            "SELECT * FROM production_order WHERE 1=1 " +
            "<if test='query.orderNo != null and query.orderNo != \"\"'>AND order_no LIKE CONCAT('%', #{query.orderNo}, '%') </if>" +
            "<if test='query.orderType != null and query.orderType != \"\"'>AND order_type = #{query.orderType} </if>" +
            "<if test='query.status != null and query.status != \"\"'>AND status = #{query.status} </if>" +
            "<if test='query.createdBy != null'>AND created_by = #{query.createdBy} </if>" +
            "<if test='query.startDate != null'>AND production_date &gt;= #{query.startDate} </if>" +
            "<if test='query.endDate != null'>AND production_date &lt;= #{query.endDate} </if>" +
            "ORDER BY production_date DESC, id DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<ProductionOrder> pageOrders(@Param("query") ProductionOrderQueryDTO query,
                                     @Param("offset") int offset,
                                     @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM production_order WHERE 1=1 " +
            "<if test='query.orderNo != null and query.orderNo != \"\"'>AND order_no LIKE CONCAT('%', #{query.orderNo}, '%') </if>" +
            "<if test='query.orderType != null and query.orderType != \"\"'>AND order_type = #{query.orderType} </if>" +
            "<if test='query.status != null and query.status != \"\"'>AND status = #{query.status} </if>" +
            "<if test='query.createdBy != null'>AND created_by = #{query.createdBy} </if>" +
            "<if test='query.startDate != null'>AND production_date &gt;= #{query.startDate} </if>" +
            "<if test='query.endDate != null'>AND production_date &lt;= #{query.endDate} </if>" +
            "</script>")
    Long countOrders(@Param("query") ProductionOrderQueryDTO query);

    @Select("<script>" +
            "SELECT id, order_no AS orderNo, order_type AS orderType, status, production_date AS productionDate " +
            "FROM production_order WHERE status NOT IN ('COMPLETED', 'CANCELED') " +
            "<if test='orderType != null and orderType != \"\"'>AND order_type = #{orderType} </if>" +
            "ORDER BY production_date DESC, id DESC LIMIT 100" +
            "</script>")
    List<ProductionOrderOptionVO> listActiveOptions(@Param("orderType") String orderType);
}

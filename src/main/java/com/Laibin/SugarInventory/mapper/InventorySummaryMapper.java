package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.InventoryQueryDTO;
import com.Laibin.SugarInventory.domain.vo.VInventorySummary;
import com.Laibin.SugarInventory.domain.vo.VWarehouseCapacity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InventorySummaryMapper extends BaseMapper<VInventorySummary> {

    @Select("<script>" +
            "SELECT * FROM v_inventory_product_summary " +
            "<where>" +
            "   <if test='query.warehouseId != null'> AND warehouse_id = #{query.warehouseId} </if>" + // 精确匹配数字库位
            "   <if test='query.productName != null'> AND product_name LIKE CONCAT('%', #{query.productName}, '%') </if>" + "</where>" +
            "ORDER BY warehouse_id, product_name " +
            "LIMIT #{offset}, #{size}" +
            "</script>")
    List<VInventorySummary> selectSummaryList(
            @Param("query") InventoryQueryDTO query,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @Select("<script>" +
            "SELECT COUNT(*) FROM v_inventory_product_summary " +
            "<where>" +
            "   <if test='query.warehouseId != null'> AND warehouse_id = #{query.warehouseId} </if>" +
            "   <if test='query.productName != null'> AND product_name LIKE CONCAT('%', #{query.productName}, '%') </if>" +
            "</where>" +
            "</script>")
    Long countSummary(@Param("query") InventoryQueryDTO query);

    @Select("SELECT * FROM v_warehouse_capacity")
    List<VWarehouseCapacity> selectCapacityList();
}
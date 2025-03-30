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
            "SELECT * FROM v_warehouse_inventory_summary " +
            "<where>" +
            "   1=1" +
            "   <if test='query.warehouseId != null'> AND warehouse_id = #{query.warehouseId} </if>" +
            "</where>" + // 精确匹配数字库位
            "ORDER BY warehouse_id, product_name " +
            "LIMIT #{offset}, #{size}" +
            "</script>")
    List<VInventorySummary> selectSummaryList(
            @Param("query") InventoryQueryDTO query,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @Select("SELECT * FROM v_warehouse_inventory_summary " +
            "ORDER BY entry_date " +
            "LIMIT 1")
    VInventorySummary selectFirstEntry();

    @Select("<script>" +
            "SELECT COUNT(*) FROM v_warehouse_inventory_summary " +
            "<where>" +
            "   1=1" +
            "   <if test='query.warehouseId != null'> AND warehouse_id = #{query.warehouseId} </if>" +
            "</where>" +
            "</script>")
    Long countSummary(@Param("query") InventoryQueryDTO query);

    @Select("SELECT * FROM v_warehouse_capacity " +
            "ORDER BY capacity_percentage DESC")
    List<VWarehouseCapacity> selectCapacityList();

    // 根据库位状态或库存id列表批量查询库位容量信息
    @Select("<script>" +
            "SELECT * FROM v_warehouse_capacity " +
            "<where>" +
            "   1=1 " +
            "   <if test='warehouseName != null and warehouseName != \"\"'> " +
            "       AND warehouse_name LIKE concat('%', #{warehouseName}, '%') " +
            "   </if> " +
            "   <if test='status != null and status != \"\"'> " +
            "       AND status = #{status} " +
            "   </if> " +
            "   <if test='warehouseIds != null and warehouseIds.size() > 0'> " +
            "       AND warehouse_id IN " +
            "       <foreach collection='warehouseIds' item='id' open='(' separator=',' close=')'> " +
            "           #{id} " +
            "       </foreach> " +
            "   </if> " +
            "</where>" +
            "ORDER BY capacity_percentage DESC " +
            "LIMIT #{offset}, #{size}" +
            "</script>")
    List<VWarehouseCapacity> selectCapacityListByStatus(@Param("warehouseName") String warehouseName,
                                                        @Param("warehouseIds") List<Integer> warehouseIds,
                                                        @Param("status") String status,
                                                        @Param("offset") int offset,
                                                        @Param("size") int size);


    // 根据库位状态和库存id列表批量查询库位容量数量
    @Select("<script>" +
            "SELECT COUNT(*) FROM v_warehouse_capacity " +
            "<where>" +
            "   1=1 " +
            "   <if test='warehouseName != null and warehouseName != \"\"'> " +
            "       AND warehouse_name LIKE concat('%', #{warehouseName}, '%') " +
            "   </if> " +
            "<if test='status!= null and status != \"\"'> " +
            "   AND status = #{status} " +
            "</if >" +
            "   <if test='warehouseIds != null and warehouseIds.size() > 0'> " +
            "       AND warehouse_id IN " +
            "       <foreach collection='warehouseIds' item='id' open='(' separator=',' close=')'> " +
            "           #{id} " +
            "       </foreach> " +
            "   </if> " +
            "</where>" +
            "</script>")
    Long countCapacityByStatus(@Param("warehouseName") String warehouseName,
                               @Param("status") String status,
                               @Param("warehouseIds") List<Integer> warehouseIds);
}
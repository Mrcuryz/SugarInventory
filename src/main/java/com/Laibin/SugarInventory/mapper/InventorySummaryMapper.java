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

    @Select("<script>" +
            "SELECT " +
            " a.product_name, " +
            " SUM(a.total_quantity) totalQuantity," +
            " SUM(a.total_pieces) totalPieces," +
            " CONCAT(ROUND(SUM(a.total_quantity) + SUM(a.total_pieces) / b.pieces_per_pallet), '板', ROUND(SUM(a.total_pieces ) % b.pieces_per_pallet), '件') AS stockInfo," +
            " SUM(a.total_pieces * b.weight_per_piece + a.total_quantity * b.pieces_per_pallet * b.weight_per_piece)  AS totalWeight " +
            " FROM" +
            " v_warehouse_inventory_summary a" +
            " LEFT JOIN product b ON a.product_id = b.id" +
            " WHERE b.`status` = #{productStatus} " +
            " <if test='productName != null and productName != \"\"'> " +
            "   AND a.product_name LIKE concat('%', #{productName}, '%') " +
            " </if>" +
            " GROUP BY a.product_id " +
            "</script>")
    List<VInventorySummary> selectProductTotalStock(@Param("productStatus") String productStatus, @Param("productName") String productName);

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

    @Select("""
            SELECT 
                w.id AS warehouse_id,
                w.warehouse_name,
                w.max_capacity,
                COALESCE(vc.cur_capacity, 0) AS cur_capacity,
                ROUND(COALESCE(vc.cur_capacity, 0) / w.max_capacity * 100, 2) AS capacity_percentage,
                w.status,
                w.created_at
            FROM warehouse w
            LEFT JOIN (
                SELECT 
                    warehouse_id, 
                    SUM(quantity) AS cur_capacity 
                FROM inventory 
                GROUP BY warehouse_id
            ) vc ON w.id = vc.warehouse_id
            ORDER BY capacity_percentage DESC
            """)
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

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
            " a.product_id AS productId, " +
            " a.product_name, " +
            " SUM(a.total_quantity) totalQuantity," +
            " SUM(a.total_pieces) totalPieces," +
            " CONCAT(ROUND(SUM(a.total_quantity) + SUM(a.total_pieces) / b.pieces_per_pallet), '板', ROUND(SUM(a.total_pieces ) % b.pieces_per_pallet), '件') AS stockInfo," +
            " SUM(a.total_pieces * b.weight_per_piece + a.total_quantity * b.pieces_per_pallet * b.weight_per_piece)  AS totalWeight, " +
            " COUNT(DISTINCT a.warehouse_id) AS warehouseCount " +
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
                w.cur_capacity AS cur_capacity,
                ROUND(CASE WHEN w.max_capacity > 0 THEN w.cur_capacity / w.max_capacity * 100 ELSE 0 END, 2) AS capacity_percentage,
                w.status,
                w.max_rows,
                w.created_at,
                w.updated_at,
                COALESCE(vc.current_pallet_count, 0) AS current_pallet_count,
                COALESCE(vc.current_product_count, 0) AS current_product_count
            FROM warehouse w
            LEFT JOIN (
                SELECT 
                    warehouse_id, 
                    COUNT(DISTINCT pallet_code_id) AS current_pallet_count,
                    COUNT(DISTINCT product_id) AS current_product_count
                FROM inventory 
                GROUP BY warehouse_id
            ) vc ON w.id = vc.warehouse_id
            ORDER BY capacity_percentage DESC
            """)
    List<VWarehouseCapacity> selectCapacityList();


    // 根据库位状态或库存id列表批量查询库位容量信息
    @Select("<script>" +
            "SELECT " +
            " w.id AS warehouse_id, " +
            " w.warehouse_name AS warehouse_name, " +
            " w.status AS status, " +
            " w.cur_capacity AS cur_capacity, " +
            " w.max_capacity AS max_capacity, " +
            " ROUND(CASE WHEN w.max_capacity > 0 THEN w.cur_capacity / w.max_capacity * 100 ELSE 0 END, 2) AS capacity_percentage, " +
            " MIN(i.entry_date) AS first_entry_date, " +
            " w.max_rows AS max_rows, " +
            " w.created_at AS created_at, " +
            " w.updated_at AS updated_at, " +
            " COUNT(DISTINCT i.pallet_code_id) AS current_pallet_count, " +
            " COUNT(DISTINCT i.product_id) AS current_product_count " +
            " FROM warehouse w " +
            " LEFT JOIN inventory i ON i.warehouse_id = w.id " +
            "<where>" +
            "   1=1 " +
            "   <if test='warehouseName != null and warehouseName != \"\"'> " +
            "       AND w.warehouse_name LIKE concat('%', #{warehouseName}, '%') " +
            "   </if> " +
            "   <if test='status != null and status != \"\"'> " +
            "       AND w.status = #{status} " +
            "   </if> " +
            "   <if test='createdStart != null and createdStart != \"\"'> " +
            "       AND w.created_at &gt;= #{createdStart} " +
            "   </if> " +
            "   <if test='createdEnd != null and createdEnd != \"\"'> " +
            "       AND w.created_at &lt;= #{createdEnd} " +
            "   </if> " +
            "   <if test='updatedStart != null and updatedStart != \"\"'> " +
            "       AND COALESCE(w.updated_at, w.created_at) &gt;= #{updatedStart} " +
            "   </if> " +
            "   <if test='updatedEnd != null and updatedEnd != \"\"'> " +
            "       AND COALESCE(w.updated_at, w.created_at) &lt;= #{updatedEnd} " +
            "   </if> " +
            "   <if test='warehouseIds != null and warehouseIds.size() > 0'> " +
            "       AND w.id IN " +
            "       <foreach collection='warehouseIds' item='id' open='(' separator=',' close=')'> " +
            "           #{id} " +
            "       </foreach> " +
            "   </if> " +
            "</where>" +
            " GROUP BY w.id, w.warehouse_name, w.status, w.cur_capacity, w.max_capacity, w.max_rows, w.created_at, w.updated_at " +
            "<choose>" +
            "   <when test='sortField == \"namePinyin\"'> ORDER BY CONVERT(w.warehouse_name USING gbk) </when>" +
            "   <when test='sortField == \"createdAt\"'> ORDER BY w.created_at </when>" +
            "   <when test='sortField == \"updatedAt\"'> ORDER BY COALESCE(w.updated_at, w.created_at) </when>" +
            "   <otherwise> ORDER BY w.id </otherwise>" +
            "</choose>" +
            "<choose>" +
            "   <when test='sortOrder == \"desc\" or sortOrder == \"DESC\"'> DESC </when>" +
            "   <otherwise> ASC </otherwise>" +
            "</choose>" +
            "LIMIT #{offset}, #{size}" +
            "</script>")
    List<VWarehouseCapacity> selectCapacityListByStatus(@Param("warehouseName") String warehouseName,
                                                        @Param("warehouseIds") List<Integer> warehouseIds,
                                                        @Param("status") String status,
                                                        @Param("sortField") String sortField,
                                                        @Param("sortOrder") String sortOrder,
                                                        @Param("createdStart") String createdStart,
                                                        @Param("createdEnd") String createdEnd,
                                                        @Param("updatedStart") String updatedStart,
                                                        @Param("updatedEnd") String updatedEnd,
                                                        @Param("offset") int offset,
                                                        @Param("size") int size);


    // 根据库位状态和库存id列表批量查询库位容量数量
    @Select("<script>" +
            "SELECT COUNT(*) FROM warehouse w " +
            "<where>" +
            "   1=1 " +
            "   <if test='warehouseName != null and warehouseName != \"\"'> " +
            "       AND w.warehouse_name LIKE concat('%', #{warehouseName}, '%') " +
            "   </if> " +
            "<if test='status!= null and status != \"\"'> " +
            "   AND w.status = #{status} " +
            "</if >" +
            "   <if test='createdStart != null and createdStart != \"\"'> " +
            "       AND w.created_at &gt;= #{createdStart} " +
            "   </if> " +
            "   <if test='createdEnd != null and createdEnd != \"\"'> " +
            "       AND w.created_at &lt;= #{createdEnd} " +
            "   </if> " +
            "   <if test='updatedStart != null and updatedStart != \"\"'> " +
            "       AND COALESCE(w.updated_at, w.created_at) &gt;= #{updatedStart} " +
            "   </if> " +
            "   <if test='updatedEnd != null and updatedEnd != \"\"'> " +
            "       AND COALESCE(w.updated_at, w.created_at) &lt;= #{updatedEnd} " +
            "   </if> " +
            "   <if test='warehouseIds != null and warehouseIds.size() > 0'> " +
            "       AND w.id IN " +
            "       <foreach collection='warehouseIds' item='id' open='(' separator=',' close=')'> " +
            "           #{id} " +
            "       </foreach> " +
            "   </if> " +
            "</where>" +
            "</script>")
    Long countCapacityByStatus(@Param("warehouseName") String warehouseName,
                               @Param("status") String status,
                               @Param("warehouseIds") List<Integer> warehouseIds,
                               @Param("createdStart") String createdStart,
                               @Param("createdEnd") String createdEnd,
                               @Param("updatedStart") String updatedStart,
                               @Param("updatedEnd") String updatedEnd);
}

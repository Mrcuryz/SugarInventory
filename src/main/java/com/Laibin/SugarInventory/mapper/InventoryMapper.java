package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.OutProductQueryDTO;
import com.Laibin.SugarInventory.domain.po.Coordinates;
import com.Laibin.SugarInventory.domain.po.Inventory;
import com.Laibin.SugarInventory.domain.vo.OutProductVO;
import com.Laibin.SugarInventory.domain.vo.OutWarehouseVO;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface InventoryMapper {

    @Select("SELECT * FROM inventory WHERE in_stock_id = #{inStockId}")
    Inventory selectByInStockId(@Param("inStockId") Integer inStockId);

    @Select("SELECT COUNT(DISTINCT `row_number`) " +
            "FROM inventory " +
            "WHERE warehouse_id = #{warehouseId} " +
            "AND side = #{side} " +
            "AND layer = #{layer}")
    int getUsedRows(@Param("warehouseId") int warehouseId,
                    @Param("side") String side,
                    @Param("layer") int layer);


    @Select("SELECT * FROM inventory " +
            "WHERE warehouse_id = #{warehouseId} " +
            "AND product_id = #{productId} " +
            "AND entry_date = #{entryDate} " +
            "AND screen_mesh_id = #{screenMeshId}")
    Inventory existSameInventory(@Param("warehouseId") Integer warehouseId,
                                 @Param("productId") Integer productId,
                                 @Param("entryDate") LocalDate entryDate,
                                 @Param("screenMeshId") Integer screenMeshId);

    @Insert("INSERT INTO inventory (warehouse_id, product_id, entry_date, side, `row_number`, layer, quantity, " +
            "screen_mesh_id, assay_id, created_at, in_stock_id, semi_record_id, product_status) " +
            "VALUES (#{warehouseId}, #{productId}, #{entryDate}, #{side}, #{rowNumber}, #{layer}, #{quantity}, " +
            "#{screenMeshId}, #{assayId}, #{createdAt}, #{inStockId}, #{semiRecordId}, #{productStatus})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Inventory inventory);

    @Select("SELECT * " +
            "FROM inventory " +
            "WHERE warehouse_id = #{warehouseId} " +
            "ORDER BY layer DESC LIMIT 1")
    Inventory getLast(Integer warehouseId);

    // **查询库存（按先进后出）**
    @Select("SELECT * FROM inventory " +
            "WHERE warehouse_id = #{warehouseId} " +
            "AND side = #{side} AND layer = #{layer} " +
            "ORDER BY `row_number` DESC")
    List<Inventory> getInventoryForOutStock(@Param("warehouseId") int warehouseId,
                                            @Param("side") String side,
                                            @Param("layer") int layer);

    // **删除某个格子的库存**
    @Delete("DELETE FROM inventory WHERE id = #{id}")
    void deleteInventoryById(@Param("id") int id);

    @Select("<script>" +
            "SELECT DISTINCT w.id AS warehouse_id, w.warehouse_name " +
            "FROM inventory i " +
            "INNER JOIN warehouse w ON i.warehouse_id = w.id " +
            "INNER JOIN product p ON i.product_id = p.id " +
            "INNER JOIN assay a ON i.assay_id = a.id " +
            "LEFT JOIN screen_mesh sm ON i.screen_mesh_id = sm.id " +
            "WHERE 1=1 " +
            "<if test='query.productName != null'>" +
            "   AND p.product_name LIKE CONCAT('%', #{query.productName}, '%') " +
            "</if> " +
            "<if test='query.standardNames != null and query.standardNames.size > 0'>" +
            "   <foreach item='name' collection='query.standardNames' open='AND (' separator=' OR ' close=')'> " +
            "       JSON_CONTAINS(a.qualified_standards, JSON_QUOTE(#{name})) " +
            "   </foreach> " +
            "</if> " +
            "<if test='query.screenMeshId != null'>" +
            "   AND sm.id = #{query.screenMeshId} " +
            "</if> " +
            "<if test='query.startDate != null'>" +
            "   AND a.sample_date &gt;= #{query.startDate} " +
            "</if> " +
            "<if test='query.endDate != null'>" +
            "   AND a.sample_date &lt;= #{query.endDate} " +
            "</if> " +
            "ORDER BY w.warehouse_name" +
            "</script>")
    List<OutWarehouseVO> findWarehousesByCondition(@Param("query") OutProductQueryDTO query);



    @Select("<script>" +
            "SELECT p.product_name, w.warehouse_name, a.sample_date, p.product_type, " +
            "       JSON_UNQUOTE(JSON_EXTRACT(a.qualified_standards, '$')) AS standardNames, " +
            "       sm.mesh_name, i.side, i.row_number, i.layer " +
            "FROM inventory i " +
            "INNER JOIN warehouse w ON i.warehouse_id = w.id " +
            "INNER JOIN product p ON i.product_id = p.id " +
            "INNER JOIN assay a ON i.assay_id = a.id " +
            "LEFT JOIN screen_mesh sm ON i.screen_mesh_id = sm.id " +
            "WHERE i.warehouse_id = #{warehouseId} " +
            "<if test='query.productName != null'>" +
            "   AND p.product_name LIKE CONCAT('%', #{query.productName}, '%') " +
            "</if> " +
            "<if test='query.standardNames != null and query.standardNames.size > 0'>" +
            "   <foreach item='name' collection='query.standardNames' open='AND (' separator=' OR ' close=')'> " +
            "       JSON_CONTAINS(a.qualified_standards, JSON_QUOTE(#{name})) " +
            "   </foreach> " +
            "</if> " +
            "<if test='query.screenMeshId != null'>" +
            "   AND sm.id = #{query.screenMeshId} " +
            "</if> " +
            "ORDER BY a.sample_date DESC " +
            "</script>")
    List<OutProductVO> findInventoryByWarehouse(@Param("warehouseId") Integer warehouseId,
                                                @Param("query") OutProductQueryDTO query);
}

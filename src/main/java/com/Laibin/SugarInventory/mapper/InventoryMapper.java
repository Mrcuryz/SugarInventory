package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.Coordinates;
import com.Laibin.SugarInventory.domain.po.Inventory;
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
}

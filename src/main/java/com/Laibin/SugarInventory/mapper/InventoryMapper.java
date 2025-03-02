package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.Coordinates;
import com.Laibin.SugarInventory.domain.po.Inventory;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Mapper
public interface InventoryMapper {

    @Select("SELECT * FROM inventory WHERE in_stock_id = #{inStockId}")
    Inventory selectByInStockId(@Param("inStockId") Integer inStockId);

    @Select("SELECT * FROM inventory " +
            "WHERE warehouse_id = #{warehouseId} " +
            "AND product_id = #{productId} " +
            "AND entry_date = #{entryDate} " +
            "AND screen_mesh_id = #{screenMeshId}")
    Inventory existSameInventory(@Param("warehouseId") Integer warehouseId,
                                 @Param("productId") Integer productId,
                                 @Param("entryDate") LocalDate entryDate,
                                 @Param("screenMeshId") Integer screenMeshId);

    @Insert("INSERT INTO inventory (warehouse_id, product_id, entry_date, total_quantity, " +
            "screen_mesh_id, assay_id, created_at, in_stock_id) " +
            "VALUES (#{warehouseId}, #{productId}, #{entryDate}, #{totalQuantity}, " +
            "#{screenMeshId}, #{assayId}, #{createdAt}, #{inStockId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Inventory inventory);

    @Update("<script>" +
            "UPDATE inventory " +
            "<set>" +
            "   <if test='productId != null'>product_id = #{productId},</if>" +
            "   <if test='totalQuantity != null'>total_quantity = #{totalQuantity},</if>" +
            "   <if test='entryDate != null'>entry_date = #{entryDate},</if>" +
            "   <if test='assayId != null'>assay_id = #{assayId},</if>" +
            "   <if test='screenMeshId != null'>screen_mesh_id = #{screenMeshId},</if>" +
            "</set>" +
            "WHERE in_stock_id = #{inStockId}" +
            "</script>")
    void updateInventory(@Param("productId") Integer productId,
                        @Param("totalQuantity") Integer totalQuantity,
                        @Param("entryDate") LocalDate entryDate,
                        @Param("assayId") Integer assayId,
                        @Param("inStockId") Integer inStockId,
                        @Param("screenMeshId") Integer screenMeshId);


    @Update("UPDATE inventory SET total_quantity = total_quantity - #{quantity} " +
            "WHERE id = #{inventoryId}")
    void reduceTotalQuantity(
            @Param("inventoryId") Integer inventoryId,
            @Param("quantity") Integer quantity
    );
}

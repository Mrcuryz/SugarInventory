package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.InventoryLocation;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface InventoryLocationMapper {
    @Insert("<script>" +
            "INSERT INTO inventory_location " +
            "(inventory_id, coordinate_x, coordinate_y, quantity) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.inventoryId}, #{item.coordinateX}, #{item.coordinateY}, #{item.quantity})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<InventoryLocation> locations);

    @Select("SELECT * FROM inventory_location " +
            "WHERE inventory_id = #{inventoryId} " +
            "AND coordinate_x = #{coordinateX} " +
            "AND coordinate_y = #{coordinateY} ")
    InventoryLocation selectForUpdate(@Param("inventoryId") Integer inventoryId,
                                      @Param("coordinateX") Double coordinateX,
                                      @Param("coordinateY") Double coordinateY);

    @Update("UPDATE inventory_location " +
            "SET quantity = quantity - #{qty} " +
            "WHERE id = #{id} " +
            "AND quantity >= #{qty}")
    int deductQuantity(@Param("id") Integer locationId, @Param("qty") Integer qty);

    @Update("UPDATE inventory_location " +
            "SET quantity = quantity + #{qty} " +
            "WHERE id = #{id}")
    int AddQuantity(@Param("id") Integer locationId, @Param("qty") Integer qty);
}

package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@Mapper
public interface WarehouseMapper extends BaseMapper<Warehouse> {
    @Update("UPDATE warehouse SET status = '维护' WHERE id = #{id}")
    int updateStatusToMaintain(@Param("id") Integer id);

    @Update("UPDATE warehouse SET status = '正常' WHERE id = #{id}")
    int cancelMaintain(@Param("id") Integer id);

    @Select("SELECT * FROM warehouse WHERE warehouse_name = #{warehouseName}")
    Warehouse selectByWarehouseName(@Param("warehouseName") String warehouseName);

    @Update("UPDATE warehouse SET cur_capacity = #{curCapacity} WHERE id = #{id}")
    void updateCurCapacity(@Param("id") Integer id, @Param("curCapacity") Integer curCapacity);

    @Update("UPDATE warehouse " +
            "SET max_capacity = #{maxCapacity} " +
            "WHERE id = #{id}")
    void updateMaxCapacity(Integer id, int maxCapacity);
}

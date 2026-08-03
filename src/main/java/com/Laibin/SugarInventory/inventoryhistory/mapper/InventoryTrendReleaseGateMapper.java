package com.Laibin.SugarInventory.inventoryhistory.mapper;

import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryTrendReleaseGate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InventoryTrendReleaseGateMapper
        extends BaseMapper<InventoryTrendReleaseGate> {
    @Select("""
            SELECT *
            FROM inventory_trend_release_gate
            WHERE gate_key = #{gateKey}
            LIMIT 1
            """)
    InventoryTrendReleaseGate findByGateKey(@Param("gateKey") String gateKey);
}

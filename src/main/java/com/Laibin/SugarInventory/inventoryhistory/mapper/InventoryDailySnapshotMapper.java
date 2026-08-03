package com.Laibin.SugarInventory.inventoryhistory.mapper;

import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryDailySnapshot;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryBalanceAggregate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InventoryDailySnapshotMapper extends BaseMapper<InventoryDailySnapshot> {
    @Select("""
            SELECT
                product_id,
                MAX(product_name_snapshot) AS product_name,
                0 AS warehouse_id,
                NULL AS warehouse_name,
                COALESCE(SUM(total_pieces), 0) AS total_pieces,
                COALESCE(SUM(total_weight_kg), 0) AS total_weight_kg
            FROM inventory_daily_snapshot
            WHERE snapshot_run_id = #{snapshotRunId}
            GROUP BY product_id
            """)
    List<InventoryBalanceAggregate> listProductBalances(
            @Param("snapshotRunId") String snapshotRunId
    );

    @Select("""
            SELECT
                product_id,
                MAX(product_name_snapshot) AS product_name,
                warehouse_id,
                MAX(warehouse_name_snapshot) AS warehouse_name,
                COALESCE(SUM(total_pieces), 0) AS total_pieces,
                COALESCE(SUM(total_weight_kg), 0) AS total_weight_kg
            FROM inventory_daily_snapshot
            WHERE snapshot_run_id = #{snapshotRunId}
            GROUP BY product_id, warehouse_id
            """)
    List<InventoryBalanceAggregate> listWarehouseProductBalances(
            @Param("snapshotRunId") String snapshotRunId
    );
}

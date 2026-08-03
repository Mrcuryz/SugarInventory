package com.Laibin.SugarInventory.inventoryhistory.mapper;

import com.Laibin.SugarInventory.inventoryhistory.domain.InventorySnapshotRun;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface InventorySnapshotRunMapper extends BaseMapper<InventorySnapshotRun> {
    @Select("""
            SELECT COALESCE(MAX(revision), 0)
            FROM inventory_snapshot_run
            WHERE snapshot_date = #{snapshotDate}
              AND snapshot_type = #{snapshotType}
            """)
    int findMaxRevision(
            @Param("snapshotDate") LocalDate snapshotDate,
            @Param("snapshotType") String snapshotType
    );

    @Select("""
            SELECT *
            FROM inventory_snapshot_run
            WHERE snapshot_date = #{snapshotDate}
              AND snapshot_type = #{snapshotType}
              AND status = 'COMPLETED'
            ORDER BY revision DESC
            LIMIT 1
            """)
    InventorySnapshotRun findLatestCompleted(
            @Param("snapshotDate") LocalDate snapshotDate,
            @Param("snapshotType") String snapshotType
    );
}

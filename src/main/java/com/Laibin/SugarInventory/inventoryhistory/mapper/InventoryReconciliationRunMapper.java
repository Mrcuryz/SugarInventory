package com.Laibin.SugarInventory.inventoryhistory.mapper;

import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryReconciliationRun;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface InventoryReconciliationRunMapper extends BaseMapper<InventoryReconciliationRun> {
    @Select("""
            SELECT COALESCE(MAX(revision), 0)
            FROM inventory_reconciliation_run
            WHERE business_date = #{businessDate}
            """)
    int findMaxRevision(@Param("businessDate") LocalDate businessDate);

    @Select("""
            SELECT *
            FROM inventory_reconciliation_run
            WHERE business_date = #{businessDate}
            ORDER BY revision DESC
            LIMIT 1
            """)
    InventoryReconciliationRun findLatest(@Param("businessDate") LocalDate businessDate);

    @Select("""
            SELECT r.*
            FROM inventory_reconciliation_run r
            JOIN (
                SELECT business_date, MAX(revision) AS latest_revision
                FROM inventory_reconciliation_run
                WHERE business_date <= #{endDate}
                GROUP BY business_date
            ) latest
              ON latest.business_date = r.business_date
             AND latest.latest_revision = r.revision
            ORDER BY r.business_date DESC
            LIMIT #{limit}
            """)
    List<InventoryReconciliationRun> listLatestDays(
            @Param("endDate") LocalDate endDate,
            @Param("limit") int limit
    );
}

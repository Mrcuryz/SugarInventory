package com.Laibin.SugarInventory.inventoryhistory.mapper;

import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryHistoryJobRun;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface InventoryHistoryJobRunMapper extends BaseMapper<InventoryHistoryJobRun> {
    @Select("""
            SELECT *
            FROM inventory_history_job_run
            WHERE business_date = #{businessDate}
            ORDER BY started_at DESC, id DESC
            LIMIT 1
            """)
    InventoryHistoryJobRun findLatest(@Param("businessDate") LocalDate businessDate);
}

package com.Laibin.SugarInventory.inventoryhistory.mapper;

import com.Laibin.SugarInventory.inventoryhistory.domain.StockMovementEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface StockMovementEventMapper extends BaseMapper<StockMovementEvent> {
    @Select("""
            SELECT *
            FROM stock_movement_event
            WHERE source_type = #{sourceType}
              AND source_record_id = #{sourceRecordId}
              AND event_type = #{eventType}
            LIMIT 1
            """)
    StockMovementEvent findBySource(
            @Param("sourceType") String sourceType,
            @Param("sourceRecordId") Long sourceRecordId,
            @Param("eventType") String eventType
    );

    @Select("""
            SELECT *
            FROM stock_movement_event
            WHERE occurred_at >= #{startInclusive}
              AND occurred_at < #{endExclusive}
            ORDER BY occurred_at, id
            """)
    List<StockMovementEvent> listOccurredBetween(
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive
    );

    @Select("""
            SELECT COUNT(*)
            FROM stock_movement_event
            WHERE occurred_at >= #{startInclusive}
              AND occurred_at < #{endExclusive}
            """)
    int countOccurredBetween(
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive
    );
}

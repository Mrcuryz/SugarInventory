package com.Laibin.SugarInventory.analytics.mapper;

import com.Laibin.SugarInventory.analytics.domain.po.RegisteredReportRunPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RegisteredReportRunMapper extends BaseMapper<RegisteredReportRunPO> {
    @Select("""
            SELECT report_run_id
            FROM agent_report_run
            WHERE expires_at <= #{cutoffAt}
            ORDER BY expires_at ASC, id ASC
            LIMIT #{batchSize}
            """)
    List<String> selectExpiredReportRunIds(
            @Param("cutoffAt") LocalDateTime cutoffAt,
            @Param("batchSize") int batchSize);
}

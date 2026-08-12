package com.Laibin.SugarInventory.analytics.mapper;

import com.Laibin.SugarInventory.analytics.domain.ReportExecutionWindowAggregate;
import com.Laibin.SugarInventory.analytics.domain.po.RegisteredReportExecutionRunPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface RegisteredReportExecutionRunMapper
        extends BaseMapper<RegisteredReportExecutionRunPO> {
    @Select("""
            SELECT
                COUNT(*) AS execution_count,
                COALESCE(SUM(status = 'SUCCEEDED'), 0) AS success_count,
                COALESCE(SUM(status = 'REJECTED'), 0) AS rejection_count,
                COALESCE(SUM(status = 'FAILED'), 0) AS failure_count,
                COALESCE(SUM(status = 'SUCCEEDED' AND partial_data = 1), 0)
                    AS partial_data_count,
                COALESCE(MAX(duration_ms), 0) AS max_duration_ms
            FROM agent_report_execution_run
            WHERE started_at >= #{windowStartedAt}
              AND started_at < #{windowEndedAt}
            """)
    ReportExecutionWindowAggregate aggregateWindow(
            @Param("windowStartedAt") LocalDateTime windowStartedAt,
            @Param("windowEndedAt") LocalDateTime windowEndedAt);

    @Select("""
            SELECT duration_ms
            FROM agent_report_execution_run
            WHERE started_at >= #{windowStartedAt}
              AND started_at < #{windowEndedAt}
            ORDER BY duration_ms ASC, id ASC
            LIMIT #{offset}, 1
            """)
    Long findDurationAtOffset(
            @Param("windowStartedAt") LocalDateTime windowStartedAt,
            @Param("windowEndedAt") LocalDateTime windowEndedAt,
            @Param("offset") long offset);

    @Select("""
            SELECT *
            FROM agent_report_execution_run
            WHERE started_at >= #{windowStartedAt}
              AND started_at < #{windowEndedAt}
            ORDER BY started_at DESC, id DESC
            LIMIT 1
            """)
    RegisteredReportExecutionRunPO findLatest(
            @Param("windowStartedAt") LocalDateTime windowStartedAt,
            @Param("windowEndedAt") LocalDateTime windowEndedAt);

    @Select("""
            SELECT *
            FROM agent_report_execution_run
            WHERE status = 'REJECTED'
              AND started_at >= #{windowStartedAt}
              AND started_at < #{windowEndedAt}
            ORDER BY started_at DESC, id DESC
            LIMIT 1
            """)
    RegisteredReportExecutionRunPO findLatestRejection(
            @Param("windowStartedAt") LocalDateTime windowStartedAt,
            @Param("windowEndedAt") LocalDateTime windowEndedAt);

    @Select("""
            SELECT *
            FROM agent_report_execution_run
            WHERE status = 'FAILED'
              AND started_at >= #{windowStartedAt}
              AND started_at < #{windowEndedAt}
            ORDER BY started_at DESC, id DESC
            LIMIT 1
            """)
    RegisteredReportExecutionRunPO findLatestFailure(
            @Param("windowStartedAt") LocalDateTime windowStartedAt,
            @Param("windowEndedAt") LocalDateTime windowEndedAt);
}

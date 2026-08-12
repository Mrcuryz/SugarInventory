package com.Laibin.SugarInventory.analytics.mapper;

import com.Laibin.SugarInventory.analytics.domain.po.RegisteredReportExportAuditPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface RegisteredReportExportAuditMapper extends BaseMapper<RegisteredReportExportAuditPO> {
    @Select("""
            SELECT COUNT(*)
            FROM agent_report_export_audit
            WHERE exported_at >= #{windowStartedAt}
              AND exported_at < #{windowEndedAt}
            """)
    long countInWindow(
            @Param("windowStartedAt") LocalDateTime windowStartedAt,
            @Param("windowEndedAt") LocalDateTime windowEndedAt);
}

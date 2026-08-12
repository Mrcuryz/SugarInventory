package com.Laibin.SugarInventory.analytics.mapper;

import com.Laibin.SugarInventory.analytics.domain.po.RegisteredReportCleanupRunPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RegisteredReportCleanupRunMapper extends BaseMapper<RegisteredReportCleanupRunPO> {
    @Select("""
            SELECT *
            FROM agent_report_cleanup_run
            ORDER BY started_at DESC, id DESC
            LIMIT 1
            """)
    RegisteredReportCleanupRunPO findLatest();
}

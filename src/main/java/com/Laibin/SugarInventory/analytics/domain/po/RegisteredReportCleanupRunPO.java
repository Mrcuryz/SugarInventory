package com.Laibin.SugarInventory.analytics.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_report_cleanup_run")
public class RegisteredReportCleanupRunPO {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String cleanupRunId;
    private LocalDateTime cutoffAt;
    private Integer batchSize;
    private Integer selectedReportCount;
    private Integer deletedReportRunCount;
    private String status;
    private String failureMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}

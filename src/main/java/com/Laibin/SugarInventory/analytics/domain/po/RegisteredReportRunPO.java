package com.Laibin.SugarInventory.analytics.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_report_run")
public class RegisteredReportRunPO {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String reportRunId;
    private Integer ownerUserId;
    private String ownerDisplayName;
    private String reportDefinitionId;
    private Integer reportVersion;
    private String requiredPermission;
    private String payloadJson;
    private String contentSha256;
    private LocalDateTime generatedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}

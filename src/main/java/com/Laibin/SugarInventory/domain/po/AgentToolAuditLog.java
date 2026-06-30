package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_tool_audit_log")
public class AgentToolAuditLog {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String agentSessionId;
    private Integer userId;
    private String toolName;
    private String toolCallId;
    private String upstreamPath;
    private String argumentsSummary;
    private String requestSummary;
    private String responseSummary;
    private String resultCode;
    private String errorCode;
    private Long durationMs;
    private LocalDateTime createdAt;
}


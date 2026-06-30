package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_api_audit_log")
public class AgentApiAuditLog {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String agentSessionId;
    private Integer userId;
    private String toolName;
    private String httpMethod;
    private String requestPath;
    private Integer responseStatus;
    private String resultCode;
    private String errorCode;
    private Long durationMs;
    private LocalDateTime createdAt;
}

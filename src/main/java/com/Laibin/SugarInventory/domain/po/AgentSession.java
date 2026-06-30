package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_session")
public class AgentSession {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private Integer userId;
    private String clientType;
    private String scopes;
    private String status;
    private String createdIp;
    private String userAgent;
    private String mcpServerName;
    private String mcpTransport;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private Integer revokedBy;
    private String revokedReason;
    private LocalDateTime lastUsedAt;
    private String lastErrorCode;
}

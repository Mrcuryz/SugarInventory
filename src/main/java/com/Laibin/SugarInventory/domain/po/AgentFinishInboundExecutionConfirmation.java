package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_finish_inbound_execution_confirmation")
public class AgentFinishInboundExecutionConfirmation {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String confirmationRef;
    private Long previewId;
    private String previewRef;
    private Integer ownerUserId;
    private String agentSessionId;
    private String status;
    private String requiredPermissions;
    private String previewContentSha256;
    private String previewStateDigest;
    private String tokenSha256;
    private String idempotencyKeySha256;
    private LocalDateTime confirmedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private LocalDateTime consumedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_finish_inbound_execution_preview")
public class AgentFinishInboundExecutionPreview {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String previewRef;
    private Integer ownerUserId;
    private String agentSessionId;
    private Integer previewVersion;
    private String status;
    private String requiredPermissions;
    private String entityRefsJson;
    private String normalizedInputJson;
    private String entityStateJson;
    private String publicPayloadJson;
    private String stateDigest;
    private String normalizedRequestSha256;
    private String contentSha256;
    private LocalDateTime previewedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime revokedAt;
    private LocalDateTime consumedAt;
}
